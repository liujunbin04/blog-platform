# 2026-08-29 容器化（Dockerfile + docker-compose）

## 目标

把 `blog/`（Spring Boot 3.5.16 / JDK 17 / Maven）和 `frontend/`（Vue 3 / Vite 8）
连同 MySQL 8、Redis 7、Elasticsearch 8、RabbitMQ 3 编排成一条命令可起的完整环境。

## 已确认的决策

| 决策点 | 结论 | 理由 |
| --- | --- | --- |
| 构建策略 | 多阶段构建，源码进镜像 | 不依赖宿主机 JDK 25 / Node 22 的具体版本，CI 可复现 |
| 前端类型检查 | 跳过（`npm run build-only`） | 类型检查是质量门禁，不该让 `docker build` 因类型小错整个失败；单独跑 `npm run type-check` |
| ES 8 认证 | 关闭 `xpack.security`，单节点 | `application.yml` 里 ES 无凭据配置，零改动即可连通 |
| 宿主机端口 | 偏移（13306/16379/19200），RabbitMQ 与 Nginx 用标准端口 | 避开 Windows Hyper-V 动态保留段，也不与现有 `blog-redis` 等容器冲突 |
| 库表初始化 | 挂载整个 `sql/` 目录自动执行 | 首次启动即建库建表，无需手工导 SQL |
| 后端 8080 | 映射到宿主机 | 方便 Postman / Swagger / IDEA 直连，排除前端干扰 |
| 数据卷 | 具名卷 | Docker Desktop + WSL2 下 I/O 与权限都比绑定挂载到 Windows 目录稳 |

## 拓扑

```
                 ┌─────────────── blog-net (bridge) ───────────────┐
  :80 ─────────► │ frontend (nginx)                               │
                 │   /api/*  ──proxy_pass──►  backend:8080 ───────┼──► mysql:3306
                 │   /*      ──try_files──►  /index.html (SPA)    │    redis:6379
                 └────────────────────────────────────────────────┘    elasticsearch:9200
  :8080 ──────────────────────────────────►  backend               │    rabbitmq:5672
```

容器间一律用 **服务名 + 容器端口** 互访，与宿主机映射端口无关。
宿主端口映射只影响你从 Windows 连进去。

## 关键实现细节

### 后端 Dockerfile（三阶段）

1. `maven:3.9-eclipse-temurin-17` 编译。先单独 `COPY pom.xml` 做缓存预热层，
   再用 `--mount=type=cache,target=/root/.m2` 挂 Maven 仓库缓存
   （预热层被打穿时依赖仍不必重下）。**需要 BuildKit**，Docker Desktop 默认开启。
2. `java -Djarmode=layertools -jar app.jar extract` 按依赖/加载器/业务代码拆分 jar，
   重建镜像时只需重传业务层那几十 KB。项目无 SNAPSHOT 依赖时
   `snapshot-dependencies` 目录不会生成，故显式 `mkdir -p` 四个目录。
3. 运行镜像 `eclipse-temurin:17-jre-jammy`，装 `tzdata`（对齐 `serverTimezone=Asia/Shanghai`）
   与 `curl`（健康检查），非 root 用户运行。

启动类为 `org.springframework.boot.loader.launch.JarLauncher`
（Spring Boot 3.2+ 从 `loader.JarLauncher` 迁到了 `loader.launch` 包）。

### 前端 Dockerfile

用 `node:22-bookworm-slim` 而非 alpine：Vite 8 的 rolldown、oxlint 都带原生二进制，
musl 下要额外拉预编译包，glibc 发行版更稳。

`VITE_API_BASE` 是**构建期**变量，会被 Vite 内联进产物，改值必须重建镜像，
重启容器无效。默认 `/api` 与页面同源，由 Nginx 反代，天然无跨域。

### Nginx 反代

没有用 `upstream { server blog-backend:8080; }`。upstream 里的域名在 Nginx
**启动时**解析一次并永久缓存 IP，后端容器重建换 IP 后代理就断了，只有重启 Nginx 才能恢复。
改用 `resolver 127.0.0.11` + 变量形式，让域名在每次请求时才解析。

