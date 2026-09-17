# 构建与运行手册

配套文件：`docker-compose.yml`、`blog/Dockerfile`、`frontend/Dockerfile`、
`docker/elasticsearch/Dockerfile`（IK 分词插件）
设计说明见 `docs/superpowers/plans/2026-08-29-dockerization.md`

> 本手册记录的配置已于 2026-08-29 在本机**完整跑通**：六个服务全部 healthy，
> 前端、后端直连、Nginx 反代、SPA 深链接、ES 中文分词搜索均已验证。

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
| frontend (Nginx) | 80 | 80 | http://localhost |
| backend | 18080 | 8080 | http://localhost:18080 |
| mysql | 33306 | 3306 | `localhost:33306` |
| redis | 16379 | 6379 | `localhost:16379` |
| elasticsearch | 19200 | 9200 | http://localhost:19200 |
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
| 前端首页 | `curl -s -o /dev/null -w "%{http_code}" http://localhost/` | 200 |
| 后端直连 | `curl -s http://localhost:18080/api/articles` | JSON 文章列表 |
| Nginx 反代 | `curl -s http://localhost/api/articles` | 同上 |
| SPA 深链接 | `curl -s -o /dev/null -w "%{http_code}" http://localhost/articles/23` | 200 |
| ES 回填 | `docker compose logs backend \| grep "ES 全量回填完成"` | `共 N 篇` |
| ES 文档数 | `curl -s http://localhost:19200/article/_count` | count > 0 |
| IK 分词 | 见下 | 中文被正确切分 |

IK 分词自检：

```bash
curl -s http://localhost:19200/article/_analyze -H "Content-Type: application/json" \
  -d '{"analyzer":"ik_smart","text":"SpringBoot博客平台全文搜索"}'
```

应切出 `springboot` / `博客` / `平台` / `全文` / `搜索`。

### 写链路自检

读接口之外，建议定期验证写入与异步消息链路。实测脚本走的是 Nginx 反代：
注册两个账号 → 登录拿 JWT → 发文章 → 校验 ES 同步 → 评论 → 校验 RabbitMQ 通知 →
清理。八项全通过才算健康。

注意**清理要清两处**：删 MySQL 行不会自动删 ES 文档，两边都要处理，
否则搜索会搜到已删除的文章。

---

## 资源与维护

### 日志轮转

compose 顶部定义了 `x-logging` 锚点，六个服务用 `logging: *default-logging` 引用，
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
| elasticsearch | `-Xms256m -Xmx512m` | 约 840MB |
| backend | `-Xms256m -Xmx512m` | 约 710MB |
| mysql | 默认 | 约 440MB |
| rabbitmq | 默认 | 约 160MB |
| frontend | — | 约 27MB |
| redis | `maxmemory 256mb` | 约 9MB |

整套约 2.1GB（调整前约 2.7GB）。若后端出现 `OutOfMemoryError`，
把 compose 里 `JAVA_OPTS` 上限调回 `768m` 即可。

> 别用 `docker exec ... java -XX:+PrintFlagsFinal -version` 查堆大小——
> 那会**另起一个 JVM**，读出来的是 JVM 默认值（约 1.9GB），跟跑应用的进程无关。
> 正确做法：`docker exec blog-backend sh -c "ps -eo args | grep '[j]ava'"`

### 数据持久化

`docker compose down` 只删容器和网络，**数据卷保留**。实测停机前后
user 5 / article 7 / comment 13 / notification 3、ES 4 篇文档、RabbitMQ 2 个队列、
Redis 1 个键，全部原样恢复。

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

**IK 分词器缺失：后端起不来**
`ArticleDocument` 用了 `ik_max_word` / `ik_smart`，官方 ES 镜像不带插件，报错：
`mapper_parsing_exception: analyzer [ik_smart] has not been configured in mappings`。
解决：本仓库的 `docker/elasticsearch/Dockerfile` 会在构建时装 `analysis-ik`，
compose 里 ES 走的是 `build` 而非官方镜像直引。插件版本必须与 ES 版本完全一致。

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
ES 索引创建失败会导致整个 Spring 容器初始化中止（repository bean 建不出来），
这类错误在日志里表现为 `UnsatisfiedDependencyException` → `articleSearchRepository`。

**ES 起不来**
Linux 宿主机需 `sudo sysctl -w vm.max_map_count=262144`。Docker Desktop（WSL2）一般已满足。

**搜索没结果**
看 `ArticleSyncTask` 是否跑过。失败时日志会有 `ES 全量回填失败，搜索功能暂不可用`，
其他功能不受影响，重启后端会重试。
