# JMeter 压测计划：文章详情接口

接口：`GET /api/articles/{id}`
配套脚本：`jmeter/`（两份 .jmx + 造数/清理 SQL）
生成日期：2026-08-29

> 本文所有性能数字均为**本机实测**，不是估算。测试环境：Docker Desktop，
> 宿主机 7.6GB 内存，后端堆 `-Xms256m -Xmx512m`，MySQL 8.4 单实例。

---

## 0. 结论先行

| 项目 | 实测值 |
| --- | --- |
| 单请求基线延迟 | 平均 37ms（中位 35ms） |
| **QPS 天花板（200 篇文章分散访问）** | **约 980**，饱和点约在并发 80 |
| 推荐验收目标 | **800 QPS，P95 < 150ms，错误率 0** |
| 首要瓶颈 | 每次请求同步写「浏览量 +1」，产生写事务 → redo 刷盘 + 行锁 |

---

## 1. 被测接口画像

压测前必须知道这个接口到底做了什么，否则测出的数字无法解释。

```java
@Transactional                              // 开事务
@CachePut(value = "article", key = "#id")   // 请求结束写 Redis
public ArticleVO getDetail(Long id) {
    Article article = getById(id);                                    // SELECT ① 存在性+权限
    ...
    lambdaUpdate().setSql("view_count = view_count + 1")...update();  // UPDATE ② 行锁
    ArticleVO vo = baseMapper.selectDetailWithAuthor(id);             // SELECT ③ JOIN 作者
    return vo;
}
```

**一次详情请求 = 1 个事务内的 2 次 SELECT + 1 次 UPDATE，外加 1 次 Redis 写入。**

两个关键含义：

1. **详情页不读缓存**，每次都真打数据库。因为缓存里的浏览量是旧的（缓存 TTL 1 小时），
   `getArticleById` 上那个 `@Cacheable` 只有 `edit-data` 接口在用。
2. **这是一个写接口**。每来一个请求就要 UPDATE 同一行，因此受行锁和事务刷盘双重制约。

### 错误响应的坑

访问不存在的文章，返回的是：

```
HTTP/1.1 200
{"code":404,"message":"文章不存在","data":null}
```

全局异常处理器把错误也包成了 **HTTP 200**，错误码放在 body 的 `code` 字段。
**所以只断言「HTTP 响应码 = 200」等于没断言**，必须同时断言 `$.code == 200`。
本计划的脚本两类断言都配了。

---

## 2. 实测基线数据

### 2.1 只有 4 篇可访问文章时（真实库现状）

| 并发 | QPS | 平均 | P50 | P95 |
| --- | --- | --- | --- | --- |
| 5 | 124.8 | 40ms | 39ms | 51ms |
| 10 | 137.2 | 72ms | 56ms | 159ms |
| 20 | 141.3 | 140ms | 137ms | 189ms |
| 40 | 140.5 | 280ms | 276ms | 346ms |
| 80 | 139.7 | 553ms | 566ms | 646ms |

QPS 从并发 10 起就不涨了，之后加并发只涨延迟——典型的资源饱和。

### 2.2 全部请求打同一篇文章（id=32）

| 并发 | QPS |
| --- | --- |
| 10 | 79.1 |
| 40 | 78.1 |
| 80 | 77.5 |

**吞吐腰斩到 78**。所有请求争同一行的 UPDATE 锁，串行化执行。
这就是必须造 200 篇文章的原因：只有 4 篇文章测出来的是行锁竞争，不是系统容量。

### 2.3 造 200 篇文章后（本次压测采用的场景）

| 并发 | QPS | 平均 | P50 | P95 |
| --- | --- | --- | --- | --- |
| 5 | 138.9 | 36ms | 33ms | 52ms |
| 10 | 239.0 | 42ms | 42ms | 56ms |
| 20 | 455.1 | 44ms | 44ms | 56ms |
| 40 | 843.0 | 47ms | 47ms | 62ms |
| 80 | **984.6** | 81ms | 84ms | 114ms |
| 120 | 980.1 | 121ms | 115ms | 159ms |

