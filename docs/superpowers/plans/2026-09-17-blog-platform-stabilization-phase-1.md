# 博客平台启动与安全基线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复本地环境的 JWT、数据库初始化、端口暴露、跨域和正文大小配置，使项目能够安全、可重复地启动。

**Architecture:** 保留现有 Spring Boot、Vue 和 Docker Compose 结构，只调整配置边界和数据库基线。JWT 改为显式失败，数据库只自动执行最终基线，历史迁移供已有数据库手动使用。

**Tech Stack:** Java 17、Spring Boot 3.5、Maven、MySQL 8.4、Docker Compose、JUnit 5、Jakarta Validation、MockMvc

---

## 文件结构

**创建：**

- `blog/src/test/java/com/example/blog/utils/JwtUtilTest.java`：验证 JWT 密钥配置和 token 往返。
- `blog/src/test/java/com/example/blog/config/CorsConfigTest.java`：验证本地跨域来源。
- `blog/src/test/java/com/example/blog/dto/ArticleDTOValidationTest.java`：验证正文大小边界。
- `blog/src/main/resources/sql/migrations/README.md`：说明已有数据库的手动迁移方式。
- `README.md`：提供首次启动、密钥生成和验证说明。

**修改：**

- `blog/src/main/java/com/example/blog/utils/JwtUtil.java`：启动时校验 JWT 密钥。
- `blog/src/main/resources/application.yml`：移除 JWT 默认密钥。
- `docker-compose.yml`：强制 JWT 环境变量、限制端口绑定、只执行数据库基线。
- `.env.example`：明确本地密钥生成方式。
- `blog/src/main/resources/sql/init.sql`：将文章正文调整为 `MEDIUMTEXT`。
- `frontend/nginx.conf`：将请求体限制调整为 2MB。
- `blog/src/main/java/com/example/blog/dto/ArticleDTO.java`：限制正文最大长度。
- `blog/src/main/java/com/example/blog/config/CorsConfig.java`：只允许本地前端来源。

**移动：**

- `blog/src/main/resources/sql/migration-2026-08-26.sql` → `blog/src/main/resources/sql/migrations/migration-2026-08-26.sql`
- `blog/src/main/resources/sql/migration-2026-08-28.sql` → `blog/src/main/resources/sql/migrations/migration-2026-08-28.sql`

---

### Task 1: JWT 密钥显式配置与启动校验

**Files:**
- Modify: `blog/src/main/java/com/example/blog/utils/JwtUtil.java`
- Modify: `blog/src/main/resources/application.yml:79-82`
- Modify: `docker-compose.yml:209-210`
- Modify: `.env.example:47-53`
- Test: `blog/src/test/java/com/example/blog/utils/JwtUtilTest.java`

- [ ] **Step 1: 写失败测试**

创建 `blog/src/test/java/com/example/blog/utils/JwtUtilTest.java`：

```java
package com.example.blog.utils;

import com.example.blog.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String VALID_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void constructorRejectsMissingSecret() {
        JwtProperties properties = properties(null);

        assertThrows(IllegalStateException.class, () -> new JwtUtil(properties));
    }

    @Test
    void constructorRejectsSecretShorterThan64Bytes() {
        JwtProperties properties = properties("too-short");

        assertThrows(IllegalStateException.class, () -> new JwtUtil(properties));
    }

    @Test
    void generatesAndParsesTokenWithValidSecret() {
        JwtUtil jwtUtil = new JwtUtil(properties(VALID_SECRET));

        String token = jwtUtil.generateToken(42L, "tester");
        Claims claims = jwtUtil.parseToken(token);

        assertEquals("42", claims.getSubject());
        assertEquals("tester", claims.get("username", String.class));
    }

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpiration(60_000L);
        return properties;
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
.\mvnw.cmd -q -Dtest=JwtUtilTest test
```

Expected: FAIL。缺少密钥或过短密钥时，当前实现没有抛出 `IllegalStateException`。

- [ ] **Step 3: 实现启动校验**

将 `blog/src/main/java/com/example/blog/utils/JwtUtil.java` 的构造器改为：

```java
public JwtUtil(JwtProperties properties) {
    String secret = properties.getSecret();
    if (secret == null || secret.isBlank()) {
        throw new IllegalStateException("jwt.secret must be configured");
    }
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 64) {
        throw new IllegalStateException("jwt.secret must contain at least 64 bytes");
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.expiration = properties.getExpiration();
}
```

将 `blog/src/main/resources/application.yml` 的 JWT secret 改为：

```yaml
  secret: ${JWT_SECRET}
```

将 `docker-compose.yml` 后端的 JWT 环境变量改为：

```yaml
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET must be set in .env}
```

将 `.env.example` 的 JWT 说明改为：

```dotenv
# 后端 JWT
# HS512 要求密钥长度至少 64 字节。
# 生成命令：
#   node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"
# 必须填写，留空时 Docker Compose 会拒绝启动。
JWT_SECRET=
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
.\mvnw.cmd -q -Dtest=JwtUtilTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/utils/JwtUtil.java blog/src/main/resources/application.yml blog/src/test/java/com/example/blog/utils/JwtUtilTest.java docker-compose.yml .env.example
git commit -m "fix(blog): 强制配置安全的 JWT 密钥"
```

