-- =============================================================
-- 迁移脚本（2026-08-28）：
-- 评论表新增 reply_to_id（楼中楼「回复@被回复人」）
-- 执行方式：mysql -u root -p blog < migration-2026-08-28.sql
-- 说明：本环境的库中已预先存在 reply_to_user_id（design.md 原始设计）
-- 与 article.hidden 两列，代码此前未使用；本迁移仅补 reply_to_id。
-- 全新环境直接执行最新 init.sql 即可，无需本脚本
-- =============================================================
USE `blog`;

-- 楼中楼回复需要记录实际的被回复目标：
-- parent_id 始终指向一级评论（前端扁平化挂树），reply_to_id 记录
-- 用户实际点击「回复」的那条评论，reply_to_user_id 记录被回复人（展示
-- 「回复@被回复人」并据此发通知，被回复评论被删后昵称仍可解析）。
-- 存量数据默认 0 / NULL（一级评论 / 未指定），无回填必要
ALTER TABLE `comment`
  ADD COLUMN `reply_to_id` BIGINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '被回复的评论 id，0 表示一级评论' AFTER `parent_id`;