**饱和点在并发 80 左右，QPS 约 980。** 再往上 QPS 不涨、P95 从 114ms 升到 159ms。

### 2.4 两个变量的影响（对照实验）

| 配置 | 并发 20 | 并发 80 |
| --- | --- | --- |
| 4 篇文章 + `flush=1` | 141 QPS | 140 QPS |
| 200 篇 + `flush=1` | 421 QPS | 975 QPS |
| 200 篇 + `flush=2` | 943 QPS | 1599 QPS |

- **文章数（行锁分散度）**：带来约 3–7 倍差异
- **`innodb_flush_log_at_trx_commit`**：带来约 2 倍差异
- 两者根因同一个：**每次请求都要同步 UPDATE**，既有行锁又要刷盘

### 2.5 一个被证伪的假设：连接池

HikariCP 默认只有 10 个连接，而并发 10 就饱和——看着很像连接池打满。
实测把池子扩到 50：

| 并发 | 池=10 | 池=50 |
| --- | --- | --- |
| 10 | 137.2 | 133.2 |
| 20 | 141.3 | 137.0 |
| 40 | 140.5 | 137.3 |
| 80 | 139.7 | 131.8 |

**没有变化，甚至略降。连接池不是瓶颈，别在这上面浪费时间。**

---

## 3. 测试方案

### 3.1 两个场景对照

| 场景 | 脚本 | 端口 | 目的 |
| --- | --- | --- | --- |
| A 直连后端 | `article-detail-direct.jmx` | 18080 | 排除 Nginx，定位后端与数据库瓶颈 |
| B 经 Nginx | `article-detail-nginx.jmx` | 80 | 与真实用户路径一致 |

两份脚本除端口外参数完全一致，结果可直接对比，差值就是 Nginx 层的开销。

### 3.2 数据准备

真实库里只有 4 篇可访问文章，行锁竞争会严重低估容量，所以先造 200 篇：

```bash
docker compose exec -T mysql mysql -uroot -p"你的密码" \
  --default-character-set=utf8mb4 blog < jmeter/prepare-data.sql
```

> **必须带 `--default-character-set=utf8mb4`**：Windows Git Bash 下 mysql 客户端默认
> 用 latin1 连接，中文会存成双重编码乱码（实测「压测文章」存成 `C3A5C5BD...`，
> 正确应为 `E58E8BE6B5...`）。
> 少了这个参数，中文列别名还会直接报 `ERROR 1064 ... near '•°'`——
> 看着像 SQL 语法错误，实为编码问题，很难排查。

造数后 ID 区间是 **900001–900200**（脚本变量 `id_min` / `id_max` 已对应）。

### 3.3 阶梯加压设计

不依赖任何插件，用**多个线程组 + TestPlan 串行执行**实现阶梯：

| 阶梯 | 并发 | 爬坡 | 持续 |
| --- | --- | --- | --- |
| S005 | 5 | 10s | 60s |
| S010 | 10 | 10s | 60s |
| S020 | 20 | 10s | 60s |
| S040 | 40 | 10s | 60s |
| S080 | 80 | 10s | 60s |
| S120 | 120 | 10s | 60s |
| S200 | 200 | 10s | 60s |

- `TestPlan.serialize_threadgroups = true`，七个组依次执行，互不干扰
- 单场景约 **8.2 分钟**，两个场景共约 17 分钟
- 取样器按阶梯命名（`S005-文章详情` … `S200-文章详情`），
  聚合报告里每个阶梯各占一行，一眼看出拐点在哪一级

阶梯为什么这样排：实测饱和点在并发 80，所以 80 以下密一些（5/10/20/40）
用来定位拐点，80 以上疏一些（120/200）用来确认是否真的到顶、以及会不会开始报错。

### 3.4 参数化

路径：`/api/articles/${__Random(${id_min},${id_max})}`

用 `__Random` 在 200 个 ID 里随机取，模拟真实分散访问。
ID 范围通过用户定义变量控制，改造数区间时只需改 `id_min` / `id_max`。

