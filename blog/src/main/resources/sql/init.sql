-- =============================================================
-- 博客平台建表脚本（MySQL 8）
-- 字符集：utf8mb4（utf8mb4_0900_ai_ci）；引擎：InnoDB
-- 执行方式见文件末尾说明，或命令行：
--   mysql -u root -p < init.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS `blog`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `blog`;

-- -------------------------------------------------------------
-- 1. 用户表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(50)     NOT NULL COMMENT '用户名',
  `password`    VARCHAR(100)    NOT NULL COMMENT '密码（BCrypt 密文）',
  `email`       VARCHAR(100)    DEFAULT NULL COMMENT '邮箱',
  `avatar`      VARCHAR(255)    DEFAULT NULL COMMENT '头像 URL',
  `nickname`    VARCHAR(50)     DEFAULT NULL COMMENT '昵称',
  `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username_deleted` (`username`, `deleted`),
  UNIQUE KEY `uk_email_deleted` (`email`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';

-- -------------------------------------------------------------
-- 2. 文章表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `article` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`       VARCHAR(200)    NOT NULL COMMENT '标题',
  `content`     TEXT            NOT NULL COMMENT '正文',
  `author_id`   BIGINT UNSIGNED NOT NULL COMMENT '作者 id（user.id）',
  `category`    VARCHAR(50)     DEFAULT NULL COMMENT '分类',
  `tags`        VARCHAR(255)    DEFAULT NULL COMMENT '标签，多个以逗号分隔',
  `view_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '浏览量',
  `like_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '点赞数',
  `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0 草稿 1 已发布',
  `hidden`      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否隐藏：0 公开 1 隐藏（作者可隐藏已发布文章）',
  `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_status_create` (`status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文章表';

-- -------------------------------------------------------------
-- 3. 评论表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `comment` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `article_id`  BIGINT UNSIGNED NOT NULL COMMENT '文章 id（article.id）',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '评论者 id（user.id）',
  `parent_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父评论 id，0 表示顶级评论（始终指向一级评论）',
  `reply_to_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '被回复的评论 id，0 表示一级评论',
  `reply_to_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '回复目标用户 id，可空（一级评论为 NULL）',
  `content`     TEXT            NOT NULL COMMENT '评论内容',
  `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_article` (`article_id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '评论表';

-- -------------------------------------------------------------
-- 4. 点赞记录表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `like_record` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '点赞用户 id（user.id）',
  `article_id`  BIGINT UNSIGNED NOT NULL COMMENT '文章 id（article.id）',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_article` (`user_id`, `article_id`),
  KEY `idx_article` (`article_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '点赞记录表';

-- -------------------------------------------------------------
-- 5. 收藏表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `favorite` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '收藏用户 id（user.id）',
  `article_id`  BIGINT UNSIGNED NOT NULL COMMENT '文章 id（article.id）',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_article` (`user_id`, `article_id`),
  KEY `idx_article` (`article_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '收藏表';

-- -------------------------------------------------------------
-- 6. 站内通知表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notification` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `recipient_id` BIGINT UNSIGNED NOT NULL COMMENT '收件人 id（user.id）',
  `sender_id`    BIGINT UNSIGNED NOT NULL COMMENT '发件人 id（user.id）',
  `type`         VARCHAR(20)     NOT NULL COMMENT '类型：COMMENT/LIKE（同 NotificationMessage.type 取值）',
  `article_id`   BIGINT UNSIGNED NOT NULL COMMENT '关联文章 id',
  `content`      VARCHAR(1000)   DEFAULT NULL COMMENT '通知内容摘要',
  `is_read`      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读 1 已读',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_recipient` (`recipient_id`, `is_read`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '站内通知表';
