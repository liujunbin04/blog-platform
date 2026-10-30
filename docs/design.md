# 博客平台 — 需求文档 & 数据库设计

> 所属项目：`blog-platform/blog`（Spring Boot 3.5.16 / MyBatis-Plus 3.5.9 / MySQL 8.0 / Redis / RabbitMQ / JWT）
> 文档日期：2026-08-22

## 一、项目概述

面向读者的博客平台后端，核心能力：用户注册登录（JWT 无状态认证）、文章管理、评论/点赞/收藏互动、按时间与热度排序、全文搜索、热门文章缓存、互动事件异步通知。

## 二、功能需求（简要）

### 2.1 用户注册/登录（JWT）
- 注册：用户名 + 密码；用户名唯一，密码 BCrypt 加密存储
- 登录：校验通过后签发 JWT（有效期 7 天），前端通过 `Authorization: Bearer <token>` 携带
- Spring Security 过滤器链解析 JWT 并注入认证上下文，无需服务端 Session
- 接口：`POST /api/auth/register`、`POST /api/auth/login`、`GET /api/user/me`

### 2.2 文章管理
- 发布（支持存草稿）、编辑、删除（软删除，仅作者本人或管理员）、查看详情（浏览量 +1）、分页列表（仅已发布）
- 接口：`POST /api/articles`、`PUT /api/articles/{id}`、`DELETE /api/articles/{id}`、`GET /api/articles/{id}`、`GET /api/articles?page=&size=&sort=`

### 2.3 评论 / 点赞 / 收藏
- 评论：发表、回复（parent_id，两级回复）、删除（作者/管理员），评论数实时维护
- 点赞：toggle 式点赞/取消，幂等（联合唯一键）
- 收藏：toggle 式收藏/取消，支持「我的收藏」分页列表
- 接口：`POST /api/articles/{id}/comments`、`GET /api/articles/{id}/comments`、`DELETE /api/comments/{id}`、`POST|DELETE /api/articles/{id}/like`、`POST|DELETE /api/articles/{id}/favorite`、`GET /api/user/favorites`

### 2.4 文章列表排序
- 时间排序：`created_at DESC`，走 `idx_status_created` 索引
- 热度排序：`hot_score DESC`，热度分由定时任务按权重公式刷新（见 5.3），走 `idx_status_hot` 索引

### 2.5 全文搜索
- MySQL 8.0 FULLTEXT ngram 全文索引（中文 2-gram 分词），覆盖标题/摘要/正文
- 关键词搜索 + 分页返回，相关度排序
- 接口：`GET /api/articles/search?keyword=`
- 备选：数据量增长后可平滑迁移 Elasticsearch（表结构不变，仅替换搜索层）

### 2.6 热门文章缓存
- Redis ZSET `hot:articles`（member=article_id，score=hot_score），定时任务每 10 分钟重建，首页热门榜直接读缓存
- 文章详情缓存（Spring Cache，key=`article::{id}`，TTL 10 分钟，只缓存公开文章）
- 防穿透（不缓存 null）、防击穿（缓存未命中时用 Redisson 分布式锁串行化回源）；雪崩由 key 按文章 id 天然分散来规避，未做额外的 TTL 随机化

### 2.7 评论/点赞异步通知
- 评论、点赞事件投递 RabbitMQ（Exchange `blog.event` → Queue `blog.notify`）
- 通知服务消费事件，写入 `notification` 表（站内信），用户分页查询、标记已读
- 消息体 JSON：`{"type":1,"articleId":100,"commentId":200,"senderId":1,"receiverId":2}`
- 消费幂等：`notification` 表联合唯一索引去重，重复投递直接忽略

## 三、ER 关系

```
user ──1:N── article ──1:N── comment
  │            │
  │N           │N
  ├── article_like(user_id, article_id)      ← N:M 点赞（中间表）
  └── article_favorite(user_id, article_id)  ← N:M 收藏（中间表）
user ──1:N── notification(receiver_id)
category ──1:N── article（预留）
```

## 四、表结构设计

通用约定：引擎 InnoDB，字符集 utf8mb4，主键 BIGINT UNSIGNED 自增；`created_at` 默认 CURRENT_TIMESTAMP，`updated_at` 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP（下文不再重复说明）。

### 4.1 user — 用户表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED | 主键，自增 |
| username | VARCHAR(50) | 登录名，唯一 |
| password | VARCHAR(100) | BCrypt 密文 |
| nickname | VARCHAR(50) | 昵称，默认 '' |
| email | VARCHAR(100) | 邮箱，可空，唯一 |
| avatar | VARCHAR(255) | 头像 URL，默认 '' |
| bio | VARCHAR(255) | 个人简介，默认 '' |
| role | VARCHAR(20) | 角色：USER / ADMIN，默认 USER |
| status | TINYINT | 1 正常 / 0 禁用，默认 1 |
| created_at / updated_at | DATETIME | 创建/更新时间 |

索引：`PRIMARY(id)`；`UNIQUE uk_username(username)` — 登录唯一；`UNIQUE uk_email(email)`

