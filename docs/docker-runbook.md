# 构建与运行手册

配套文件：`docker-compose.yml`、`blog/Dockerfile`、`frontend/Dockerfile`
编排设计说明见 `docker-compose.yml` 中的注释

> 本手册记录的配置已于 2026-08-29 在本机**完整跑通**：六个服务全部 healthy，
> 前端、后端直连、Nginx 反代、SPA 深链接、中文全文搜索均已验证。
>
> **2026-10-30 变更**：Elasticsearch 已移除，全文搜索改用 MySQL FULLTEXT ngram
> （见 `docs/design.md` 2.5 的决策记录）。现在只有 **五个**服务；
> 旧数据卷里的 `es_data` 不会被自动清理，用 `docker compose down -v` 可一并删掉。

---

## 直接回答：`docker compose up -d` 可以吗？

**能，但日常用不够。**

- 第一次跑时镜像不存在，Compose 会**自动构建**后端和前端，`up -d` 确实能起来。
- 但**第二次之后它不会重新构建**。改了 Java 或 Vue 代码再跑 `up -d`，跑的还是旧镜像。
- 所以日常一律用 `docker compose up -d --build`。

---

## 端口表

**必须在项目根目录执行**（`cd ~/Desktop/blog-platform`），否则报
`no configuration file provided`。

| 服务 | 宿主端口 | 容器端口 | 地址 |
| --- | --- | --- | --- |
| frontend (Nginx) | 18081 | 80 | http://localhost:18081 |
| backend | 18080 | 8080 | http://localhost:18080 |
| mysql | 33306 | 3306 | `localhost:33306` |
| redis | 16379 | 6379 | `localhost:16379` |
| rabbitmq (AMQP) | 56720 | 5672 | `localhost:56720` |
| rabbitmq (管理台) | 15672 | 15672 | http://localhost:15672 |

### 选端口的硬性规则：宿主机端口必须 > 15000 或 < 1024

本机 IPv4 动态端口范围是 **1024–15000**（`netsh int ipv4 show dynamicport tcp`
显示"启动端口 1024，端口数 13977"）。Hyper-V 会从这个区间随机划走连续段，
落在里面的端口随时可能绑定失败。

实测踩坑：MySQL 原本用 13306，被 13208–13307 段吃掉，报
`bind: An attempt was made to access a socket in a way forbidden by its access permissions`。

这类保留段**每次重启都会变**，且 Docker Desktop 启动时会新申请一批——
所以 Docker 没运行时查到的段不作数。查看当前被划走的段：

```bash
netsh interface ipv4 show excludedportrange protocol=tcp
```

另外 33060 也不能用：那是本机 MySQL 的 X Plugin 默认端口。

---

## 首次配置

Compose 不再提供默认 JWT 密钥。首次启动前：

```powershell
Copy-Item .env.example .env
node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"
```

把生成值写入 `.env` 的 `JWT_SECRET`。未配置时后端和 Compose 会直接拒绝启动。

`init.sql` 是全新数据库的唯一基线。历史迁移脚本位于
`blog/src/main/resources/sql/migrations/`，只用于已有数据库手动升级。

## 常用命令

```bash
# 首次全量构建并启动（约 7 分钟；之后重建几十秒）
docker compose up -d --build

# 改了后端 / 前端代码后重建单个服务
docker compose up -d --build backend
docker compose up -d --build frontend

# 改了 VITE_API_BASE 等构建期变量（Vite 会内联进产物，必须禁用缓存重建）
docker compose build --no-cache frontend && docker compose up -d frontend

# 停掉但保留数据
docker compose down

# 连数据卷一起清掉（下次启动会重新执行建表脚本，回到干净环境）
docker compose down -v

# 看日志
docker compose logs -f backend
```

---

## 验证

```bash
docker compose ps          # 六个都应为 healthy
```

| 检查项 | 命令 | 预期 |
| --- | --- | --- |
| 前端首页 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:18081/` | 200 |
| 后端直连 | `curl -s http://localhost:18080/api/articles` | JSON 文章列表 |
| Nginx 反代 | `curl -s http://localhost:18081/api/articles` | 同上 |
| SPA 深链接 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:18081/articles/23` | 200 |
| 全文检索 | `curl -s "http://localhost:18081/api/articles/search?keyword=全文"` | `total` > 0 |
| 热榜 | `curl -s http://localhost:18081/api/articles/hot?topN=5` | 按热度倒序 |
| 死信队列 | `curl -s -H "Authorization: Bearer <ADMIN_TOKEN>" http://localhost:18080/api/admin/dlq` | 三个 DLQ，条数均为 0 |

FULLTEXT 索引自检（确认 ngram 解析器已生效）：

```bash
docker compose exec -T mysql mysql -uroot -p123456 blog -e "SHOW CREATE TABLE article\G" \
  | grep ft_search
# 期望：FULLTEXT KEY `ft_search` (`title`,`content`) /*!50100 WITH PARSER `ngram` */
```

