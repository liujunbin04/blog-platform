# =============================================================================
# 博客平台后端 —— Spring Boot 3.5.16 / JDK 17
# 三阶段构建：Maven 编译 → 分层 jar 提取 → 精简运行镜像
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1：编译
# 用 maven 官方镜像，不依赖宿主机 JDK（本机装的是 JDK 25，而 pom 目标版本是 17）
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先只放 pom.xml，让「依赖没变」时能命中这一层的缓存。
# 否则改一行代码就要重跑依赖解析。
COPY pom.xml ./
# 这一层纯粹是「缓存预热」：pom 没变就整层命中，跳过依赖下载。
# 加 || true 是因为 dependency:go-offline 对部分插件依赖会报解析失败，
# 而它失败并不代表真缺东西——真正的解析由下面 mvn package 完成，
# 那里出问题会如实报错。少了这个兜底，一个无关的插件告警就能卡死 docker build。
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline -q || true

COPY src ./src

# --mount=cache 把本地 Maven 仓库挂成构建缓存：
# 即使上面那层被源码变更打穿，依赖也不用重新下载。
# 需要 BuildKit（Docker Desktop 默认开启；Linux 上老版本要 DOCKER_BUILDKIT=1）
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests

# 产物固定为 blog-0.0.1-SNAPSHOT.jar（artifactId=blog，version=0.0.1-SNAPSHOT）

# -----------------------------------------------------------------------------
# Stage 2：按 Spring Boot 分层拆 jar
# 依赖层几乎不变、业务代码层每次都变，拆开后重建镜像只需重传几十 KB
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS extract

WORKDIR /build
COPY --from=builder /build/target/*.jar app.jar

# 项目没有 SNAPSHOT 依赖时 snapshot-dependencies 不会被创建，
# 显式 mkdir 保证下一阶段 COPY 一定拿得到四个目录
RUN java -Djarmode=layertools -jar app.jar extract \
 && mkdir -p dependencies spring-boot-loader snapshot-dependencies application

# -----------------------------------------------------------------------------
# Stage 3：运行镜像
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy

# tzdata：让 JVM 认得出 Asia/Shanghai，日志时间戳和 JDBC 的 serverTimezone 对得上
# curl：容器健康检查探测 /api/articles 用
RUN apt-get update \
 && apt-get install -y --no-install-recommends tzdata curl \
 && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo "Asia/Shanghai" > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

# 非 root 运行
RUN groupadd -r -g 10001 blog \
 && useradd  -r -u 10001 -g blog -m -d /home/blog blog

WORKDIR /app

# 按「变动频率从低到高」依次拷贝，应用层放最后
COPY --from=extract /build/dependencies/        ./
COPY --from=extract /build/spring-boot-loader/  ./
COPY --from=extract /build/snapshot-dependencies/ ./
COPY --from=extract /build/application/         ./

RUN chown -R blog:blog /app

USER blog

EXPOSE 8080

# 用 JAVA_OPTS 而不是硬编码，方便在 compose 里按环境覆盖。
# 这里给固定堆大小而非 MaxRAMPercentage：没配容器内存上限时
# 百分比会按宿主机总内存算，容易吃太多
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

# /api/articles 是 GET 且 SecurityConfig 里 permitAll，无需认证即可探测
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=5 \
    CMD curl -fsS http://localhost:8080/api/articles > /dev/null || exit 1

# Spring Boot 3.2+ 起 JarLauncher 迁到 loader.launch 包（Boot 2 是 loader.JarLauncher）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