> 没用 CSV Data Set 是因为它按相对路径找文件，而 JMeter 的「当前目录」取决于
> 启动方式，容易踩坑。若需要精确控制每次请求用哪个 ID，再换成 CSV 也不迟。

### 3.5 断言配置（每个取样器 5 条）

| 断言 | 检查内容 | 为什么需要 |
| --- | --- | --- |
| 响应码 200 | HTTP 状态码 | 基础连通性 |
| JSON `$.code` = 200 | 业务成功标志 | **关键**：错误也返回 HTTP 200，只靠状态码漏判 |
| JSON `$.data.content` 存在 | 正文非空 | 防止返回空壳对象 |
| JSON `$.data.authorId` 存在 | 作者信息已关联 | 验证 JOIN 查询真的成功了 |
| 响应时间 < 3000ms | 单请求不超时 | 把极端慢请求直接标记为失败 |

### 3.6 监听器

| 监听器 | 状态 | 说明 |
| --- | --- | --- |
| 聚合报告 | 启用 | 看每个阶梯的 QPS / 平均 / P95 / 错误率 |
| 汇总报告 | 启用 | 实时看跑得对不对 |
| 查看结果树 | **禁用** | 压测时必须关掉，它会把每个请求的完整响应驻留内存 |

---

## 4. 执行方式

### 4.1 必须用非 GUI 模式

GUI 模式只用来编辑脚本和看结果，**跑压测一律用命令行**：

```bash
cd jmeter

jmeter -n -t article-detail-direct.jmx \
  -l result-direct.jtl -e -o report-direct/

jmeter -n -t article-detail-nginx.jmx \
  -l result-nginx.jtl -e -o report-nginx/
```

`-e -o` 会生成 HTML 报告，用浏览器打开 `report-direct/index.html` 即可。

### 4.2 注意事项

- **先跑低并发验证脚本正确性**：用 GUI 打开脚本，把线程数临时改成 1、
  循环 5 次，确认断言全绿再上量。别一上来就跑 8 分钟。
- **压测机和被测机分开**：都在这台电脑上时，JMeter 本身会抢 CPU。
  并发 200 时尤其明显，如果 QPS 上不去，先确认是不是 JMeter 自己到瓶颈了
  （看任务管理器里 java 进程的 CPU）。
- **每次压测前重启一下后端**，让 ES 回填、缓存等状态回到干净起点，保证可复现。
- **别在压测时开 GUI**，也别开结果树。

### 4.3 清理

```bash
# 第 1 步：清 MySQL
docker compose exec -T mysql mysql -uroot -p"你的密码" \
  --default-character-set=utf8mb4 blog < jmeter/cleanup-data.sql

# 第 2 步：清 ES（压测期间重启过后端就必须做，ArticleSyncTask 会把这 200 篇回填进索引）
curl -X POST "http://localhost:19200/article/_delete_by_query" \
  -H "Content-Type: application/json" \
  -d '{"query":{"range":{"id":{"gte":900001,"lte":900200}}}}'
curl -X POST "http://localhost:19200/article/_refresh"

# 第 3 步：清 Redis 缓存（@CachePut 会留下上百条 article::id，TTL 1 小时）
docker exec blog-redis redis-cli --scan --pattern "blog:cache:article::900*" \
  | xargs -r docker exec -i blog-redis redis-cli DEL
```

**三处都要清。** 实测踩过：只清了 MySQL，结果 ES 从 4 篇涨到 204 篇，
Redis 里残留 201 条缓存键。详情页不读这个缓存所以不影响功能，但会干扰下次压测。

---

## 5. 目标 QPS 与判读标准

### 5.1 如何找拐点

看聚合报告里每阶梯一行，找这三个信号中**最先出现**的那个：

1. **QPS 不再随并发增长**（吞吐见顶）
2. **P95 明显上翘**（比如从 60ms 跳到 150ms 以上）
3. **错误率从 0 变成非 0**

