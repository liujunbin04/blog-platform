-- =============================================================
-- 迁移脚本（2026-08-26）：
-- B1 用户唯一索引改造 + B6 关系表冗余列删除 + B2 通知表
-- 执行方式：mysql -u root -p blog < migration-2026-08-26.sql
-- =============================================================
USE `blog`;

-- B1: 用户唯一索引改为含 deleted 的组合索引。
-- MP 逻辑删除值固定为 1：同一用户名最多一条活跃记录(deleted=0)，软删后可重名；
-- 但多次注册删除会撞历史行(deleted=1)——教程场景可接受，
-- 生产方案应改用 deleted_at 时间戳或应用层保证
ALTER TABLE `user`
  DROP INDEX `uk_username`,
  DROP INDEX `uk_email`,
  ADD UNIQUE KEY `uk_username_deleted` (`username`, `deleted`),
  ADD UNIQUE KEY `uk_email_deleted` (`email`, `deleted`);

-- B6: like_record/favorite 物理删除语义下 deleted 恒为 0，纯冗余
ALTER TABLE `like_record` DROP COLUMN `deleted`;
ALTER TABLE `favorite` DROP COLUMN `deleted`;

-- B2: 站内通知表
CREATE TABLE IF NOT EXISTS `notification` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `recipient_id` BIGINT UNSIGNED NOT NULL COMMENT '收件人 id（user.id）',
  `sender_id`    BIGINT UNSIGNED NOT NULL COMMENT '发件人 id（user.id）',
  `type`         VARCHAR(20)     NOT NULL COMMENT '类型：COMMENT/LIKE（同 NotificationMessage.type 取值）',
  `article_id`   BIGINT UNSIGNED NOT NULL COMMENT '关联文章 id',
  `content`      VARCHAR(500)    DEFAULT NULL COMMENT '通知内容摘要',
  `is_read`      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读 1 已读',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_recipient` (`recipient_id`, `is_read`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '站内通知表';