---

### Task 2: 分离数据库基线与历史迁移

**Files:**
- Modify: `docker-compose.yml:51-55`
- Move: `blog/src/main/resources/sql/migration-2026-08-26.sql`
- Move: `blog/src/main/resources/sql/migration-2026-08-28.sql`
- Create: `blog/src/main/resources/sql/migrations/README.md`

- [ ] **Step 1: 用隔离项目验证当前初始化失败**

Run:

```powershell
$env:MYSQL_PORT = "43306"
$env:JWT_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
docker compose -p blog-init-test up -d mysql
docker compose -p blog-init-test logs mysql
```

Expected: FAIL。MySQL 初始化日志包含缺失索引或重复列错误。原因与 `init.sql` 已包含最终结构有关。

清理隔离数据卷：

```powershell
docker compose -p blog-init-test down -v
Remove-Item Env:MYSQL_PORT
Remove-Item Env:JWT_SECRET
```

- [ ] **Step 2: 移动历史迁移**

将以下文件移动并保持内容不变：

```text
blog/src/main/resources/sql/migration-2026-08-26.sql
  -> blog/src/main/resources/sql/migrations/migration-2026-08-26.sql

blog/src/main/resources/sql/migration-2026-08-28.sql
  -> blog/src/main/resources/sql/migrations/migration-2026-08-28.sql
```

- [ ] **Step 3: 只挂载最终基线**

将 `docker-compose.yml` 的 SQL volume 改为：

```yaml
      - ./blog/src/main/resources/sql/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
```

创建 `blog/src/main/resources/sql/migrations/README.md`：

````markdown
# 历史数据库迁移

`init.sql` 是全新数据库的唯一基线，Compose 只自动执行该文件。

本目录中的迁移脚本只用于升级已经存在的本地数据库，不会被 Compose 自动执行。

## 手动执行

```powershell
Get-Content -Raw .\migration-2026-08-26.sql |
  docker compose exec -T mysql mysql -uroot -pblog_dev_pwd blog

Get-Content -Raw .\migration-2026-08-28.sql |
  docker compose exec -T mysql mysql -uroot -pblog_dev_pwd blog
```

若本地数据可以删除，优先使用全新初始化：

```powershell
docker compose down -v
docker compose up -d --build
```
````

- [ ] **Step 4: 验证全新初始化成功**

Run:

```powershell
$env:MYSQL_PORT = "43306"
$env:JWT_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
docker compose -p blog-init-test up -d --wait mysql
docker compose -p blog-init-test exec -T mysql mysql -uroot -pblog_dev_pwd -e "USE blog; SHOW TABLES; SHOW INDEX FROM user;"
```

Expected: PASS。输出包含 `user`、`article`、`comment`、`like_record`、`favorite`、`notification`，且用户表包含 `uk_username_deleted` 和 `uk_email_deleted`。

清理：

```powershell
docker compose -p blog-init-test down -v
Remove-Item Env:MYSQL_PORT
Remove-Item Env:JWT_SECRET
```

- [ ] **Step 5: 提交**

```powershell
git add -- docker-compose.yml blog/src/main/resources/sql
git commit -m "fix(db): 分离数据库基线和历史迁移"
```

---

### Task 3: 限制本地端口和跨域来源

**Files:**
- Modify: `docker-compose.yml:49-50,96-97,126-127,150-151,214-215,229-230`
- Modify: `blog/src/main/java/com/example/blog/config/CorsConfig.java`
- Test: `blog/src/test/java/com/example/blog/config/CorsConfigTest.java`

- [ ] **Step 1: 写跨域失败测试**

创建 `blog/src/test/java/com/example/blog/config/CorsConfigTest.java`：

```java
package com.example.blog.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CorsConfigTest {

    @Test
    void allowsOnlyLocalFrontendOrigins() {
        assertArrayEquals(
                new String[]{"http://localhost", "http://localhost:5173"},
                CorsConfig.ALLOWED_ORIGINS
        );
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
.\mvnw.cmd -q -Dtest=CorsConfigTest test
```

Expected: FAIL。当前 `CorsConfig` 还没有 `ALLOWED_ORIGINS` 常量。

- [ ] **Step 3: 收紧跨域并绑定本地端口**

在 `CorsConfig` 中增加：

```java
    static final String[] ALLOWED_ORIGINS = {
            "http://localhost",
            "http://localhost:5173"
    };
```

将 `CorsConfig.addCorsMappings` 改为：

```java
registry.addMapping("/**")
        .allowedOrigins(ALLOWED_ORIGINS)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .maxAge(3600);
```

将 `docker-compose.yml` 中所有宿主机端口映射改为只绑定本机：

```yaml
      - "127.0.0.1:${MYSQL_PORT:-33306}:3306"
      - "127.0.0.1:${REDIS_PORT:-16379}:6379"
      - "127.0.0.1:${ES_PORT:-19200}:9200"
      - "127.0.0.1:${RABBITMQ_PORT:-56720}:5672"
      - "127.0.0.1:${RABBITMQ_MGMT_PORT:-15672}:15672"
      - "127.0.0.1:${BACKEND_PORT:-18080}:8080"
      - "127.0.0.1:${FRONTEND_PORT:-80}:80"
```