注意 `ngram_token_size` 默认 2，搜索**单个汉字**命不中全文索引，
接口会退化成 `LIKE` 扫描（见 `ArticleSearchQueryServiceImpl`），这是预期行为。

### 局域网访问

**只暴露前端**。后端、MySQL、Redis、RabbitMQ 仍然只绑 `127.0.0.1`，
外部流量统一经 Nginx 的 `/api` 反代进入。

> 注意：路径上是同源的，**但浏览器仍然会带 Origin**（端口不是 80，属于非默认端口），
> 所以后端 CORS 白名单必须放行局域网来源，见下文「CORS 必须放行局域网来源」。

compose 里前端的端口映射**不带** `127.0.0.1:` 前缀，即绑定到 `0.0.0.0`：

```yaml
- "${FRONTEND_PORT:-80}:80"
```

另一半是 Windows 防火墙：它默认阻止入站，必须为宿主机端口建放行规则
（需要**管理员** PowerShell）：

```powershell
New-NetFirewallRule -DisplayName "blog-platform (LAN 18081)" `
  -Direction Inbound -Protocol TCP -LocalPort 18081 -Action Allow -Profile Private
```

删掉规则用 `Remove-NetFirewallRule -DisplayName "blog-platform (LAN 18081)"`。

查本机局域网地址：`ipconfig | findstr IPv4`，然后其他设备访问
`http://<局域网IP>:18081/`。

#### 为什么不用 80

本机 80 被 Steam++ 加速器等程序占用，而且 **Windows 允许两个进程同时绑定
`0.0.0.0:80`**（不同于 Linux 的默认行为），`netstat` 会显示两行都处于
LISTENING，实际流量串到先绑定的那个，表现为「Docker 说端口已映射，但访问返回
别的服务的 404」。所以前端端口用 18081。

端口选型还要遵守上文规则：必须 `> 15000`（或 `< 1024`）且不在 Hyper-V 排除段内。
8088 这类端口虽然看着空闲，但落在 1024–15000 的动态段里，重启后可能被划走。

#### CORS 必须放行局域网来源

这是最容易踩的坑：**同源部署下浏览器本来不发 Origin，一旦换成局域网 IP 或换了端口，
浏览器就会带上 Origin，而它不在后端白名单里会直接被判 403「Invalid CORS request」**。
被拦的不只是预检，带 Origin 的普通写请求同样会被拒，前端只显示「网络异常」。

内置白名单（`CorsConfig.DEFAULT_ALLOWED_ORIGINS`）只有本机地址：
`http://localhost:5173`、`http://localhost:18081`、`http://127.0.0.1:18081`。

局域网访问需要在 `.env` 追加，端口必须和 `FRONTEND_PORT` 一致：

```bash
CORS_EXTRA_ORIGINS=http://192.168.1.9:18081
```

多个来源用逗号分隔，写完整 `协议://IP:端口`，不要带结尾斜杠。
改完 `docker compose up -d backend` 生效（环境变量，不需要 `--build`）。

验证：

```bash
curl -s -o /dev/null -w "%{http_code}
" -X POST http://192.168.1.9:18081/api/auth/register   -H "Origin: http://192.168.1.9:18081" -H "Content-Type: application/json"   -d '{"username":"probe","password":"Passw0rd1"}'
# 期望 200；把 Origin 换成任意其他域名应返回 403 Invalid CORS request
```

#### 安全提醒

局域网开放只适合自用/演示。当前 `.env` 里的 MySQL 密码、RabbitMQ 账号
都是开发默认值，且 `JWT_SECRET` 若未更换会让所有令牌可被伪造——不要把这套配置
直接放到能出公网的环境。

### 写链路自检

读接口之外，建议定期验证写入与异步消息链路。实测脚本走的是 Nginx 反代：
注册两个账号 → 登录拿 JWT → 发文章 → 校验可搜索 → 评论/点赞 → 校验 RabbitMQ 通知
与热度加分 → 清理。全通过才算健康。

热度链路自检：点赞后 `docker compose exec -T redis redis-cli ZSCORE hot:articles <id>`
应增加 `likeWeight`（默认 5）；`redis-cli --scan --pattern 'hot:dedup:*'` 能看到去重键。

---

## 资源与维护

### 日志轮转

compose 顶部定义了 `x-logging` 锚点，五个服务用 `logging: *default-logging` 引用，
每个容器最多保留 3 个 10MB 的日志文件，超出滚动覆盖。

Docker 默认的 `json-file` 驱动**不限制大小**，服务长期开着日志会无限增长。
确认是否生效：

```bash
docker inspect blog-backend --format '{{index .HostConfig.LogConfig.Config "max-size"}}'
# 期望输出 10m
```

改这个配置要 `docker compose up -d` 重建容器才生效（不需要 `--build`）。

### 内存

| 服务 | 堆参数 | 实测占用 |
| --- | --- | --- |
| backend | `-Xms256m -Xmx512m` | 约 710MB |
| mysql | 默认 | 约 440MB |
| rabbitmq | 默认 | 约 160MB |
| frontend | — | 约 27MB |
| redis | `maxmemory 256mb` | 约 9MB |

