# Blog Platform

本地全栈博客项目，包含 Spring Boot 后端、Vue 3 前端和 Docker Compose 基础设施。

## 首次启动

```powershell
Copy-Item .env.example .env
node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"
```

将生成的值填写到 `.env` 的 `JWT_SECRET`，然后运行：

```powershell
docker compose up -d --build
docker compose ps
```

访问 `http://localhost`。

## 开发运行

后端：

```powershell
$env:JWT_SECRET = "将上一步生成的密钥填写到这里"
cd blog
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd frontend
npm ci
npm run dev
```

## 测试

```powershell
cd blog
.\mvnw.cmd test

cd ..\frontend
npm test
npm run type-check
npx eslint .
npx oxlint .
```

## 数据库

`blog/src/main/resources/sql/init.sql` 是全新数据库的唯一基线。

`blog/src/main/resources/sql/migrations/` 只用于已有数据库手动升级，不会被 Compose 自动执行。
