-- =============================================================
-- 压测数据清理
--
-- 用法（在项目根目录执行）：
--   docker compose exec -T mysql mysql -uroot -p"你的密码" \
--     --default-character-set=utf8mb4 blog < jmeter/cleanup-data.sql
--
-- 必须带 --default-character-set=utf8mb4：客户端默认 latin1，中文会报语法错误
-- （实测报错 "near '•°' at line 2"，看着像 SQL 语法问题，实为编码问题）。
--
-- 注意：文章详情接口不查 ES，所以正常情况下只清 MySQL 即可。
-- 但如果压测期间重启过后端，ArticleSyncTask 会把这 200 篇全量回填进 ES，
-- 那时还需要额外清理 ES（见文件末尾）。
-- =============================================================

SET NAMES utf8mb4;

DELETE FROM article WHERE id BETWEEN 900001 AND 900200;

-- 校验：应返回 0，且 user/article/comment/notification 回到压测前的基线
SELECT
  (SELECT COUNT(*) FROM user)         AS user数,
  (SELECT COUNT(*) FROM article)      AS article数,
  (SELECT COUNT(*) FROM comment)      AS comment数,
  (SELECT COUNT(*) FROM notification) AS notification数,
  (SELECT COUNT(*) FROM article WHERE id BETWEEN 900001 AND 900200) AS 残留压测文章;

-- -------------------------------------------------------------
-- 若压测期间重启过后端，ES 里也会有这 200 篇，执行下面的命令清理：
--
-- curl -X POST "http://localhost:19200/article/_delete_by_query" \
--   -H "Content-Type: application/json" \
--   -d '{"query":{"range":{"id":{"gte":900001,"lte":900200}}}}'
--
-- 然后刷新并确认（应回到压测前的数量）：
--
-- curl -X POST "http://localhost:19200/article/_refresh"
-- curl "http://localhost:19200/article/_count"
-- -------------------------------------------------------------
