# Part B 全量处理设计文档（B1-B13）

日期：2026-08-26
状态：已确认
前置：`docs/superpowers/specs/2026-08-26-cleanup-and-audit-design.md` Part B 清单

## 已确认的决策汇总

| 项 | 决策 |
|----|------|
| B2 通知 | 完整实现：落库 + 查询接口 + 前端入口 |
| B4 热门文章 | 补端点 + 前端热门位 |
| B1 索引 | 唯一索引改 `(username, deleted)` / `(email, deleted)`，提供手动迁移 SQL |
| B5/B6 | 删除死方法；init.sql 去掉 like_record/favorite 的 deleted 列（含实体同步），提供手动迁移 SQL |
| B7 打包 | vite manualChunks 拆包 |
| B9/B10/B11/B12 | 全部实施 |

## 一、数据库变更（手动执行，附迁移文件）

新增 `blog/src/main/resources/sql/migration-2026-08-26.sql`：

```sql
-- =============================================================
-- 迁移脚本：B1 用户唯一索引改造 + B6 关系表冗余列删除 + B2 通知表
-- 执行方式：mysql -u root -p blog < migration-2026-08-26.sql
-- =============================================================
USE `blog`;

-- B1: 用户唯一索引改为含 deleted 的组合索引。
-- MP 逻辑删除值固定为 1：同一用户名最多一条活跃记录(deleted=0)，
-- 但多次注册删除会撞历史行(deleted=1)——教程场景可接受；
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
  `type`         VARCHAR(20)     NOT NULL COMMENT '类型：comment/like',
  `article_id`   BIGINT UNSIGNED NOT NULL COMMENT '关联文章 id',
  `content`      VARCHAR(500)    DEFAULT NULL COMMENT '通知内容摘要',
  `is_read`      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读 1 已读',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_recipient` (`recipient_id`, `is_read`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '站内通知表';
```

同时 `init.sql` 保持与最终 schema 一致（user 表索引、两关系表去列、追加 notification 表）。

## 二、后端改动

### B1 — user 表索引配套说明
`UserServiceImpl.register` 注释更新：DB 层唯一索引已兼容软删用户重名（校验逻辑不变）。

### B6 — 实体同步
`LikeRecord.java`/`Favorite.java` 若含 deleted 字段则删除（当前实体本就无此字段，只更新类头注释里关于物理删除原因的表述）；无实际代码改动风险。

### B2 — 通知功能完整实现

**新增文件**：
- `entity/Notification.java`：字段与表一致（@TableName notification；无逻辑删除）。
- `mapper/NotificationMapper.java`：BaseMapper 标准模板。
- `service/NotificationService.java` + `impl/NotificationServiceImpl.java`：
  - `void create(Notification notification)`——幂等创建：插入前查「同 sender+article+type 且 create_time ≥ now()-10秒」存在则跳过（防 MQ at-least-once 重复投递）。
  - `PageResult<NotificationVO> pageMy(NotificationPageDTO dto)`——按收件人过滤，create_time 倒序，批量回查 sender 昵称（防 N+1 模式）。
  - `long countUnread(Long userId)`。
  - `void markAllRead(Long userId)`。
- `vo/NotificationVO.java`：id、senderNickname、type、articleId、content、isRead、createTime。
- `dto/NotificationPageDTO.java`：pageNum/pageSize 校验注解同 FavoritePageDTO 模式。
- `controller/NotificationController.java`（`/api/notifications`，全部需登录）：
  - `GET ?pageNum=&pageSize=` → `Result<PageResult<NotificationVO>>`，响应体附带未读数——直接复用 PageResult 不合适，返回 `Result<Map<String,Object>>` 过于松散；采用 VO 包装：`NotificationPageVO { total/pageNum/pageSize/list/unreadCount }`。实现上 NotificationService 提供 `pageMyWithUnread(dto)` 返回该包装。
  - `PUT /read-all` → `Result<Void>`。

**修改 `consumer/NotificationConsumer.java`**：两个 onComment/onLike 的 TODO 日志替换为 `notificationService.create(...)` 落库（消费失败仍 basicNack requeue）。DTO 字段 recipientId/senderId/type/articleId/content 与表列一一对应，直接转换。

### B4 — 热门文章端点

`ArticleController` 新增：

```java
@GetMapping("/hot")
public Result<List<ArticleVO>> hot(@RequestParam(defaultValue = "10") @Min(1) @Max(50) int topN)
```

内部调 `hotArticleService.getHotArticles(topN)`；Controller 注入 HotArticleService。
SecurityConfig：现有 `GET /api/articles/*` 单段通配已覆盖 `/api/articles/hot`——游客可看热门位，无需改配置（在 plan 中验证一次行为即可，不改代码）。

### B11 — 同步窗口放宽
`ArticleSyncTask.syncRecentArticles` 的 `.minusMinutes(5)` 改 `.minusMinutes(6)`，注释中窗口边界说明同步改为 6 分钟口径。

### B3 — 冗余查询清理
`CommentServiceImpl.addComment` 步骤 4 中 `getById(parentId).getUserId()` 复用步骤 2 已查询的 `parent` 引用（把 parent 提升到方法作用域），删掉重复查询。

### B13 — ObjectMapper 序列化 401 body
SecurityConfig 构造器注入 `ObjectMapper`（Spring Boot 自动配置提供），entry point 内 `objectMapper.writeValueAsString(Result.error(401, "未登录或登录已过期"))` 写出；异常兜底仍写原硬编码串。

### B5 — 删除死方法
`JwtUtil.isTokenExpired` 与 `getUsernameFromToken` 无调用者，整体删除。

## 三、前端改动

### B7 — manualChunks 拆包
`vite.config.ts` build.rolldownOptions.output.manualChunks 函数：node_modules 下 element-plus、@element-plus/icons-vue、markdown-it 各自成 chunk；消除 >500kB 告警（不保证阈值内的其他组合，以构建结果为准）。

### B8 — 401 提示去重
`utils/request.ts`：模块级 `let redirectingToLogin = false`；handleUnauthorized 进入时若已在跳转中直接 return，跳转完成（router.push resolve 或路由变化后）复位标志。

### B9 — 评论深度限制
`components/CommentItem.vue` 增加 `depth?: number` prop（默认 0），`.children` 的缩进 margin 仅在 `depth < 3` 时应用；递归传递 `depth + 1`。

### B10 — 收藏 Tab 懒加载
`Profile.vue`：el-tabs 加 `@tab-change`，`activeTab === 'favorites'` 且未加载过时才 `loadFavorites()`；mounted 只加载我的文章。加 `favLoadedOnce` 标志防止重复请求；取消收藏后刷新沿用现有 loadFavorites。

### B12 — Markdown 样式补全
新建 `src/assets/markdown.css`，定义 .markdown-body 的 h1-h4/blockquote/table（含斑马纹）/hr/ul/ol/li/a/img/code/pre 完整样式，使用 Element Plus CSS 变量保持主题一致；main.ts 引入；移除 ArticleDetail.vue 与 ArticleEdit.vue 中被取代的局部 :deep 样式（img/pre/code 三条保留全局化）。

## 四、验证

1. 后端 `./mvnw.cmd -q compile`、前端 `npm run build && npm run lint` 全绿。
2. 前端构建输出确认主 chunk 告警消失（chunkSizeWarningLimit 不调）。
3. 两份 SQL（init.sql 更新 + migration 文件新增）随代码提交；实际 DB 变更由用户手动执行迁移脚本。
