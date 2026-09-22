-- =============================================================
-- 迁移脚本（2026-09-18）：
-- notification 表新增 event_key（业务事件唯一键，用于通知去重）
-- 执行方式：mysql -u root -p blog < migration-2026-09-18.sql
--
-- 为什么不能一条 ALTER 写完：
--   event_key 是 NOT NULL + 唯一索引，而存量通知行的 event_key 只能是同一个
--   默认值（如 ''），建唯一索引时会因重复直接报 1062。因此必须分三步：
--   先加可空列 → 回填出互不相同的值 → 再收紧约束并建唯一索引。
--
-- 全新环境直接执行最新 init.sql 即可，无需本脚本。
-- =============================================================
USE `blog`;

-- 1. 先加可空列：对存量行不做强制填值，避免 ALTER 失败
ALTER TABLE `notification`
  ADD COLUMN `event_key` VARCHAR(100) NULL
    COMMENT '业务事件唯一键' AFTER `article_id`;

-- 2. 回填存量行
--    取 LEGACY:<id> 而非伪造 LIKE:/COMMENT: 前缀：存量行已无法还原其来源记录 id，
--    伪造业务键会污染去重语义；LEGACY: 前缀也不会与后续真实业务键冲突。
UPDATE `notification`
SET `event_key` = CONCAT('LEGACY:', `id`)
WHERE `event_key` IS NULL;

-- 3. 收紧为 NOT NULL，与 init.sql 的列定义保持一致（业务侧始终会写入该值）
ALTER TABLE `notification`
  MODIFY COLUMN `event_key` VARCHAR(100) NOT NULL
    COMMENT '业务事件唯一键';

-- 4. 最后建唯一索引：此时各行的值已互不相同
ALTER TABLE `notification`
  ADD UNIQUE KEY `uk_event_key` (`event_key`);