`/` 必须 `try_files $uri $uri/ /index.html`：前端是 `createWebHistory`，
刷新 `/articles/123` 这类深链接时服务器上没有对应文件。

### Compose

- 四个中间件全部配 `healthcheck`，后端用 `depends_on: condition: service_healthy` 等待。
  只写 `depends_on` 不写 `condition` 只保证容器已创建，不保证服务已就绪。
- MySQL 8.4 **已移除** `--default-authentication-plugin`，不要加回去。
- ES 健康检查同时接受 `green` 和 `yellow`：单节点无副本分片，`yellow` 就是正常态。
- `$$MYSQL_ROOT_PASSWORD` 的双美元符是 compose 转义，运行时由容器内 shell 展开。

## 使用

```bash
cp .env.example .env          # 可选，不建就走开发默认值
docker compose up -d --build
docker compose ps             # 六个都 healthy 后访问 http://localhost
```

| 入口 | 地址 |
| --- | --- |
| 前端 | http://localhost |
| 后端直连 | http://localhost:8080 |
| RabbitMQ 管理台 | http://localhost:15672 |

常用操作：

```bash
docker compose logs -f backend          # 跟后端日志
docker compose restart backend          # 改了后端代码
docker compose build --no-cache frontend # 改了 VITE_API_BASE
docker compose down -v                  # 连数据卷一起清掉（下次启动会重新执行建表脚本）
```

## 后续补充（同日晚些时候）

- **版本对齐**：ES 从 8.15.3 改为 **8.17.4**、RabbitMQ 从 alpine 变体改为
  **3.13-management**，与本机已在运行的容器版本一致（这两个标签已确认可拉取）。
- **Docker Hub 本机直连不通**：`docker pull hello-world` 报
  `dial tcp registry-1.docker.io:443: connectex ...`。
  但 DaoCloud 加速镜像 `docker.m.daocloud.io` **可用**，
  已用它预拉取各镜像并 `docker tag` 回官方标签，
  这样 compose 里保持可移植的官方镜像名，同时本机不必访问外网。
  长效做法是给 Docker Desktop 配 `registry-mirrors`。
- **启动前冲突**：本机已有 `blog-redis` / `blog-rabbitmq` / `blog-es` 三个容器，
  以及 IDE 里跑在 8080 的 Spring Boot，会与 compose 撞容器名和端口。
  清理与数据迁移步骤见 `docs/docker-runbook.md`。
- 已验证：`docker compose config` 语法、变量插值、端口与依赖渲染全部正确；
  具名卷与匿名卷的差异、ES 索引自愈能力（`ArticleSyncTask` 启动全量回填）已确认。

## 已知风险与未验证项

以下仍未端到端跑通（镜像预拉取完成后即可验证）：

1. **`mysql:8.4` / `node:22-bookworm-slim` / `maven:3.9-eclipse-temurin-17` /
   `eclipse-temurin:17-jre-jammy` 四个标签**在加速镜像上的存在性尚在拉取验证中。
   若某个拉不动，改成相近版本即可（后端与 ES 客户端需保持 8.x）。
2. **实际构建未跑**：后端 Maven 编译、前端 `npm ci` + `vite build` 均未执行过。
3. **`vite-plugin-vue-devtools`**：`vite.config.ts` 里无条件注册了该插件。
   它在生产构建下通常 no-op，若 `npm run build-only` 报错，
   把它改成 `mode === 'development' && vueDevTools()` 即可。
4. **ES 的 `vm.max_map_count`**：Linux 宿主机需 `sysctl -w vm.max_map_count=262144`。
   Docker Desktop（WSL2 虚拟机）一般已满足。
5. **Windows 保留端口**：当前保留段是 3336、5357、11838–12437、50000–50059，
   所选偏移端口均未冲突。但 Hyper-V 动态段重启后会变，
   若 `up` 时报端口占用，用 `netsh interface ipv4 show excludedportrange protocol=tcp`
   查后改 `.env` 里的端口即可。

## 未做（按 YAGNI 排除）

- 开发态热重载 overlay：热重载继续用 `npm run dev` + Vite proxy。
- Nginx HTTPS：需要证书，留到真实部署时再加。
- ES 与 RabbitMQ 的认证加固：已在 compose 注释里标出改动位置。
