# 遗留优化落实与全项目审查设计文档

日期：2026-08-26
状态：已确认

## 背景与目标

前端分支合并后，最终全分支审查裁定了一批「可带病合并」的遗留优化项。本文档覆盖两部分：

- **Part A**：把已确认的全部遗留优化落实（用户已逐项确认实现路线）。
- **Part B**：全项目代码审查发现的其余问题清单，仅记录存档，由用户逐项决策后再另行安排实施。

## Part A — 本次实施项（10 项）

### 后端（4 项）

#### A1. 草稿可见性修复

`ArticleServiceImpl.getDetail(Long id)` 增加状态校验：

- 查到文章后，若 `status != 1` 且当前登录用户不是文章作者 → 抛 `BusinessException(404, "文章不存在")`。
- 匿名访问草稿同样得到 404（不暴露草稿存在性）；作者本人看自己的草稿正常返回。
- 判定「当前登录用户」用 SecurityContextHolder 存在性判断（匿名时 principal 不是 LoginUser），不得抛 401——游客看已发布文章的路径不能受影响。

#### A2. 编辑数据接口 `GET /api/articles/{id}/edit-data`

- Controller 新增端点，需登录（SecurityConfig 现有规则天然覆盖，`/api/articles/*` 放行仅限 GET 列表/搜索/详情三个精确声明，新路径不在放行面内——注意验证 `/api/articles/{id}/edit-data` 不被现有 `GET /api/articles/*` 单段通配误放行，若有冲突需在该规则中排除或调整声明顺序）。
- Service 新增 `getEditData(Long id)`：校验文章存在（404）→ 校验作者是本人（403「无权操作他人的文章」）→ 返回 `getArticleById(id)`（复用现有 @Cacheable 纯读缓存，该方法自此有真实调用者）。
- 前端 `ArticleEdit.vue` 的 `loadForEdit` 改调新接口；`frontend/src/api/article.ts` 增加 `getArticleEditData(id)`。

#### A3. 搜索结果补作者（消除「佚名」）

- `ArticleSearchVO` 增加嵌套 `AuthorVO author` 字段。
- `ArticleController.search`：利用 ES 文档已有的 `authorId`，收集本次命中页的去重 authorId 集合，批量查 user 表组装 Map，回填每个 VO 的 author（作者已逻辑删除则为 null，前端已有「佚名」兜底）。与列表页相同的防 N+1 批量模式，无需重建 ES 索引。
- 前端 `types/api.ts` 的 `ArticleSearchVO` 增加 `author: AuthorVO | null`；列表页搜索结果即可显示真实作者（现有 `item.author?.nickname || '佚名'` 代码无需改动）。

#### A4. 安全收紧

- `CorsConfig`：移除 `.allowCredentials(true)`（本项目 token 走 Authorization 头，浏览器凭证机制无用武之地）。移除后 `allowedOriginPatterns("*")` 可保留。
- `application.yml`：
  - DB 密码：`password: ${DB_PASSWORD:123456}`
  - JWT secret：`secret: ${JWT_SECRET:<现值>}`
  - 生产环境通过环境变量覆盖，开发环境开箱即用不变。

### 前端（6 项）

#### A5. baseURL 环境变量化

`utils/request.ts`：

```ts
baseURL: import.meta.env.VITE_API_BASE ?? 'http://localhost:8080/api',
```

`env.d.ts` 补充 `ImportMetaEnv` 接口声明 `VITE_API_BASE?: string`。

#### A6. 列表页双重加载修复

`ArticleList.vue` 重构加载触发方式：

- 移除 pagination 的 `@current-change` / `@size-change` 与 radio-group 的 `@change` 回调。
- 改为 `watch([pageNum, pageSize, sort], () => load())`；keyword 变化仍走 resetAndLoad（重置页码）。
- `load()` 内部做参数快照去重：与上次成功请求的参数串相同则跳过——同一 tick 内 pageSize 变化引发的 current-change + size-change 连发、以及 watch 触发的重复调用，都收敛为一次网络请求。
- 注意保留既有行为：切换排序/关键词后从第一页开始。

#### A7. unhandled rejection 清理（5 处）