### 4.2 article — 文章表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED | 主键，自增 |
| user_id | BIGINT UNSIGNED | 作者 id |
| category_id | BIGINT UNSIGNED | 分类 id，可空（预留） |
| title | VARCHAR(200) | 标题 |
| summary | VARCHAR(500) | 摘要，默认 ''（列表/搜索展示） |
| content | LONGTEXT | 正文 |
| cover | VARCHAR(255) | 封面图 URL，默认 '' |
| status | TINYINT | 0 草稿 / 1 已发布，默认 0 |
| view_count | INT UNSIGNED | 浏览量，默认 0 |
| like_count | INT UNSIGNED | 点赞数，默认 0 |
| comment_count | INT UNSIGNED | 评论数，默认 0 |
| favorite_count | INT UNSIGNED | 收藏数，默认 0 |
| hot_score | INT | 热度分，默认 0，定时任务刷新 |
| is_top | TINYINT | 置顶 0/1，默认 0 |
| deleted | TINYINT | 逻辑删除 0/1，默认 0（MP @TableLogic） |
| created_at / updated_at | DATETIME | 创建/更新时间 |

索引：
- `PRIMARY(id)`
- `KEY idx_user(user_id)` — 「我的文章」
- `KEY idx_category(category_id)` — 分类筛选
- `KEY idx_status_created(status, created_at)` — 时间排序列表
- `KEY idx_status_hot(status, hot_score)` — 热度排序列表
- `FULLTEXT ft_search(title, summary, content) WITH PARSER ngram` — 全文搜索（中文分词）

### 4.3 comment — 评论表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED | 主键，自增 |
| article_id | BIGINT UNSIGNED | 文章 id |
| user_id | BIGINT UNSIGNED | 评论者 id |
| parent_id | BIGINT UNSIGNED | 父评论 id，0=顶级评论 |
| reply_to_user_id | BIGINT UNSIGNED | 回复目标用户 id，可空 |
| content | VARCHAR(1000) | 评论内容 |
| status | TINYINT | 1 正常 / 0 已删除或隐藏，默认 1 |
| created_at | DATETIME | 创建时间 |

索引：`PRIMARY(id)`；`KEY idx_article(article_id, created_at)` — 按文章拉评论列表；`KEY idx_user(user_id)`

### 4.4 article_like — 文章点赞表（中间表）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED | 主键，自增 |
| user_id | BIGINT UNSIGNED | 点赞用户 id |
| article_id | BIGINT UNSIGNED | 文章 id |
| status | TINYINT | 1 已赞 / 0 已取消，默认 1（toggle 保留记录，兼作通知去重） |
| created_at / updated_at | DATETIME | 创建/更新时间 |

索引：`PRIMARY(id)`；`UNIQUE uk_user_article(user_id, article_id)` — 幂等 + 快速查询「是否已赞」；`KEY idx_article(article_id)`

### 4.5 article_favorite — 收藏表（中间表）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED | 主键，自增 |
| user_id | BIGINT UNSIGNED | 收藏用户 id |
| article_id | BIGINT UNSIGNED | 文章 id |
| created_at | DATETIME | 创建时间 |

索引：`PRIMARY(id)`；`UNIQUE uk_user_article(user_id, article_id)` — 幂等；`KEY idx_user_time(user_id, created_at)` — 「我的收藏」分页

### 4.6 notification — 站内通知表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED | 主键，自增 |
| receiver_id | BIGINT UNSIGNED | 接收者（被评论/被点赞的文章作者） |
| sender_id | BIGINT UNSIGNED | 触发者，可空（系统通知） |
| type | TINYINT | 1 评论 / 2 点赞 / 3 收藏 / 4 系统 |
| article_id | BIGINT UNSIGNED | 关联文章，可空 |
| comment_id | BIGINT UNSIGNED | 关联评论，可空 |
| content | VARCHAR(255) | 通知文案 |
| is_read | TINYINT | 0 未读 / 1 已读，默认 0 |
| created_at | DATETIME | 创建时间 |

索引：`PRIMARY(id)`；`KEY idx_receiver(receiver_id, is_read, created_at)` — 「我的通知」列表；`UNIQUE uk_dedup(receiver_id, type, article_id, comment_id, sender_id)` — MQ 幂等去重

### 4.7 category — 分类表（可选预留）

> 需求未明确要求分类，本表为扩展预留，可按需裁剪；裁剪时同步移除 article.category_id 字段与 idx_category 索引。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT UNSIGNED | 主键，自增 |
| name | VARCHAR(50) | 分类名，唯一 |
| sort | INT | 排序权重，默认 0 |
| created_at | DATETIME | 创建时间 |

索引：`PRIMARY(id)`；`UNIQUE uk_name(name)`

## 五、设计要点

### 5.1 认证与权限
- JWT 无状态认证；密码 BCrypt
- 写操作权限：作者本人或 ADMIN（编辑/删除文章、删除评论）；点赞/收藏/评论需登录

### 5.2 计数一致性
- 浏览量：Redis `article:view:{id}` INCR，定时任务批量刷回 `view_count`
- 点赞/评论/收藏计数：先维护 Redis 计数，定时刷回 DB；`*_count` 字段最终一致，允许短时间偏差