整套约 1.3GB（移除 ES 前约 2.1GB）。若后端出现 `OutOfMemoryError`，
把 compose 里 `JAVA_OPTS` 上限调回 `768m` 即可。

> 别用 `docker exec ... java -XX:+PrintFlagsFinal -version` 查堆大小——
> 那会**另起一个 JVM**，读出来的是 JVM 默认值（约 1.9GB），跟跑应用的进程无关。
> 正确做法：`docker exec blog-backend sh -c "ps -eo args | grep '[j]ava'"`

### 数据持久化

`docker compose down` 只删容器和网络，**数据卷保留**。实测停机前后
user / article / comment / notification 行数、RabbitMQ 队列、Redis 键，全部原样恢复。

只有 `docker compose down -v`（带 `-v`）才会连卷一起删——那会回到干净环境，
下次启动重新执行建表脚本。

---

## 数据迁移（已完成，此处留档）

容器 MySQL 是全新的，数据从本机 3306 迁过来。步骤：

```bash
# 1. 只导数据，不导表结构（init.sql 已经建好表，避免 CREATE TABLE 重复执行）
/c/Mysql80/mysql-8.0.46-winx64/bin/mysqldump \
  -h 127.0.0.1 -P 3306 -u root -p \
  --no-create-info --complete-insert --single-transaction \
  --default-character-set=utf8mb4 --set-gtid-purged=OFF \
  blog > blog_data.sql

# 2. 起 MySQL，等 healthy（首次启动自动执行 init.sql 建库建表）
docker compose up -d mysql

# 3. 导入（关外键检查，避免多表互相引用时插入顺序报错）
{ echo "SET FOREIGN_KEY_CHECKS=0;"; cat blog_data.sql; } \
  | docker compose exec -T mysql mysql -uroot -p"blog_dev_pwd" blog
```

密码取 `.env` 的 `MYSQL_ROOT_PASSWORD`，没建 `.env` 就是 `blog_dev_pwd`。
`init.sql` 与两个 migration 脚本都只有 DDL、无 INSERT，所以这套"先建表后导数据"不会撞主键。

> `init.sql` 只在**数据卷为空**时执行一次。改了脚本要重跑得先 `docker compose down -v`。

---

## 排障

**前端一直 `health: starting`**
容器 `/etc/hosts` 里 `localhost` 同时映射到 `::1`，而 nginx 只监听 IPv4 的
`0.0.0.0:80`，走 `::1` 会 Connection refused。健康检查已改为显式 `127.0.0.1`。
不要试图加 `listen [::]:80` 解决——容器未启用 IPv6 时 nginx 会报
`Address family not supported by protocol` 直接起不来。

**端口绑定失败**
两种报错要区分：
- `forbidden by its access permissions` → 落在 Hyper-V 保留段，换 >15000 的端口
- `Only one usage of each socket address` → 已被别的进程占用，`netstat -ano | findstr :端口` 查

**`registry-1.docker.io` 超时**
本机 Docker Hub 直连不通。长效解法是配 Docker Desktop 加速镜像：
Settings → Docker Engine 加 `"registry-mirrors": ["https://docker.m.daocloud.io"]`。
临时绕法：
```bash
docker pull docker.m.daocloud.io/library/mysql:8.4
docker tag  docker.m.daocloud.io/library/mysql:8.4 mysql:8.4
```

**后端反复重启**
```bash
docker compose logs backend | tail -60
```
多数是依赖服务没就绪。compose 已经用 `depends_on: condition: service_healthy` 等齐，
如果仍失败，先看 `docker compose ps` 里谁不是 healthy。

**搜索没结果**
先确认索引存在（见上文 FULLTEXT 索引自检）。`init.sql` 只在数据卷为空时执行，
**改了 `init.sql` 必须 `docker compose down -v` 重建**，否则旧库没有 `ft_search`，
全文检索会一直返回空。
注意单个汉字搜不到是预期行为（`ngram_token_size=2`），走的是 LIKE 兜底。

**热度榜为空或分数不对**
- 空：看日志有没有 `热门文章重建完成`。启动时会 seed 一次，之后每 30 分钟校正
- 分数不对：增量靠 MQ 事件，`rabbitmqctl list_queues name messages` 看 `hot.score.queue`
  是否有积压；死信积压用 `GET /api/admin/dlq` 查，必要时 `POST /api/admin/dlq/replay` 重放
- 注意增量与重建的差别：重建以数据库为准，会**覆盖**增量累加的结果。
  刚点完赞看到分数被"降回去"是正常的，说明重建拿到了权威数据

**封禁 DLQ 端点 403**
`app.admin.usernames` 为空时任何人都访问不了。在 `.env` 设 `ADMIN_USERNAMES=你的登录名`
（逗号分隔），再 `docker compose up -d backend` 让环境变量生效。