- `Login.vue` / `Register.vue` / `ArticleEdit.vue`：`await formRef.value?.validate()` 统一改为先 `const valid = await formRef.value?.validate().catch(() => false)`，`if (!valid) return`。
- `ArticleDetail.vue` `onDelete`：confirm 包 `.catch(() => {})`（取消确认即静默返回）。
- `Profile.vue` `onDeleteArticle`：同上。
- `ArticleDetail.vue` `onToggleLike` / `onToggleFavorite`：接口调用包 try/catch，失败时本地 liked/favorited/likeCount 状态不动（错误提示已由拦截器弹出）。

#### A8. 评论计数修正

`ArticleDetail.vue`：新增递归函数统计含子评论的总数，替换模板中的 `comments.length`。

```ts
function countComments(list: CommentVO[]): number {
  return list.reduce((sum, c) => sum + 1 + countComments(c.children), 0)
}
```

#### A9. 编辑页响应路由参数

`ArticleEdit.vue`：`articleId` 由 setup 一次性常量改为 `computed(() => route.params.id as string | undefined)`；`loadForEdit` 通过 `watch(articleId, ..., { immediate: true })` 触发；编辑模式切换为新建模式时清空表单。

#### A10. 并入 A7（个人中心取消收藏无确认，无需处理）

原清单中「个人中心取消收藏确认取消」实际不存在取消环节（直接执行），无需改动；A7 已涵盖全部 rejection 清理点。

## 验证

1. 后端：`./mvnw.cmd -q compile` 退出码 0。
2. 前端：`npm run build` 与 `npm run lint` 退出码 0。
3. 每个实施项独立提交，提交信息沿用 `类型(范围): 中文描述` 格式。

## Part B — 全项目审查发现清单（仅存档，待用户决策）

以下问题本次不修，用户看过清单后逐项决定。

### Bug / 正确性类

| # | 位置 | 问题 |
|---|------|------|
| B1 | init.sql user 表 `uk_username`/`uk_email` | 逻辑删除用户的用户名和邮箱被唯一索引永久占用：service 层注册校验只查 deleted=0，但 DB 唯一索引是全局的——用户被删除后同名/同邮箱永远无法重新注册。修法方向：唯一索引改 `(username, deleted)` 或引入 deleted_at 时间戳列 |
| B2 | NotificationConsumer | 通知只有日志没有落库（代码自带 TODO 标注），通知功能实际未完成。修法方向：建 notification 表 + 落库 + 查询接口 |
| B3 | CommentServiceImpl.addComment L75-76 | `article` 与父评论存在冗余重复查询（parent 在步骤 2 已查过却再次 getById），且 `article.getAuthorId()` 未判空即解引用（前置校验已保证非 null，属防御性问题+冗余查询） |

### 死代码 / 冗余类

| # | 位置 | 问题 |
|---|------|------|
| B4 | HotArticleService 全链路 | 服务 + 定时任务 + Redis 缓存齐全，但没有任何 Controller 暴露端点——孤儿代码（A2 实施后 getArticleById 不再有此问题） |
| B5 | JwtUtil.isTokenExpired / getUsernameFromToken | 无任何调用者 |
| B6 | like_record / favorite 表 deleted 列 | 两实体故意物理删除，deleted 列恒为 0，纯冗余 |

### 优化 / 打磨类

| # | 位置 | 问题 |
|---|------|------|
| B7 | main.ts 图标全量注册 | ~300 个图标组件全量注册是主 chunk >500kB 构建告警的主因之一，可按需注册或 manualChunks 拆包 |
| B8 | request.ts 并发 401 | 多请求同时 401 会叠加弹多条 warning，可加去重标志位 |
| B9 | CommentItem 无限递归缩进 | 深层回复持续缩进展示会很窄，可限制展示深度或超过 N 级拍平 |
| B10 | Profile.vue 双 Tab 同时加载 | mounted 时两个 Tab 数据同时请求，收藏 Tab 可懒加载（首次切到再请求） |
| B11 | ArticleSyncTask 5 分钟窗口边界 | 恰落在边界的更新可能漏同步一次且不会补（注释自认），可改有状态游标或放宽窗口 |
| B12 | markdown-body 样式不全 | 引用/表格/标题等 Markdown 元素样式未覆盖，长文渲染观感素 |
| B13 | SecurityConfig 401 入口点手写 JSON 字符串 | 可换 ObjectMapper 序列化保持一致性（纯打磨） |