### 5.3 热度分公式（定时任务每 10 分钟刷新）

```
hot_score = view_count × 1 + like_count × 5 + comment_count × 10 + favorite_count × 8
```

只统计近 30 天文章（防止老文章长期霸榜），权重可配置化。

### 5.4 Redis Key 约定

| Key | 类型 | 说明 |
|---|---|---|
| hot:articles | ZSET | 热门榜（score=hot_score，定时重建） |
| hot:articles:building | ZSET | 重建中的临时 key，构建完成后 rename 覆盖 |
| article:view:{id} | INCR | 浏览量计数 |
| blog:cache:article::{id} | String | 文章详情缓存（Spring Cache），TTL 10min |
| lock:article::{id} | String | 防击穿互斥锁（Redisson） |
| hot:dedup:{eventKey} | String | 热度加分事件去重，TTL 2h |

### 5.5 MQ 约定
- Exchange `blog.event`（direct）→ Queue `blog.notify`，routing key：`notify.comment` / `notify.like`
- 消费失败重试 3 次后进入死信队列 `blog.notify.dlq`，人工兜底
- 幂等由 notification 表 `uk_dedup` 唯一索引保证

### 5.6 实现注意事项
- 逻辑删除：实现时在 application.yml 开启 `mybatis-plus.global-config.db-config.logic-delete-field: deleted`（当前脚手架未开启）
- 分页：注册 MyBatis-Plus `PaginationInnerInterceptor`（mybatis-plus-jsqlparser 已引入，不加则分页静默失效）
- 全文搜索：`MATCH...AGAINST` 与普通索引并存，仅搜索接口使用；短关键词（<2 字）ngram 效果差，可补 LIKE 兜底

## 六、建表 SQL

```sql
-- 1. 用户表
CREATE TABLE `user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(50)  NOT NULL COMMENT '登录名',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  `nickname`    VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '昵称',
  `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `avatar`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像 URL',
  `bio`         VARCHAR(255) NOT NULL DEFAULT '' COMMENT '个人简介',
  `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 USER/ADMIN',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 禁用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- 2. 文章表
CREATE TABLE `article` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '作者 id',
  `category_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '分类 id（预留）',
  `title`          VARCHAR(200) NOT NULL COMMENT '标题',
  `summary`        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '摘要',
  `content`        LONGTEXT     NOT NULL COMMENT '正文',
  `cover`          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '封面图 URL',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 草稿 1 已发布',
  `view_count`     INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览量',
  `like_count`     INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
  `comment_count`  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数',
  `favorite_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏数',
  `hot_score`      INT          NOT NULL DEFAULT 0 COMMENT '热度分（定时刷新）',
  `is_top`         TINYINT      NOT NULL DEFAULT 0 COMMENT '置顶 0/1',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0/1',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status_created` (`status`, `created_at`),
  KEY `idx_status_hot` (`status`, `hot_score`),
  FULLTEXT KEY `ft_search` (`title`, `summary`, `content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文章表';

-- 3. 评论表
CREATE TABLE `comment` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `article_id`      BIGINT UNSIGNED NOT NULL COMMENT '文章 id',
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '评论者 id',
  `parent_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父评论 id，0=顶级',
  `reply_to_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '回复目标用户 id',
  `content`         VARCHAR(1000) NOT NULL COMMENT '评论内容',
  `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '1 正常 0 删除/隐藏',
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_article` (`article_id`, `created_at`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评论表';

-- 4. 点赞表
CREATE TABLE `article_like` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '点赞用户 id',
  `article_id` BIGINT UNSIGNED NOT NULL COMMENT '文章 id',
  `status`     TINYINT         NOT NULL DEFAULT 1 COMMENT '1 已赞 0 已取消',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_article` (`user_id`, `article_id`),
  KEY `idx_article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文章点赞表';

-- 5. 收藏表
CREATE TABLE `article_favorite` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '收藏用户 id',
  `article_id` BIGINT UNSIGNED NOT NULL COMMENT '文章 id',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_article` (`user_id`, `article_id`),
  KEY `idx_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收藏表';

-- 6. 站内通知表
CREATE TABLE `notification` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `receiver_id` BIGINT UNSIGNED NOT NULL COMMENT '接收者 id',
  `sender_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '触发者 id',
  `type`        TINYINT         NOT NULL COMMENT '1 评论 2 点赞 3 收藏 4 系统',
  `article_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '关联文章 id',
  `comment_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '关联评论 id',
  `content`     VARCHAR(255)    NOT NULL COMMENT '通知文案',
  `is_read`     TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未读 1 已读',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_receiver` (`receiver_id`, `is_read`, `created_at`),
  UNIQUE KEY `uk_dedup` (`receiver_id`, `type`, `article_id`, `comment_id`, `sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内通知表';

-- 7. 分类表（可选预留）
CREATE TABLE `category` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`       VARCHAR(50) NOT NULL COMMENT '分类名',
  `sort`       INT         NOT NULL DEFAULT 0 COMMENT '排序权重',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分类表';
```