按实测数据推断，拐点应该出现在 **S080 到 S120 之间**。

### 5.2 推荐验收目标

如果要给这个接口定一个可接受标准，建议：

| 指标 | 目标值 | 依据 |
| --- | --- | --- |
| QPS | ≥ 800 | 并发 40 时实测 843，留约 20% 余量 |
| P95 | < 150ms | 并发 80 时实测 114ms，并发 120 时 159ms |
| P99 | < 300ms | 并发 80 时实测处于该量级 |
| 错误率 | 0 | 全程实测均为 0 |

**不要按 980 定目标**——那是天花板，满负荷运行没有任何应对突发流量的余量。

### 5.3 Nginx 开销判读

对比两个场景同一阶梯的 QPS 和 P95：

- 差值 < 5%：Nginx 不是瓶颈，可以放心让所有流量走它
- 差值 > 20%：Nginx 成瓶颈，检查 worker_processes、keepalive 连接数

---

## 6. 优化建议（按性价比排序）

### P0：浏览量自增异步化 —— 治本

**问题**：每次详情请求都同步执行一次 `UPDATE view_count`，
既有行锁竞争，又逼着 MySQL 每次提交都刷 redo log。

**证据**：
- 单篇文章访问 QPS 只有 78，分散到 200 篇后 980（行锁）
- `innodb_flush_log_at_trx_commit` 从 1 改 2，并发 20 下 QPS 从 421 → 943（刷盘）
- 两者叠加导致详情页这个「读」接口实际按「写」接口的成本在跑

**方案**：浏览量改用 Redis `INCR` 累积，定时任务（比如每分钟）批量回写 MySQL；
或者发一条消息给 MQ 异步处理。详情页随之变成纯读接口。

**预期收益**：去掉每请求的写事务后，可叠加 2–7 倍提升，且能彻底解决热点文章的锁竞争。

### P1：详情页改用读缓存

**问题**：`getDetail` 上只有 `@CachePut`（只写），缓存白写了却没人读。
每次请求还是要跑 2 次 SELECT。

**方案**：文章内容用 `@Cacheable` 读（缓存命中就不查库），
浏览量从 Redis 单独取，两者在返回前组装。

**注意**：改完要确保文章更新/删除时 `@CacheEvict` 生效，否则会读到旧内容。
现有代码在 update、delete、hide、unhide 上都已加了 `@CacheEvict`，改动面不大。

### P2：`innodb_flush_log_at_trx_commit = 2`

**实测收益**：QPS 翻倍（并发 20 下 421 → 943，并发 80 下 975 → 1599）。

**代价**：MySQL 崩溃时可能丢失最后 1 秒的事务。对博客这类可容忍少量数据丢失的
场景通常可接受，但要清楚这是拿持久性换吞吐。

**优先级低于 P0**：P0 做完后详情页根本不写库了，这个参数的收益自然也就消失了。

### P3：不要动连接池

实测 10 → 50 完全没变化（140 vs 137）。**在这个接口上，连接池不是瓶颈。**
除非 P0 之后瓶颈转移，否则别加这个配置——加了没用还白占连接。

### P4：其他可选项

- **静态资源交给 Nginx 缓存**：前端 JS/CSS 走长缓存（已配 `expires 1y`）
- **JVM 参数**：后端堆 512m 目前够用，压测时留意 GC 日志
- **JMeter 分布式压测**：如果 JMeter 本身 CPU 打满导致压不上去，
  再考虑多机压测，否则没必要

---

## 7. 复现命令速查

```bash
# 1. 造数
docker compose exec -T mysql mysql -uroot -p"blog_dev_pwd" \
  --default-character-set=utf8mb4 blog < jmeter/prepare-data.sql

# 2. 压测（非 GUI）
cd jmeter
jmeter -n -t article-detail-direct.jmx -l r1.jtl -e -o report-direct/
jmeter -n -t article-detail-nginx.jmx  -l r2.jtl -e -o report-nginx/

# 3. 清理（MySQL + ES + Redis 三处，见 4.3 节）
```
