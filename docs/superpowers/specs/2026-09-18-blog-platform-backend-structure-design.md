# 博客平台后端结构收敛设计

## 背景

稳定性修复完成后，核心行为已经受测试覆盖，但后端仍存在重复规则和不清晰边界：

- 文章公开性判断分散在文章、评论、搜索和同步任务中。
- 作者批量查询和 `AuthorVO` 拼装重复出现在多个服务。
- Elasticsearch 写入、删除和对账分散在文章服务和定时任务。
- `ArticleController.search` 直接编排 ES、可见性过滤和作者回填。
- 旧评论整树接口与新分页接口并存。

本设计只收敛后端结构，不改变前端行为、成功响应结构或数据库结构。

## 目标

- 文章可见性规则只有一个实现。
- 作者信息批量查询和转换只有一个实现。
- Elasticsearch 操作只有一个服务入口。
- 搜索编排从 Controller 移到服务层。
- 评论只保留分页读取路径。
- 所有现有成功接口继续返回相同结果。
- 后端完整测试和前端测试继续通过。

## 非目标

- 不拆分前端胖组件。
- 不修改数据库表结构或索引。
- 不改变成功响应 JSON。
- 不修改 JWT、缓存、热门榜或通知协议。
- 不引入 MapStruct、CQRS 框架或新的基础设施。
- 不重写文章事务模型。

## 文章可见性统一

新增 `ArticleVisibilityPolicy`，作为无数据库依赖的纯规则组件。

职责：

- `isDraft(Article article)`
- `isHidden(Article article)`
- `isPublic(Article article)`
- `isVisibleTo(Article article, Long userId)`

规则：

- `status == 1` 且 `hidden == 0` 为公开。
- 草稿或隐藏文章只对作者本人可见。
- `null` 状态按非公开处理。
- `null` hidden 按未隐藏处理。

替换点：

- `ArticleServiceImpl.isPublic`
- `CommentServiceImpl.requireVisibleArticle`
- `ArticleSyncTask.isPublic`
- `ArticleSearchVisibilityServiceImpl.isPublic`

`ArticleService.requirePublicArticle` 保留当前契约，内部使用策略。

## 作者信息统一

新增 `AuthorAssembler`，集中处理作者查询和转换。

职责：

- `Map<Long, User> loadUsers(Collection<Long> userIds)`
- `AuthorVO toAuthor(User user)`
- `Map<Long, AuthorVO> loadAuthors(Collection<Long> userIds)`

约束：

- 空集合直接返回空 Map，不调用 `listByIds`。
- 自动去重和过滤 `null`。
- 不改变缺失用户时返回 `null` 作者的行为。

替换点：

- 文章列表
- 热门列表
- 搜索作者回填
- 收藏列表
- 评论作者和被回复人
- 通知发件人
- ES 文档作者昵称

## Elasticsearch 同步统一

新增 `ArticleSearchSyncService`，作为 Elasticsearch 操作的唯一入口。

职责：

- `sync(Article article)`
- `remove(Long articleId)`
- `syncAll(List<Article> articles)`
- `reconcileVisibility()`

行为：

- 构建文档时统一补充作者昵称。
- 单篇和批量同步失败时记录错误，不抛出到业务事务。
- 删除失败时记录错误。
- 对账时删除不存在、草稿、隐藏或已删除的 ES 文档。
- 对账和批量写入使用同一文档构建逻辑。

`ArticleServiceImpl` 只依赖 `ArticleSearchSyncService`，不再直接依赖 `ArticleSearchRepository`。

`ArticleSyncTask` 只负责：

- 应用启动后触发对账和全量同步。
- 每五分钟查询更新窗口并调用批量同步。

## 搜索编排

新增 `ArticleSearchQueryService`。

职责：

- 接收 `ArticleSearchDTO`。
- 调用 Elasticsearch 查询。
- 应用 MySQL 可见性过滤。
- 批量回填作者。
- 返回 `PageResult<ArticleSearchVO>`。

`ArticleController.search` 只负责参数校验和 `Result` 包装。

## 评论读取路径收敛

删除旧评论整树路径：

- `CommentService.getCommentTree`
- `CommentServiceImpl.getCommentTree`
- `CommentController` 的 `GET /api/comments`
- 前端 `getCommentTree`

保留：

- `POST /api/comments`
- `DELETE /api/comments/{id}`
- `GET /api/comments/page`

评论树构建、子回复挂载和被回复人昵称填充继续由分页路径使用。

## 文件结构

新增：

- `blog/src/main/java/com/example/blog/service/ArticleVisibilityPolicy.java`
- `blog/src/main/java/com/example/blog/service/AuthorAssembler.java`
- `blog/src/main/java/com/example/blog/service/ArticleSearchSyncService.java`
- `blog/src/main/java/com/example/blog/service/impl/ArticleSearchSyncServiceImpl.java`
- `blog/src/main/java/com/example/blog/service/ArticleSearchQueryService.java`
- `blog/src/main/java/com/example/blog/service/impl/ArticleSearchQueryServiceImpl.java`

修改：

- 文章、评论、热门、收藏、通知、搜索可见性和同步任务的实现。
- `ArticleController` 和 `CommentController`。
- `ArticleSearchVisibilityService`，改为复用可见性策略。
- 前端评论 API，删除旧树请求。

## 测试策略

新增或调整：

- `ArticleVisibilityPolicyTest`
- `AuthorAssemblerTest`
- `ArticleSearchSyncServiceImplTest`
- `ArticleSearchQueryServiceImplTest`
- 更新 `ArticleServiceImplTest`、`CommentServiceImplTest`、`HotArticleServiceImplTest`

回归：

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q test

cd ..\frontend
npm test
npm run type-check
npx eslint .
npx oxlint .
```

## 实施拆分

1. 引入 `ArticleVisibilityPolicy` 并替换调用点。
2. 引入 `AuthorAssembler` 并替换调用点。
3. 引入 `ArticleSearchSyncService` 并收口 ES 操作。
4. 引入 `ArticleSearchQueryService` 并精简 Controller。
5. 删除旧评论整树路径。
6. 运行完整回归并更新批次计划。

## 验收标准

- 文章公开性判断不再存在重复实现。
- 作者批量回填不再由各服务重复实现。
- ES 写入、删除和对账只通过一个服务入口。
- 搜索 Controller 不再直接处理 ES 与作者回填。
- 旧评论整树接口和前端调用被删除。
- 评论分页、文章列表、热门、搜索、收藏和通知行为不变。
- 后端和前端验证全部通过。