- [ ] **Step 4: 运行测试和配置检查**

Run:

```powershell
cd blog
.\mvnw.cmd -q -Dtest=CorsConfigTest test
cd ..
docker compose config
```

Expected: PASS，且 Compose 输出中的 `host_ip` 都是 `127.0.0.1`。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/config/CorsConfig.java blog/src/test/java/com/example/blog/config/CorsConfigTest.java docker-compose.yml
git commit -m "fix(config): 限制本地端口和跨域来源"
```

---

### Task 4: 统一文章正文大小限制

**Files:**
- Modify: `blog/src/main/java/com/example/blog/dto/ArticleDTO.java:18-20`
- Modify: `blog/src/main/resources/sql/init.sql:38`
- Modify: `frontend/nginx.conf:18`
- Test: `blog/src/test/java/com/example/blog/dto/ArticleDTOValidationTest.java`

- [ ] **Step 1: 写正文边界测试**

创建 `blog/src/test/java/com/example/blog/dto/ArticleDTOValidationTest.java`：

```java
package com.example.blog.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsContentLargerThanTwoMillionCharacters() {
        ArticleDTO dto = validArticle();
        dto.setContent("a".repeat(2_000_001));

        Set<ConstraintViolation<ArticleDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("content")));
    }

    @Test
    void acceptsContentAtTwoMillionCharacters() {
        ArticleDTO dto = validArticle();
        dto.setContent("a".repeat(2_000_000));

        Set<ConstraintViolation<ArticleDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream()
                .noneMatch(v -> v.getPropertyPath().toString().equals("content")));
    }

    private ArticleDTO validArticle() {
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("title");
        dto.setContent("content");
        dto.setStatus(1);
        return dto;
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
.\mvnw.cmd -q -Dtest=ArticleDTOValidationTest test
```

Expected: FAIL。超过 2,000,000 字符的正文当前没有约束。

- [ ] **Step 3: 统一大小限制**

在 `ArticleDTO.content` 上增加：

```java
    @Size(max = 2_000_000, message = "文章内容不能超过 2MB")
    private String content;
```

将 `init.sql` 的文章正文字段改为：

```sql
  `content`     MEDIUMTEXT      NOT NULL COMMENT '正文',
```

将 `frontend/nginx.conf` 改为：

```nginx
    client_max_body_size 2m;
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
.\mvnw.cmd -q -Dtest=ArticleDTOValidationTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/dto/ArticleDTO.java blog/src/main/resources/sql/init.sql frontend/nginx.conf blog/src/test/java/com/example/blog/dto/ArticleDTOValidationTest.java
git commit -m "fix(article): 统一正文大小限制"
```

---

### Task 5: 补充本地启动文档并完成批次验证

**Files:**
- Create: `README.md`
- Modify: `docs/docker-runbook.md`

- [ ] **Step 1: 创建根目录 README**

创建 `README.md`：

````markdown
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
````

- [ ] **Step 2: 更新 Docker 手册**

在 `docs/docker-runbook.md` 的“常用命令”之前增加：

````markdown
## 首次配置

Compose 不再提供默认 JWT 密钥。首次启动前：

```powershell
Copy-Item .env.example .env
node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"
```

把生成值写入 `.env` 的 `JWT_SECRET`。未配置时后端和 Compose 会直接拒绝启动。

`init.sql` 是全新数据库的唯一基线。历史迁移脚本位于
`blog/src/main/resources/sql/migrations/`，只用于已有数据库手动升级。
````

- [ ] **Step 3: 运行完整验证**

Run:

```powershell
cd blog
.\mvnw.cmd test
cd ..\frontend
npm test
npm run type-check
npx eslint .
npx oxlint .
cd ..
docker compose config
```

Expected: 所有测试和静态检查通过，Compose 配置有效。

- [ ] **Step 4: 验证缺少 JWT 时启动失败**

使用示例环境文件验证缺少 JWT 时配置失败：

```powershell
docker compose --env-file .env.example config
```

Expected: FAIL，错误信息包含 `JWT_SECRET must be set in .env`。

- [ ] **Step 5: 提交**

```powershell
git add -- README.md docs/docker-runbook.md
git commit -m "docs: 补充本地启动和安全配置说明"
```

---

## 批次验收清单

- [ ] 缺少 `JWT_SECRET` 时 Compose 拒绝启动。
- [ ] JWT 密钥短于 64 字节时后端拒绝启动。
- [ ] `init.sql` 可以在全新 MySQL 数据卷中独立执行成功。
- [ ] Compose 不再自动执行历史迁移。
- [ ] MySQL、Redis、RabbitMQ、Elasticsearch、后端和前端端口只绑定 `127.0.0.1`。
- [ ] 只有本地前端来源通过 CORS。
- [ ] 文章正文限制为 2,000,000 字符，数据库可容纳该大小。
- [ ] 后端测试、前端测试、类型检查和静态检查全部通过。
