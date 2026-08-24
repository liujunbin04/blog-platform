# 博客平台前端设计文档

日期：2026-08-24
状态：已确认

## 背景与目标

为现有 Spring Boot 博客后端（`blog/`）配套 Vue 3 前端（`frontend/`）。前端脚手架（Vite + Vue 3 + TS + Pinia + Vue Router）已存在，页面、路由、请求层均为空白。

技术栈：Vue 3 + Element Plus + axios + Pinia + Vue Router + markdown-it。

## 已确认的关键决策

| 决策点 | 结论 |
|--------|------|
| 「我的文章」数据来源 | 后端新增 `GET /api/articles/my` 分页接口 |
| 点赞/收藏按钮初始状态 | 后端各新增一个状态查询接口 |
| Markdown 方案 | markdown-it 渲染 + textarea/预览分栏编辑器（国内网络友好） |
| 权限策略 | 游客可浏览文章列表/详情/评论，操作时引导登录；后端 SecurityConfig 相应放行 GET 接口 |
| 代码组织 | 分层结构：api / utils / stores / components / views |

## 一、后端补充（3 处，风格与现有代码一致）

### 1. `GET /api/articles/my`

- `ArticleService` 新增 `pageMyArticles(ArticlePageDTO)`，按 `SecurityUtil.getCurrentUserId()` 过滤作者，创建时间倒序，忽略 sort 参数。
- 复用 `ArticleVO` + 批量查作者的组装方式（与 `pageArticles` 相同的防 N+1 模式）。
- 路由匹配：`/my` 为精确路径，优先于 `/{id}`，无冲突。

### 2. 点赞/收藏状态查询

- `GET /api/likes/{articleId}/status` → `Result<Boolean>`：当前用户是否已点赞。
- `GET /api/favorites/{articleId}/status` → `Result<Boolean>`：当前用户是否已收藏。
- Service 各加一个按 `(userId, articleId)` 的 exists 查询。两个接口均需登录。

### 3. SecurityConfig 放行游客浏览

- 匿名放行：`GET /api/articles`（列表）、`GET /api/articles/search`、`GET /api/articles/{id}`、`GET /api/comments`。
- `/api/articles/my` 必须声明为需认证且放在放行规则之前（Spring Security 首条匹配生效）。
- 补充 `AuthenticationEntryPoint`：未登录访问受保护接口时返回 401 JSON（`{"code":401,"message":"未登录或登录已过期"}`），替代默认的空 body 403，前端据此可靠识别登录过期。

写操作（发布/评论/点赞/收藏）仍全部需要认证。

## 二、前端结构

```
frontend/src/
├── api/
│   ├── auth.ts        # login / register
│   ├── article.ts     # 分页列表 / 详情 / 搜索 / 发布 / 更新 / 删除 / 我的文章
│   ├── comment.ts     # 评论树 / 发表评论
│   ├── like.ts        # 点赞 / 取消 / 状态查询
│   └── favorite.ts    # 收藏 / 取消 / 状态查询 / 我的收藏分页
├── types/api.ts       # Result<T>、PageResult<T>、LoginVO、UserVO、AuthorVO、
│                      # ArticleVO、FavoriteVO、CommentVO 等 TS 类型（对齐后端 VO）
├── utils/request.ts   # axios 实例与拦截器（见下）
├── stores/user.ts     # 用户状态（见下）
├── components/
│   └── AppHeader.vue  # 顶部导航：logo、搜索入口、发布按钮、登录入口/用户下拉（退出登录）
└── views/
    ├── Login.vue
    ├── Register.vue
    ├── ArticleList.vue
    ├── ArticleDetail.vue
    ├── ArticleEdit.vue
    └── Profile.vue
```

App.vue 改造为「AppHeader + `<router-view>`」布局。

### axios 实例（utils/request.ts)

- `baseURL: 'http://localhost:8080/api'`，`timeout: 10000`。
- 请求拦截器：从 user store 读 token，非空则注入 `Authorization: Bearer <token>`。
- 响应拦截器：后端统一返回 `{code, message, data}`：
  - `code === 200` → 直接返回 `data`（业务层拿到的就是纯数据）；
  - `code === 401` 或 HTTP 401 → 清空用户状态，跳转 `/login?redirect=当前路径`，并 ElMessage 提示；
  - 其他 → ElMessage.error(message)，并 reject（业务侧可自行捕获做特殊处理，如点赞的"请勿重复点赞"）。

