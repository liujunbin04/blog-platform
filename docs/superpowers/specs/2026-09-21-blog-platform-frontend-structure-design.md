# 博客平台前端结构收敛设计

## 背景

后端结构收敛完成后，前端仍有四个胖组件承担过多职责：

- `ArticleDetail.vue` 约 446 行，同时负责文章加载、互动状态、评论分页和 Markdown 渲染。
- `ArticleEdit.vue` 约 468 行，同时负责表单状态、Markdown 工具栏、预览、大纲、移动端面板和保存流程。
- `Profile.vue` 约 340 行，同时维护文章列表和收藏列表两套分页与操作。
- `AppHeader.vue` 约 316 行，同时负责导航、搜索、通知和用户菜单。

这些文件难以一次读全，修改一处容易波及其他职责；其中一部分逻辑在页面之间重复。

本设计只做前端组件拆分和重复状态收敛，不改变视觉样式、用户流程、成功响应结构或数据库结构。

## 目标

- 四个胖组件按业务职责拆成子组件，主文件降到约 150 至 250 行。
- 子组件以纯展示为主，通过 props 和 emits 通信，父组件持有状态和 API 调用。
- 通知下拉自带加载和轮询逻辑，独立管理通知状态。
- 保留 `EditorToolbar`、`EditorOutline`、`CommentItem`、`AppShell`、`ContextRail` 等已有拆分。
- 前端类型检查、Lint、构建和既有测试继续通过。

## 非目标

- 不抽分页或确认弹窗 composable。
- 不引入新的 OpenAPI 依赖或状态管理方案。
- 不改动 `api/`、`types/`、`stores/`、`router/` 和 `utils/`。
- 不新增组件测试框架或测试文件。
- 不更新设计文档中已过时的描述（留给后续批次）。

## 设计原则

1. 纯搬移：子组件模板和样式尽量原样复制，不重新设计视觉。
2. 父组件持有状态：API 调用和业务状态留在父组件，子组件只收 props、发 emits。
3. 例外仅限通知：`HeaderNotifications` 自己管理 `notifyList`、`unreadCount` 和加载逻辑。
4. 不提前抽象：只有在出现第二次的地方才考虑抽 composable，本轮保持现状。
5. 新增文件统一放在 `frontend/src/components/`，使用 PascalCase 命名，不新建子目录。

## ArticleDetail 拆分

父组件保留 `articleId`、`article`、`liked`、`favorited`、`comments`、分页状态、请求序号和全部 API 调用。

### `ArticleDetailHeader`

- props：`article: ArticleVO`、`isAuthor: boolean`、`readingMinutes: number`。
- emits：`edit`、`delete`。
- 内容：文章类别与阅读时长、标题、作者与元信息、作者操作按钮。

### `ArticleRail`

- props：`article: ArticleVO`、`liked: boolean`、`favorited: boolean`、`articleTags: string[]`。
- emits：`toggle-like`、`toggle-favorite`。
- 内容：作者块、标签块、互动块和未来扩展占位。

### `ArticleComments`

- props：`comments: CommentVO[]`、`commentTotal: number`、`commentPage: number`、`commentPageSize: number`、`submitting: boolean`、`replyTo: CommentVO | null`。
- emits：`submit`（携带内容字符串）、`change-page`、`reply`、`cancel-reply`、`delete-comment`。
- 内部持有评论输入框内容 `commentContent`。
- 内容：评论区标题、回复提示条、评论编辑框、一级评论列表和分页。
- 父组件保留移动端底部操作条，并负责聚焦评论编辑器。

## ArticleEdit 拆分

父组件保留 `form`、`rules`、`formRef`、`pageLoading`、`saving`、`loadedStatus`、`cursorPosition`、`mobilePanel`、`contentTextarea`、`previewHtml`、`outline` 和 `editorStats`。

### `EditorDocumentBar`

- props：`isEditing: boolean`、`title: string`、`characterCount: number`、`saving: boolean`、`showDraftButton: boolean`、`primaryLabel: string`。
- emits：`back`、`save-draft`、`save-publish`。
- 内容：返回按钮、编辑或草稿标记、标题、字数、保存按钮。

### `EditorMetadataFields`

- props：`modelValue: { title: string; category: string; tags: string }`。
- emits：`update:modelValue`。
- 内容：标题、分类和标签三个表单项。
- 表单项通过 `prop` 名称挂载到父组件的 `el-form` 校验上下文，不直接持有 `form`。

### `EditorPanels`

- props：`previewHtml: string`、`outline: OutlineItem[]`、`mobilePanel: 'edit' | 'preview' | 'outline' | 'info'`、`form: ArticleFormData`。
- emits：`update:mobilePanel`、`navigate`。
- 内容：平板切换导航、预览面板、移动端大纲面板和移动端信息面板。

