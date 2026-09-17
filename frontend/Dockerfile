# =============================================================================
# 博客平台前端 —— Vue 3 + Vite 8
# 两阶段构建：Node 打包 dist → Nginx 静态托管 + /api 反代
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1：打包
# 用 bookworm-slim（glibc）而非 alpine：Vite 8 的 rolldown 和 oxlint 都带
# 原生二进制，musl 下需要额外的预编译包，glibc 发行版省事且更稳
# -----------------------------------------------------------------------------
FROM node:22-bookworm-slim AS builder

WORKDIR /app

COPY package.json package-lock.json ./
# npm 缓存挂出来，重建时不必重下一遍依赖
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY . .

# VITE_API_BASE 是构建期变量（Vite 在打包时把它内联进产物），
# 改这个值必须重新 build，重启容器无效——这一点在 compose 注释里也标注了。
# 默认 /api：与页面同源，由 Nginx 反向代理到后端，天然没有跨域问题
ARG VITE_API_BASE=/api
ENV VITE_API_BASE=${VITE_API_BASE}

# build-only = vite build，跳过 vue-tsc 类型检查。
# 类型检查属于质量门禁，不该让 docker build 因为一个类型小错就整个失败；
# 需要时在 CI 或本地单独跑 npm run type-check
RUN npm run build-only

# -----------------------------------------------------------------------------
# Stage 2：Nginx 托管
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
# 覆盖默认 default.conf；构建上下文是 frontend/，nginx.conf 就放在同级
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# alpine 自带 wget（busybox），curl 没有。
# 必须写 127.0.0.1 而不是 localhost：容器 /etc/hosts 里 localhost 同时映射到 ::1，
# 而 nginx 只监听 IPv4 的 0.0.0.0:80，走 ::1 会 Connection refused，健康检查永远失败。
# （也不能靠加 listen [::]:80 解决——容器未启用 IPv6 时 nginx 会
#   报 Address family not supported by protocol 直接起不来）
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1

# 优雅停机
STOPSIGNAL SIGQUIT

CMD ["nginx", "-g", "daemon off;"]