### Pinia 用户状态（stores/user.ts）

- state：`token: string`、`user: UserVO | null`。
- localStorage 持久化（key：`blog_token` / `blog_user`），初始化时读回，刷新不丢登录态。
- actions：`setLogin(loginVO)`、`logout()`（清 state + 清 localStorage）。
- getter：`isLoggedIn`。

## 三、路由与权限

| 路由 | 页面 | 权限 | 说明 |
|------|------|------|------|
| `/` | 重定向到 `/articles` | 公开 | — |
| `/articles` | ArticleList | 公开 | 默认落地页 |
| `/articles/:id` | ArticleDetail | 公开 | — |
| `/publish`、`/publish/:id` | ArticleEdit | 登录 | 同一页面复用编辑模式 |
| `/profile` | Profile | 登录 | — |
| `/login` | Login | 公开 | 已登录访问则跳 `/articles` |
| `/register` | Register | 公开 | 同上 |

全局前置守卫：

- `meta.requiresAuth` 且未登录 → `/login?redirect=<目标 fullPath>`；
- 登录页/注册页在已登录状态下访问 → 跳 `/articles`。

## 四、页面功能明细

### Login.vue

- 用户名 + 密码表单，Element Plus 校验（必填）。
- 成功：写入 store → 跳 redirect 参数指定的页面（无则 `/articles`）。
- 底部链接到注册页。

### Register.vue

- 字段：用户名(3-20)、密码(6-20)、确认密码(与密码一致)、昵称(≤50，选填)、邮箱(格式校验，选填)——对齐后端 `RegisterDTO`。
- 成功：ElMessage 提示并带用户名跳登录页。

### ArticleList.vue

- 查询参数：`pageNum/pageSize/sort(time|hot)/keyword`。
- 关键词搜索走 `GET /articles/search`（ES 高亮标题+摘要片段），清空关键词回到普通分页接口。
- 排序切换：最新 / 最热（按浏览量）。
- 列表项卡片：标题、作者昵称、分类/标签、发布时间、浏览量、点赞数；点击进详情。
- 底部 `el-pagination`（total、pageSize 切换）。

### ArticleDetail.vue

- markdown-it 渲染正文（保持默认 `html:false`，HTML 输入被转义，防 XSS）。
- 操作栏：点赞（含数量）、收藏，未登录点击跳登录；已登录进入页面时调状态查询接口回显激活态。
- 作者本人可见「编辑」（跳 `/publish/{id}`）与「删除」（confirm 后 DELETE，回列表）。
- 评论区：发表框（≤1000 字）、递归评论树（`children` 无限层级，支持回复某条——回复即带上该评论 id 作为 parentId）。

### ArticleEdit.vue

- 表单：标题(≤200 必填)、分类(≤50 选填)、标签(逗号分隔字符串，≤255 选填)、内容(必填)。
- 编辑器：左侧 textarea、右侧 markdown-it 实时预览的分栏布局。
- 路由带 `id` 时为编辑模式：加载详情回填（仅本人有权，后端校验），提交走 PUT；否则 POST。
- 成功后跳文章详情页。

### Profile.vue

- Tab 1「我的文章」：`GET /articles/my` 分页列表，每项可编辑/删除。
- Tab 2「我的收藏」：`GET /favorites` 分页列表（FavoriteVO：标题、作者、收藏时间等），点击跳详情，支持取消收藏。

### AppHeader.vue

- 左：站名 logo（点击回列表）。
- 中：搜索框（回车跳列表页并带关键词）。
- 右：未登录显示登录/注册；已登录显示发布按钮 + 用户下拉菜单（个人中心、退出登录）。

## 五、错误处理约定

- 业务错误（400/403/500）：响应拦截器统一 ElMessage 展示 message。
- 401：拦截器统一处理登出+跳转，页面无需各自处理。
- 表单校验错误：前端 Element Plus 规则先行，后端 400 信息兜底展示。

## 六、新增依赖

dependencies：`element-plus`、`@element-plus/icons-vue`、`axios`、`markdown-it`
devDependencies：`@types/markdown-it`

安装若遇国内网络问题，使用 npmmirror 镜像。

## 七、验证

1. `npm run build` 通过（含 vue-tsc 类型检查）。
2. 启动后端（需 MySQL/Redis），手动冒烟：注册→登录→发文章→列表→详情→点赞→收藏→评论→个人中心两个 Tab。