## Profile 拆分

父组件保留 `activeTab`、两套分页状态、全部加载函数和操作函数。

### `ProfileArticleLedger`

- props：`list: ArticleVO[]`、`total: number`、`page: { pageNum: number; pageSize: number }`、`loading: boolean`。
- emits：`update:page`、`page-change`、`size-change`、`edit`、`delete`、`toggle-hide`、`open`。
- 内容：文章骨架屏、空状态、文章表格和分页。

### `ProfileFavoriteLedger`

- props：`list: FavoriteVO[]`、`total: number`、`page: { pageNum: number; pageSize: number }`、`loading: boolean`。
- emits：`update:page`、`page-change`、`size-change`、`cancel`、`open`。
- 内容：收藏骨架屏、空状态、收藏表格和分页。

分页状态通过 `v-model:page` 留在父组件；跳转文章详情通过 `open` emit 回父组件，子组件不直接使用路由。顶部 `profile-header` 和 `el-tab-pane` 保留在父组件。

## AppHeader 拆分

父组件保留 `keyword`、`isHot`、`goArticles`、`goHot`、`onSearch`、`onUserCommand`、`.app-header` 和 `.header-inner` 布局壳。

### `HeaderNav`

- props：`keyword: string`、`isHot: boolean`。
- emits：`update:keyword`、`search`、`go-articles`、`go-hot`。
- 内容：站点名、发现与热榜导航、搜索框。

### `HeaderNotifications`

- 不接收 props，不发出 emits。
- 内部持有 `notifyList`、`unreadCount`、`loadNotifications`、`onNotifyVisible`、`onMarkAllRead`、`onClearNotifications` 和登录状态监听。
- 内容：带未读徽标的通知下拉。

### `HeaderUserMenu`

- props：`isLoggedIn: boolean`、`nickname: string`。
- emits：`publish`、`profile`、`logout`。
- 内容：写文章按钮、用户下拉，以及未登录时的登录与注册按钮。

用户状态操作集中在父组件 `onUserCommand` 中，子组件只上报命令。

布局风险：`.header-inner` 上的 `gap`、`order` 和 `flex-basis` 规则跨组件生效。父组件保留布局壳，子组件只渲染内部内容；需要作用于子组件根元素的移动端规则在各子组件 scoped 样式内声明，实现时以构建产物确认移动端顶栏不塌陷。

## 文件结构

新增（11 个）：

- `frontend/src/components/ArticleDetailHeader.vue`
- `frontend/src/components/ArticleRail.vue`
- `frontend/src/components/ArticleComments.vue`
- `frontend/src/components/EditorDocumentBar.vue`
- `frontend/src/components/EditorMetadataFields.vue`
- `frontend/src/components/EditorPanels.vue`
- `frontend/src/components/ProfileArticleLedger.vue`
- `frontend/src/components/ProfileFavoriteLedger.vue`
- `frontend/src/components/HeaderNav.vue`
- `frontend/src/components/HeaderNotifications.vue`
- `frontend/src/components/HeaderUserMenu.vue`

修改（4 个）：

- `frontend/src/views/ArticleDetail.vue`
- `frontend/src/views/ArticleEdit.vue`
- `frontend/src/views/Profile.vue`
- `frontend/src/components/AppHeader.vue`

不改动：`EditorToolbar.vue`、`EditorOutline.vue`、`CommentItem.vue`、`AppShell.vue`、`ContextRail.vue` 以及所有 api、类型、store、路由和 utils。

## 测试策略

本轮不新增测试文件，验证依靠类型检查、Lint、构建和手动回归：

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
npm run build
npm test
```

每个文件拆完后先跑类型检查和两个 Linter，全部拆完再跑构建和既有测试。

手动回归四个页面：

- 文章详情：点赞、收藏、评论分页、回复、删除评论、删除文章。
- 文章编辑：工具栏、大纲跳转、预览、移动端四面板、存草稿、发布。
- 个人中心：两个标签页分页、隐藏、删除、取消收藏。
- 顶部导航：搜索、发现与热榜切换、通知下拉、用户菜单、移动端布局。

## 实施拆分

1. 拆分 `ArticleDetail.vue` 为三个子组件并验证。
2. 拆分 `ArticleEdit.vue` 为三个子组件并验证。
3. 拆分 `Profile.vue` 为两个子组件并验证。
4. 拆分 `AppHeader.vue` 为三个子组件并验证。
5. 运行完整前端验证并手动回归四个页面。

## 验收标准

- 四个胖组件各自降到约 150 至 250 行。
- 新增 11 个子组件，全部通过 props 和 emits 通信。
- 通知下拉独立管理状态。
- 页面视觉、交互流程和接口调用保持不变。
- 类型检查、Lint、构建和既有测试全部通过。
- 四个页面的手动回归全部通过。
