-- =============================================================
-- 压测数据准备：造 200 篇文章，ID 区间 900001-900200
--
-- 用法（在项目根目录执行）：
--   docker compose exec -T mysql mysql -uroot -p"你的密码" \
--     --default-character-set=utf8mb4 blog < jmeter/prepare-data.sql
--
-- 必须带 --default-character-set=utf8mb4：
-- mysql 客户端在 Windows Git Bash 下默认用 latin1 连接，中文会存成双重编码乱码
-- （实测：不加时「压测文章」存成 C3A5C5BD...，加了才是正确的 E58E8BE6B5...）。
-- 文件里的 SET NAMES 是第二道保险。
--
-- 为什么需要：详情页接口不走读缓存，每次都真查库。真实库里只有 4 篇可访问
-- 文章，全部请求打在同一行上会触发严重的 MySQL 行锁竞争（实测单篇 QPS 只有
-- 78，分散后 140）。造 200 篇是为了让访问分散开，测出真实容量。
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO article
  (id, title, content, author_id, category, tags,
   view_count, like_count, status, hidden, deleted, create_time, update_time)
WITH RECURSIVE seq(n) AS (
  SELECT 900001
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 900200
)
SELECT
  n,
  CONCAT('压测文章-', n),
  CONCAT(
    '这是 JMeter 压测专用正文，编号 ', n, '。',
    '内容包含中文分词测试词汇：分布式系统、容器编排、全文检索、缓存穿透。',
    '正文长度刻意做得比列表页摘要长，用于模拟真实文章的序列化开销。'
  ),
  (SELECT id FROM user ORDER BY id LIMIT 1),   -- 挂到第一个真实用户名下
  'tech',
  'jmeter,压测',
  0,
  0,
  1,        -- status=1 已发布
  0,        -- hidden=0 未隐藏
  0,        -- deleted=0 未删除
  NOW(),
  NOW()
FROM seq;

-- 校验：应返回 200
SELECT COUNT(*) AS 压测文章数 FROM article WHERE id BETWEEN 900001 AND 900200;
