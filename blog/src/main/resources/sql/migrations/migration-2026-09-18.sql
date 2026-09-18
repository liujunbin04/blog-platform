USE `blog`;

ALTER TABLE `notification`
  ADD COLUMN `event_key` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务事件唯一键' AFTER `article_id`,
  ADD UNIQUE KEY `uk_event_key` (`event_key`);
