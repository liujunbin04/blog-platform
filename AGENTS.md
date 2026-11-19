# 仓库指南

## 构建、测试与开发命令

在 `blog/` 目录执行：`.\mvnw.cmd test`、`.\mvnw.cmd clean package`、`.\mvnw.cmd spring-boot:run`。在 `frontend/` 目录执行：`npm ci`、`npm run dev`、`npm test`、`npm run test:watch`、`npm run build`、`npm run lint`、`npm run format`。

在仓库根目录初始化本地服务栈：

```powershell
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

仅在需要删除并重新初始化 MySQL、Redis 和 RabbitMQ 数据卷时，才使用 `docker compose down -v`。

## 编码风格与命名约定

后端使用四空格缩进；前端格式由 `.prettierrc.json` 机械强制（两空格缩进、单引号、省略分号），Vue 组件名使用 `PascalCase`。提交前端改动前运行 `npm run lint` 和 `npm run format`。DTO、实体、VO、服务、控制器和仓储应保留在现有包中。

## 测试指南

行为变更应补充聚焦测试，涵盖权限、参数校验和边界情况。创建拉取请求前，运行上述前后端测试命令。

## 提交与拉取请求指南

提交信息使用带作用域的 Conventional Commits 格式，例如 `feat(frontend): add article filters` 或 `fix(blog): handle duplicate likes`。拉取请求应说明行为变更、列出验证命令、关联相关 issue；可见的 UI 变更应附截图。禁止提交 `.env`、密钥或生成的构建产物。
