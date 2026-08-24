# 博客平台前端实施计划（含三处后端补充）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为现有 Spring Boot 博客后端配套完整的 Vue 3 前端（6 个页面），并补齐 3 处后端缺口（我的文章接口、点赞/收藏状态查询、游客可浏览放行）。

**Architecture:** 前端采用分层结构：`api/` 按后端模块拆分请求函数，`utils/request.ts` 统一 axios 实例与拦截器（注入 token、解包 `Result<T>`、401 处理），`stores/user.ts` 管理登录态并持久化到 localStorage，`views/` 六个页面 + `components/` 复用组件。后端只做增量修改，风格与现有代码一致。

**Tech Stack:** Vue 3 + TypeScript + Element Plus + axios + Pinia(v4) + Vue Router(v5) + markdown-it；后端 Spring Boot 3.5 + MyBatis-Plus + Spring Security(JWT)。

**设计文档:** `docs/superpowers/specs/2026-08-24-blog-frontend-design.md`（本计划的需求来源，冲突时以设计文档为准）

## Global Constraints

- axios `baseURL` 固定为 `http://localhost:8080/api`，timeout 10000ms。
- localStorage key 固定：`blog_token`、`blog_user`。
- 后端统一返回 `{code, message, data}`，code=200 成功；HTTP 层 401 由新增的 AuthenticationEntryPoint 返回同样结构的 JSON。
- **测试策略（经用户确认的设计 §七）**：本项目前后端均无自动化测试设施（后端 `src/test` 为空，集成测试需 MySQL+Redis+RabbitMQ+ES 全套中间件）。每个任务的验证方式为：后端任务跑 `./mvnw.cmd compile`，前端任务跑 `npm run build`（含 vue-tsc 类型检查）。最终人工冒烟清单见 Task 12。执行者不得擅自引入测试框架。
- 所有命令在 Windows git-bash 下执行；路径用正斜杠。
- 代码风格与现有代码一致：中文注释、解释「为什么」；前端组件用 `<script setup lang="ts">` + scoped 样式。
- 前端 tsconfig 开启了 `noUncheckedIndexedAccess`，数组/对象下标访问返回值可能是 `undefined`，注意判空。
- 新增依赖仅限：`element-plus`、`@element-plus/icons-vue`、`axios`、`markdown-it`（运行时）；`@types/markdown-it`（开发）。国内网络慢时加 `--registry=https://registry.npmmirror.com`。
- 提交信息格式沿用仓库现状：`类型(范围): 中文描述`，如 `feat(frontend): 登录注册页`。frontend/ 与 blog/ 目前整体未被 git 跟踪，首个涉及它们的提交会把相关文件一并纳入版本控制，属预期行为。

---

### Task 1: 后端「我的文章」分页接口

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/ArticleService.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/controller/ArticleController.java`

**Interfaces:**
- Consumes: 现有 `ArticleService extends IService<Article>`、`SecurityUtil.getCurrentUserId()`、`PageResult.of(...)`、`ArticleVO.from(article, user)`。
- Produces: `GET /api/articles/my?pageNum=&pageSize=` 返回 `Result<PageResult<ArticleVO>>`（需登录）。Task 6 的 `getMyArticles()` 依赖它。

- [ ] **Step 1: ArticleService 接口添加方法**

在 `ArticleService.java` 的 `pageArticles` 方法声明之后追加：

```java
    /**
     * 分页查询当前登录用户的文章（个人中心「我的文章」，按创建时间倒序）
     */
    PageResult<ArticleVO> pageMyArticles(ArticlePageDTO dto);
```

- [ ] **Step 2: ArticleServiceImpl 实现**

在 `ArticleServiceImpl.java` 的 `pageArticles` 方法之后追加：

```java
    @Override
    public PageResult<ArticleVO> pageMyArticles(ArticlePageDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 我的文章固定按创建时间倒序，忽略列表页的 time/hot 排序参数
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getAuthorId, userId)
                .orderByDesc(Article::getCreateTime);
        Page<Article> page = page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
        // 作者就是当前用户本人，一次查询即可完成组装，无需像公共列表那样批量查
        User me = userService.getById(userId);
        List<ArticleVO> list = page.getRecords().stream()
                .map(a -> ArticleVO.from(a, me))
                .toList();
        return PageResult.of(list, page.getTotal(), page.getCurrent(), page.getSize());
    }
```

- [ ] **Step 3: ArticleController 添加端点**

在 `ArticleController.java` 中 `detail` 方法之前插入（放在 `/{id}` 之前纯属可读性；Spring MVC 精确路径优先于路径模板，`/my` 不会被 `/{id}` 吞掉）：

```java
    /**
     * 当前用户的文章分页（个人中心「我的文章」，需登录）
     */
    @GetMapping("/my")
    public Result<PageResult<ArticleVO>> my(@Valid ArticlePageDTO dto) {
        return Result.success(articleService.pageMyArticles(dto));
    }
```

- [ ] **Step 4: 编译验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0，BUILD SUCCESS（`-q` 下无 ERROR 输出即成功）

- [ ] **Step 5: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/service/ArticleService.java blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java blog/src/main/java/com/example/blog/controller/ArticleController.java
git commit -m "feat(blog): 新增当前用户文章分页接口 GET /api/articles/my"
```

---

### Task 2: 后端点赞/收藏状态查询接口

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/LikeRecordService.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/LikeRecordServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/controller/LikeRecordController.java`（需补 `GetMapping` import）
- Modify: `blog/src/main/java/com/example/blog/service/FavoriteService.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/FavoriteServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/controller/FavoriteController.java`（已有 `GetMapping` import）

**Interfaces:**
- Consumes: 两个 ServiceImpl 已继承 `ServiceImpl<Mapper, Entity>`，可直接用 `count(LambdaQueryWrapper)`；`SecurityUtil.getCurrentUserId()`。
- Produces: `GET /api/likes/{articleId}/status`、`GET /api/favorites/{articleId}/status`，均返回 `Result<Boolean>`（需登录）。Task 5 的 `getLikeStatus()/getFavoriteStatus()`、Task 9 的详情页依赖它们。

- [ ] **Step 1: LikeRecordService 接口添加方法**

```java
    /**
     * 当前用户是否已点赞某文章
     */
    boolean hasLiked(Long articleId);
```

- [ ] **Step 2: LikeRecordServiceImpl 实现**

在 `unlike` 方法之后追加：

```java
    @Override
    @Transactional(readOnly = true)
    public boolean hasLiked(Long articleId) {
        return count(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, SecurityUtil.getCurrentUserId())
                .eq(LikeRecord::getArticleId, articleId)) > 0;
    }
```

- [ ] **Step 3: LikeRecordController 添加端点**

类上补 import：`org.springframework.web.bind.annotation.GetMapping`，然后添加：

```java
    /**
     * 查询当前用户是否已点赞：GET /api/likes/{articleId}/status
     */
    @GetMapping("/{articleId}/status")
    public Result<Boolean> status(@PathVariable Long articleId) {
        return Result.success(likeRecordService.hasLiked(articleId));
    }
```

- [ ] **Step 4: FavoriteService 接口添加方法**

```java
    /**
     * 当前用户是否已收藏某文章
     */
    boolean hasFavorited(Long articleId);
```

- [ ] **Step 5: FavoriteServiceImpl 实现**

在 `removeFavorite` 之后、`pageFavorites` 之前追加：

```java
    @Override
    @Transactional(readOnly = true)
    public boolean hasFavorited(Long articleId) {
        return count(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, SecurityUtil.getCurrentUserId())
                .eq(Favorite::getArticleId, articleId)) > 0;
    }
```

- [ ] **Step 6: FavoriteController 添加端点**

```java
    /**
     * 查询当前用户是否已收藏：GET /api/favorites/{articleId}/status
     */
    @GetMapping("/{articleId}/status")
    public Result<Boolean> status(@PathVariable Long articleId) {
        return Result.success(favoriteService.hasFavorited(articleId));
    }
```

- [ ] **Step 7: 编译验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0

- [ ] **Step 8: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/service/LikeRecordService.java blog/src/main/java/com/example/blog/service/impl/LikeRecordServiceImpl.java blog/src/main/java/com/example/blog/controller/LikeRecordController.java blog/src/main/java/com/example/blog/service/FavoriteService.java blog/src/main/java/com/example/blog/service/impl/FavoriteServiceImpl.java blog/src/main/java/com/example/blog/controller/FavoriteController.java
git commit -m "feat(blog): 新增点赞/收藏状态查询接口"
```

---

### Task 3: SecurityConfig 游客放行 + 401 JSON 入口点

**Files:**
- Modify: `blog/src/main/java/com/example/blog/config/SecurityConfig.java:33-37`（authorizeHttpRequests 部分）及 import 区

**Interfaces:**
- Consumes: 现有 `SecurityFilterChain` 配置结构。
- Produces: 匿名可访问 `GET /api/articles`、`GET /api/articles/search`、`GET /api/articles/{id}`、`GET /api/comments`；未认证访问其他接口返回 HTTP 401 + `{"code":401,"message":"未登录或登录已过期","data":null}`。前端 Task 5 拦截器据此处理登录过期。

- [ ] **Step 1: 修改 authorizeHttpRequests 并添加 exceptionHandling**

import 区新增：

```java
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
```

把 `securityFilterChain` 方法中 `.authorizeHttpRequests(auth -> auth ... )` 一段替换为（保留方法其余部分不动）：

```java
                .authorizeHttpRequests(auth -> auth
                        // 注册、登录接口匿名可访问
                        .requestMatchers("/api/auth/**").permitAll()
                        // 「我的文章」需要登录：必须声明在下面的通配放行之前，
                        // Spring Security 按声明顺序首次匹配生效
                        .requestMatchers(HttpMethod.GET, "/api/articles/my").authenticated()
                        // 游客可浏览：文章列表 / 搜索 / 详情、评论树（仅 GET）
                        .requestMatchers(HttpMethod.GET, "/api/articles",
                                "/api/articles/search", "/api/articles/*", "/api/comments").permitAll()
                        // 其余一律需要认证：发文章/评论/点赞/收藏等写操作、个人中心等
                        .anyRequest().authenticated())
                // 未认证时返回 401 JSON：默认行为是空 body 的 403，
                // 前端无法据此区分「未登录」和「无权限」
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
                }))
```

注意：`/api/articles/*` 只匹配单段路径，能覆盖 `/articles/123` 详情；`/my` 因上一条规则先匹配而保持需认证。

- [ ] **Step 2: 编译验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0

- [ ] **Step 3: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/config/SecurityConfig.java
git commit -m "feat(blog): 放行游客浏览接口并以 401 JSON 返回未认证"
```

---

### Task 4: 前端依赖安装与 Element Plus 接入

**Files:**
- Modify: `frontend/package.json`（由 npm install 写入）
- Modify: `frontend/src/main.ts`
- Delete: `frontend/src/stores/counter.ts`（脚手架示例文件，无人引用）

**Interfaces:**
- Produces: 全局可用的 Element Plus 组件、中文 locale、全量图标全局注册（模板里直接写 `<search />`、`<arrow-down />` 等 kebab 名）。

- [ ] **Step 1: 安装运行时依赖**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm install element-plus @element-plus/icons-vue axios markdown-it`
Expected: 退出码 0；若因网络超时失败，改用 `npm install element-plus @element-plus/icons-vue axios markdown-it --registry=https://registry.npmmirror.com`

- [ ] **Step 2: 安装开发依赖**

Run: `npm install -D @types/markdown-it`
Expected: 退出码 0（同上可加镜像参数）

- [ ] **Step 3: 重写 main.ts 接入 Element Plus**

`frontend/src/main.ts` 整体替换为：

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

// 图标全局注册为 kebab-case 组件，模板中可直接写 <search /> 等
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
```

- [ ] **Step 4: 删除脚手架示例 store**

Run: `rm c:/Users/vannnn/Desktop/blog-platform/frontend/src/stores/counter.ts`
Expected: 文件删除（该文件从未被提交过 git，无需 git rm）

- [ ] **Step 5: 构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0（vue-tsc 类型检查 + vite build 均通过；此时路由仍为空、App.vue 还是脚手架内容，属预期）

- [ ] **Step 6: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/package.json frontend/package-lock.json frontend/src/main.ts
git commit -m "feat(frontend): 引入 Element Plus/axios/markdown-it 并全局接入"
```

---

### Task 5: 前端基础设施——类型、工具、用户状态、axios 封装、API 模块

**Files:**
- Create: `frontend/src/types/api.ts`
- Create: `frontend/src/utils/format.ts`
- Create: `frontend/src/utils/request.ts`
- Create: `frontend/src/stores/user.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/api/article.ts`
- Create: `frontend/src/api/comment.ts`
- Create: `frontend/src/api/like.ts`
- Create: `frontend/src/api/favorite.ts`

**Interfaces:**
- Consumes: Task 4 的 element-plus/axios 依赖；后端各 VO 字段（见下方类型定义，与后端 Java VO 一一对应）。
- Produces（后续所有页面任务依赖这些确切签名）:
  - 类型：`PageResult<T>{total,pageNum,pageSize,list}`、`UserVO{id,username,nickname,email,avatar,createTime}`、`LoginVO{token,user}`、`AuthorVO{id,nickname,avatar}`、`ArticleVO{id,title,content,category,tags,viewCount,likeCount,status,author,createTime,updateTime}`、`ArticleSearchVO{id,title,contentSnippet,category,tags,viewCount,likeCount,createTime}`、`FavoriteVO{favoriteId,articleId,title,category,tags,viewCount,likeCount,author,articleCreateTime,favoriteTime}`、`CommentVO{id,articleId,parentId,content,author,createTime,children}`
  - `useUserStore()` → `{token, user, isLoggedIn, setLogin(loginVO), clearLogin()}`
  - `formatDateTime(iso?: string | null): string`、`highlightHtml(text?: string | null): string`
  - `request`（axios 实例，业务代码不直接用）
  - auth: `login(data: LoginForm): Promise<LoginVO>`、`register(data: RegisterForm): Promise<UserVO>`
  - article: `getArticlePage(params: ArticleQuery)`、`searchArticles(params: {keyword, page, size})`、`getArticleDetail(id)`、`getMyArticles(params: ArticleQuery)`、`publishArticle(data: ArticleFormData)`、`updateArticle(id, data)`、`deleteArticle(id)` —— 除 delete 外均 resolve 具体类型（见代码）
  - comment: `getCommentTree(articleId): Promise<CommentVO[]>`、`addComment(data): Promise<CommentVO>`
  - like: `likeArticle(id)/unlikeArticle(id): Promise<null>`、`getLikeStatus(id): Promise<boolean>`
  - favorite: `favoriteArticle(id)/unfavoriteArticle(id): Promise<null>`、`getFavoriteStatus(id): Promise<boolean>`、`getMyFavorites(params): Promise<PageResult<FavoriteVO>>`

- [ ] **Step 1: 创建类型定义 `src/types/api.ts`**

字段与后端 `common/PageResult.java`、各 VO 严格对应；时间均为后端 LocalDateTime 序列化出的 `"2026-08-24T10:30:00"` 格式字符串：

```ts
/** 与后端 common/PageResult 对应 */
export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  list: T[]
}

/** 后端 UserVO */
export interface UserVO {
  id: number
  username: string
  nickname: string | null
  email: string | null
  avatar: string | null
  createTime: string
}

/** 后端 LoginVO */
export interface LoginVO {
  token: string
  user: UserVO
}

/** 后端 AuthorVO */
export interface AuthorVO {
  id: number
  nickname: string | null
  avatar: string | null
}

/** 后端 ArticleVO */
export interface ArticleVO {
  id: number
  title: string
  content: string
  category: string | null
  tags: string | null
  viewCount: number
  likeCount: number
  status: number
  author: AuthorVO | null
  createTime: string
  updateTime: string
}

/** 后端 ArticleSearchVO（title/contentSnippet 含 <em> 高亮标签） */
export interface ArticleSearchVO {
  id: number
  title: string
  contentSnippet: string
  category: string | null
  tags: string | null
  viewCount: number
  likeCount: number
  createTime: string
}

/** 后端 FavoriteVO */
export interface FavoriteVO {
  favoriteId: number
  articleId: number
  title: string
  category: string | null
  tags: string | null
  viewCount: number
  likeCount: number
  author: AuthorVO | null
  articleCreateTime: string
  favoriteTime: string
}

/** 后端 CommentVO（parentId 为 0 表示一级评论） */
export interface CommentVO {
  id: number
  articleId: number
  parentId: number
  content: string
  author: AuthorVO | null
  createTime: string
  children: CommentVO[]
}
```

- [ ] **Step 2: 创建工具函数 `src/utils/format.ts`**

```ts
/** 后端 LocalDateTime 序列化为 "2026-08-24T10:30:00"，截取为 "2026-08-24 10:30" 展示 */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').slice(0, 16)
}

/**
 * 搜索结果的标题/摘要含 ES 高亮 <em> 标签。
 * 先整体转义 HTML 再还原 <em>，保证除高亮外不会渲染任何原始 HTML（防注入）
 */
export function highlightHtml(text?: string | null): string {
  if (!text) return ''
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped.replace(/&lt;(\/?)em&gt;/g, '<$1em>')
}
```

- [ ] **Step 3: 创建用户状态 `src/stores/user.ts`**

```ts
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginVO, UserVO } from '@/types/api'

const TOKEN_KEY = 'blog_token'
const USER_KEY = 'blog_user'

function loadStoredUser(): UserVO | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserVO) : null
  } catch {
    // localStorage 数据被手动改坏时不阻塞启动，视为未登录
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  // 初始化时从 localStorage 读回，刷新页面不丢登录态
  const token = ref(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<UserVO | null>(loadStoredUser())

  const isLoggedIn = computed(() => token.value !== '')

  /** 登录成功后写入状态并持久化 */
  function setLogin(loginVO: LoginVO) {
    token.value = loginVO.token
    user.value = loginVO.user
    localStorage.setItem(TOKEN_KEY, loginVO.token)
    localStorage.setItem(USER_KEY, JSON.stringify(loginVO.user))
  }

  /** 退出登录 / 登录过期：清空状态与持久化数据 */
  function clearLogin() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, isLoggedIn, setLogin, clearLogin }
})
```

- [ ] **Step 4: 创建 axios 封装 `src/utils/request.ts`**

```ts
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000,
})

// 请求拦截器：自动携带 token（在函数体内调用 useUserStore，
// 保证此时 Pinia 已随 app 初始化完成）
request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

/** 未登录 / 登录过期统一处理：清空本地状态并跳转登录页 */
function handleUnauthorized(message: string) {
  const userStore = useUserStore()
  userStore.clearLogin()
  ElMessage.warning(message)
  if (router.currentRoute.value.path !== '/login') {
    router.push({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  }
}

// 响应拦截器：解包统一返回结构 Result<T>，业务层直接拿到 data。
// 注意后端 GlobalExceptionHandler 不改 HTTP 状态码（业务错误也是 HTTP 200），
// 因此 code 判断必须在成功回调里做；HTTP 层错误（如安全过滤器直接返回的 401）走错误回调
request.interceptors.response.use(
  (response) => {
    const result = response.data
    if (result.code === 200) {
      return result.data
    }
    if (result.code === 401) {
      handleUnauthorized(result.message || '未登录或登录已过期')
    } else {
      ElMessage.error(result.message || '请求失败')
    }
    return Promise.reject(new Error(result.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      handleUnauthorized(message || '未登录或登录已过期')
    } else {
      ElMessage.error(message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default request
```

- [ ] **Step 5: 创建 API 模块 `src/api/auth.ts`**

```ts
import request from '@/utils/request'
import type { LoginVO, UserVO } from '@/types/api'

export interface LoginForm {
  username: string
  password: string
}

export interface RegisterForm {
  username: string
  password: string
  email?: string
  nickname?: string
}

/** 登录 */
export function login(data: LoginForm) {
  return request.post<never, LoginVO>('/auth/login', data)
}

/** 注册 */
export function register(data: RegisterForm) {
  return request.post<never, UserVO>('/auth/register', data)
}
```

说明：axios 泛型第二个参数是「返回值类型」。拦截器已把 `AxiosResponse` 解包成纯 data，所以统一写 `request.xxx<never, 期望类型>()`，调用方拿到的就是 `Promise<期望类型>`。

- [ ] **Step 6: 创建 API 模块 `src/api/article.ts`**

```ts
import request from '@/utils/request'
import type { ArticleSearchVO, ArticleVO, PageResult } from '@/types/api'

export interface ArticleQuery {
  pageNum: number
  pageSize: number
  sort?: 'time' | 'hot'
}

export interface ArticleFormData {
  title: string
  category?: string
  tags?: string
  content: string
}

/** 文章分页列表 */
export function getArticlePage(params: ArticleQuery) {
  return request.get<never, PageResult<ArticleVO>>('/articles', { params })
}

/** 全文搜索（ES 高亮） */
export function searchArticles(params: { keyword: string; page: number; size: number }) {
  return request.get<never, PageResult<ArticleSearchVO>>('/articles/search', { params })
}

/** 文章详情（浏览量 +1） */
export function getArticleDetail(id: number | string) {
  return request.get<never, ArticleVO>(`/articles/${id}`)
}

/** 我的文章分页 */
export function getMyArticles(params: ArticleQuery) {
  return request.get<never, PageResult<ArticleVO>>('/articles/my', { params })
}

/** 发布文章 */
export function publishArticle(data: ArticleFormData) {
  return request.post<never, ArticleVO>('/articles', data)
}

/** 更新文章（仅作者本人） */
export function updateArticle(id: number | string, data: ArticleFormData) {
  return request.put<never, ArticleVO>(`/articles/${id}`, data)
}

/** 删除文章（仅作者本人） */
export function deleteArticle(id: number | string) {
  return request.delete<never, null>(`/articles/${id}`)
}
```

- [ ] **Step 7: 创建 API 模块 `src/api/comment.ts`**

```ts
import request from '@/utils/request'
import type { CommentVO } from '@/types/api'

/** 某篇文章的评论树 */
export function getCommentTree(articleId: number | string) {
  return request.get<never, CommentVO[]>('/comments', { params: { articleId } })
}

/** 发表评论 / 回复评论；parentId 不传表示一级评论 */
export function addComment(data: { articleId: number | string; parentId?: number; content: string }) {
  return request.post<never, CommentVO>('/comments', data)
}
```

- [ ] **Step 8: 创建 API 模块 `src/api/like.ts`**

```ts
import request from '@/utils/request'

/** 点赞 */
export function likeArticle(articleId: number | string) {
  return request.post<never, null>(`/likes/${articleId}`)
}

/** 取消点赞 */
export function unlikeArticle(articleId: number | string) {
  return request.delete<never, null>(`/likes/${articleId}`)
}

/** 当前用户是否已点赞 */
export function getLikeStatus(articleId: number | string) {
  return request.get<never, boolean>(`/likes/${articleId}/status`)
}
```

- [ ] **Step 9: 创建 API 模块 `src/api/favorite.ts`**

```ts
import request from '@/utils/request'
import type { FavoriteVO, PageResult } from '@/types/api'

/** 收藏 */
export function favoriteArticle(articleId: number | string) {
  return request.post<never, null>(`/favorites/${articleId}`)
}

/** 取消收藏 */
export function unfavoriteArticle(articleId: number | string) {
  return request.delete<never, null>(`/favorites/${articleId}`)
}

/** 当前用户是否已收藏 */
export function getFavoriteStatus(articleId: number | string) {
  return request.get<never, boolean>(`/favorites/${articleId}/status`)
}

/** 我的收藏分页 */
export function getMyFavorites(params: { pageNum: number; pageSize: number }) {
  return request.get<never, PageResult<FavoriteVO>>('/favorites', { params })
}
```

- [ ] **Step 10: 构建验证**

说明：此时 `request.ts` import 了 `@/router`（现有空路由）与 `@/stores/user`（Step 3 已建），依赖齐备。

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0

- [ ] **Step 11: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/types frontend/src/utils frontend/src/stores frontend/src/api
git commit -m "feat(frontend): 类型定义、用户状态、axios 封装与 API 模块"
```

---

### Task 6: 应用骨架 App.vue 与顶部导航 AppHeader

**Files:**
- Modify: `frontend/src/App.vue`（整体替换脚手架内容）
- Create: `frontend/src/components/AppHeader.vue`

**Interfaces:**
- Consumes: Task 5 的 `useUserStore`。
- Produces: 全局布局（顶栏 + `<router-view>`）。AppHeader 自带搜索框，跳 `/articles?keyword=xx`（Task 8 列表页消费该 query）。

- [ ] **Step 1: 重写 `src/App.vue`**

```vue
<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
</script>

<template>
  <el-container class="layout">
    <el-header class="layout-header">
      <AppHeader />
    </el-header>
    <el-main class="layout-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout-header {
  padding: 0;
  border-bottom: 1px solid var(--el-border-color-light);
}
.layout-main {
  max-width: 960px;
  margin: 0 auto;
  width: 100%;
}
</style>
```

- [ ] **Step 2: 创建 `src/components/AppHeader.vue`**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')

/** 回车或点击按钮：带关键词跳转文章列表页 */
function onSearch() {
  const kw = keyword.value.trim()
  router.push(kw ? { path: '/articles', query: { keyword: kw } } : { path: '/articles' })
}

function onUserCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    userStore.clearLogin()
    ElMessage.success('已退出登录')
    router.push('/articles')
  }
}
</script>

<template>
  <div class="app-header">
    <span class="logo" @click="router.push('/articles')">我的博客</span>
    <el-input
      v-model="keyword"
      class="search"
      placeholder="搜索文章"
      clearable
      @keyup.enter="onSearch"
    >
      <template #append>
        <el-button @click="onSearch">
          <el-icon><search /></el-icon>
        </el-button>
      </template>
    </el-input>
    <div class="actions">
      <template v-if="userStore.isLoggedIn">
        <el-button type="primary" @click="router.push('/publish')">发布文章</el-button>
        <el-dropdown @command="onUserCommand">
          <span class="nickname">
            {{ userStore.user?.nickname || userStore.user?.username }}
            <el-icon><arrow-down /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
      <template v-else>
        <el-button @click="router.push('/login')">登录</el-button>
        <el-button type="primary" @click="router.push('/register')">注册</el-button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 60px;
  padding: 0 24px;
}
.logo {
  font-size: 20px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.search {
  max-width: 360px;
}
.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.nickname {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  outline: none;
}
</style>
```

- [ ] **Step 3: 构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0

- [ ] **Step 4: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/App.vue frontend/src/components/AppHeader.vue
git commit -m "feat(frontend): 应用骨架与顶部导航"
```

---

### Task 7: 登录页与注册页

**Files:**
- Create: `frontend/src/views/Login.vue`
- Create: `frontend/src/views/Register.vue`

**Interfaces:**
- Consumes: Task 5 的 `login()/register()`、`useUserStore.setLogin()`。
- Produces: 路由 `/login`、`/register`（Task 12 注册路由）。约定：登录页支持 `?redirect=` 登录后回跳、`?username=` 预填（注册页跳转过来）。

- [ ] **Step 1: 创建 `src/views/Login.vue`**

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
// 从注册页跳转过来时预填用户名
const form = reactive({
  username: (route.query.username as string) ?? '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const loginVO = await login(form)
    userStore.setLogin(loginVO)
    ElMessage.success('登录成功')
    await router.replace((route.query.redirect as string) || '/articles')
  } catch {
    // 失败提示已由响应拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="auth-title">登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="密码"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="footer-link">还没有账号？<router-link to="/register">去注册</router-link></div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding-top: 80px;
}
.auth-card {
  width: 400px;
}
.auth-title {
  text-align: center;
  margin-top: 0;
}
.submit-btn {
  width: 100%;
}
.footer-link {
  text-align: center;
  font-size: 14px;
}
</style>
```

- [ ] **Step 2: 创建 `src/views/Register.vue`**

校验规则对齐后端 `RegisterDTO`（用户名 3-20、密码 6-20、邮箱格式、昵称 ≤50）：

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度需在 3-20 个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度需在 6-20 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value === form.password) callback()
        else callback(new Error('两次输入的密码不一致'))
      },
      trigger: 'blur',
    },
  ],
  nickname: [{ max: 50, message: '昵称不能超过 50 个字符', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

async function onSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    // 选填字段为空串时不传，避免后端把 "" 当非法邮箱
    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
      email: form.email || undefined,
    })
    ElMessage.success('注册成功，请登录')
    await router.push({ path: '/login', query: { username: form.username } })
  } catch {
    // 失败提示（如用户名已存在）已由拦截器弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="auth-title">注册</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（3-20 个字符）" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="密码（6-20 个字符）" />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="确认密码" />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称（选填）" />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="邮箱（选填）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
            注 册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="footer-link">已有账号？<router-link to="/login">去登录</router-link></div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding-top: 60px;
}
.auth-card {
  width: 420px;
}
.auth-title {
  text-align: center;
  margin-top: 0;
}
.submit-btn {
  width: 100%;
}
.footer-link {
  text-align: center;
  font-size: 14px;
}
</style>
```

- [ ] **Step 3: 构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0（两个页面此时尚未挂到路由上，但 vue-tsc 会完整检查其类型）

- [ ] **Step 4: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/views/Login.vue frontend/src/views/Register.vue
git commit -m "feat(frontend): 登录与注册页"
```

---

### Task 8: 文章列表页（分页 / 排序 / 搜索）

**Files:**
- Create: `frontend/src/views/ArticleList.vue`

**Interfaces:**
- Consumes: Task 5 的 `getArticlePage()`、`searchArticles()`、`formatDateTime()`、`highlightHtml()`；Task 6 AppHeader 写入的 `route.query.keyword`。
- Produces: 路由 `/articles`（Task 12 注册）。列表项点击跳 `/articles/:id`。

- [ ] **Step 1: 创建 `src/views/ArticleList.vue`**

```vue
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getArticlePage, searchArticles } from '@/api/article'
import type { ArticleSearchVO, ArticleVO } from '@/types/api'
import { formatDateTime, highlightHtml } from '@/utils/format'

/** 列表项：普通分页是 ArticleVO，搜索结果多一个 contentSnippet 片段 */
type ListItem = ArticleVO & Partial<Pick<ArticleSearchVO, 'contentSnippet'>>

const route = useRoute()

const loading = ref(false)
const list = ref<ListItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const sort = ref<'time' | 'hot'>('time')
const keyword = ref((route.query.keyword as string) ?? '')

/** 关键词非空走 ES 搜索接口，否则走普通分页接口 */
async function load() {
  loading.value = true
  try {
    const kw = keyword.value.trim()
    const page = kw
      ? await searchArticles({ keyword: kw, page: pageNum.value, size: pageSize.value })
      : await getArticlePage({ pageNum: pageNum.value, pageSize: pageSize.value, sort: sort.value })
    list.value = page.list as ListItem[]
    total.value = Number(page.total)
  } catch {
    // 错误已由响应拦截器提示
  } finally {
    loading.value = false
  }
}

/** 条件变化（换排序 / 换每页大小 / 清空关键词）后从第一页重新加载 */
function resetAndLoad() {
  pageNum.value = 1
  load()
}

// 用户停留在列表页时再次通过顶部导航搜索：响应 query 变化
watch(
  () => route.query.keyword,
  (kw) => {
    keyword.value = (kw as string) ?? ''
    resetAndLoad()
  },
)

function onTagClose() {
  keyword.value = ''
  resetAndLoad()
}

onMounted(load)
</script>

<template>
  <div class="article-list">
    <div class="toolbar">
      <el-radio-group v-model="sort" @change="resetAndLoad">
        <el-radio-button value="time">最新</el-radio-button>
        <el-radio-button value="hot">最热</el-radio-button>
      </el-radio-group>
      <el-tag v-if="keyword.trim()" closable @close="onTagClose">搜索：{{ keyword }}</el-tag>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="暂无文章" />
    <div v-loading="loading">
      <el-card
        v-for="item in list"
        :key="item.id"
        class="item"
        shadow="hover"
        @click="$router.push(`/articles/${item.id}`)"
      >
        <!-- 标题/摘要可能含 ES 高亮 <em>，highlightHtml 已确保只放行 em 标签 -->
        <h3 class="title" v-html="highlightHtml(item.title)" />
        <p v-if="item.contentSnippet" class="snippet" v-html="highlightHtml(item.contentSnippet)" />
        <div class="meta">
          <span>{{ item.author?.nickname || '佚名' }}</span>
          <el-tag v-if="item.category" size="small" type="info">{{ item.category }}</el-tag>
          <span>{{ formatDateTime(item.createTime) }}</span>
          <span class="meta-num"><el-icon><view /></el-icon>{{ item.viewCount }}</span>
          <span class="meta-num"><el-icon><star /></el-icon>{{ item.likeCount }}</span>
        </div>
      </el-card>
    </div>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @current-change="load"
      @size-change="resetAndLoad"
    />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.item {
  margin-bottom: 12px;
  cursor: pointer;
}
.title {
  margin: 0 0 8px;
  font-size: 17px;
}
.title :deep(em),
.snippet :deep(em) {
  color: var(--el-color-primary);
  font-style: normal;
}
.snippet {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.meta-num {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.pagination {
  justify-content: center;
  margin-top: 20px;
}
</style>
```

- [ ] **Step 2: 构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0

- [ ] **Step 3: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/views/ArticleList.vue
git commit -m "feat(frontend): 文章列表页（分页/排序/搜索高亮）"
```

---

### Task 9: 评论组件与文章详情页

**Files:**
- Create: `frontend/src/components/CommentItem.vue`
- Create: `frontend/src/views/ArticleDetail.vue`

**Interfaces:**
- Consumes: Task 5 的 `getArticleDetail/getArticlePage 无关——具体为 getArticleDetail()`、`deleteArticle()`、`getCommentTree()`、`addComment()`、`likeArticle()/unlikeArticle()/getLikeStatus()`、`favoriteArticle()/unfavoriteArticle()/getFavoriteStatus()`、`useUserStore`、`formatDateTime`；Task 4 全局注册的图标。
- Produces: 路由 `/articles/:id`（Task 12 注册）。`CommentItem` 是递归组件（SFC 以文件名自引用），props `{ comment: CommentVO }`，事件 `reply(comment: CommentVO)`。
- **回复语义约定**：后端按 parentId 建两级树，界面上回复任意层级评论都挂到其所在一级评论之下——`parentId = 目标评论.parentId === 0 ? 目标评论.id : 目标评论.parentId`。

- [ ] **Step 1: 创建递归评论组件 `src/components/CommentItem.vue`**

```vue
<script setup lang="ts">
import type { CommentVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

defineProps<{
  comment: CommentVO
}>()

const emit = defineEmits<{
  reply: [comment: CommentVO]
}>()
</script>

<template>
  <div class="comment-item">
    <div class="head">
      <el-avatar :size="28">{{ comment.author?.nickname?.charAt(0) || '?' }}</el-avatar>
      <span class="nickname">{{ comment.author?.nickname || '佚名' }}</span>
      <span class="time">{{ formatDateTime(comment.createTime) }}</span>
      <el-button link type="primary" size="small" @click="emit('reply', comment)">回复</el-button>
    </div>
    <p class="content">{{ comment.content }}</p>
    <div v-if="comment.children.length > 0" class="children">
      <CommentItem
        v-for="child in comment.children"
        :key="child.id"
        :comment="child"
        @reply="emit('reply', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.comment-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nickname {
  font-weight: 500;
}
.time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.content {
  margin: 8px 0 0 36px;
  white-space: pre-wrap;
}
.children {
  margin-left: 36px;
}
</style>
```

- [ ] **Step 2: 创建 `src/views/ArticleDetail.vue`**

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { addComment, getCommentTree } from '@/api/comment'
import { deleteArticle, getArticleDetail } from '@/api/article'
import { favoriteArticle, getFavoriteStatus, unfavoriteArticle } from '@/api/favorite'
import { getLikeStatus, likeArticle, unlikeArticle } from '@/api/like'
import type { ArticleVO, CommentVO } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'
import CommentItem from '@/components/CommentItem.vue'

// 默认 html:false：正文里的原始 HTML 会被转义，防 XSS
const md = new MarkdownIt({ breaks: true })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const articleId = route.params.id as string

const loading = ref(false)
const article = ref<ArticleVO | null>(null)
const liked = ref(false)
const favorited = ref(false)
const comments = ref<CommentVO[]>([])
const commentContent = ref('')
const submitting = ref(false)
/** 正在回复的评论；null 表示发表一级评论 */
const replyTo = ref<CommentVO | null>(null)

const renderedContent = computed(() => (article.value ? md.render(article.value.content) : ''))

const isAuthor = computed(
  () => userStore.isLoggedIn && article.value?.author?.id === userStore.user?.id,
)

/** 未登录点击互动按钮时引导登录，并记录回跳地址 */
function requireLogin(): boolean {
  if (userStore.isLoggedIn) return true
  ElMessage.warning('请先登录')
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function loadArticle() {
  loading.value = true
  try {
    article.value = await getArticleDetail(articleId)
  } finally {
    loading.value = false
  }
}

async function loadComments() {
  comments.value = await getCommentTree(articleId)
}

/** 已登录则进入页面时回显点赞/收藏状态 */
async function loadInteractionStatus() {
  if (!userStore.isLoggedIn) return
  liked.value = await getLikeStatus(articleId)
  favorited.value = await getFavoriteStatus(articleId)
}

async function onToggleLike() {
  if (!requireLogin() || !article.value) return
  if (liked.value) {
    await unlikeArticle(articleId)
    liked.value = false
    article.value.likeCount -= 1
  } else {
    await likeArticle(articleId)
    liked.value = true
    article.value.likeCount += 1
  }
}

async function onToggleFavorite() {
  if (!requireLogin()) return
  if (favorited.value) {
    await unfavoriteArticle(articleId)
    favorited.value = false
    ElMessage.success('已取消收藏')
  } else {
    await favoriteArticle(articleId)
    favorited.value = true
    ElMessage.success('收藏成功')
  }
}

async function onDelete() {
  await ElMessageBox.confirm('删除后不可恢复，确定删除该文章吗？', '删除确认', { type: 'warning' })
  await deleteArticle(articleId)
  ElMessage.success('删除成功')
  await router.push('/articles')
}

function onReply(comment: CommentVO) {
  replyTo.value = comment
}

function cancelReply() {
  replyTo.value = null
}

/** 回复任意层级评论都挂到其一级父评论下（见任务 Interfaces 的语义约定） */
function resolveParentId(target: CommentVO): number {
  return target.parentId === 0 ? target.id : target.parentId
}

async function onSubmitComment() {
  const content = commentContent.value.trim()
  if (!content) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  submitting.value = true
  try {
    await addComment({
      articleId,
      parentId: replyTo.value ? resolveParentId(replyTo.value) : undefined,
      content,
    })
    commentContent.value = ''
    cancelReply()
    await loadComments()
  } catch {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadArticle()
  loadComments()
  loadInteractionStatus()
})
</script>

<template>
  <div v-loading="loading" class="article-detail">
    <template v-if="article">
      <el-card>
        <h1 class="title">{{ article.title }}</h1>
        <div class="meta">
          <span>{{ article.author?.nickname || '佚名' }}</span>
          <el-tag v-if="article.category" size="small" type="info">{{ article.category }}</el-tag>
          <span>{{ formatDateTime(article.createTime) }}</span>
          <span>浏览 {{ article.viewCount }}</span>
          <el-tag v-for="tag in (article.tags || '').split(',').filter((t) => t.trim())" :key="tag" size="small">
            {{ tag.trim() }}
          </el-tag>
          <span v-if="isAuthor" class="owner-actions">
            <el-button link type="primary" @click="router.push(`/publish/${article.id}`)">编辑</el-button>
            <el-button link type="danger" @click="onDelete">删除</el-button>
          </span>
        </div>
        <div class="action-bar">
          <el-button :type="liked ? 'primary' : 'default'" round @click="onToggleLike">
            <el-icon><star /></el-icon>
            点赞 {{ article.likeCount }}
          </el-button>
          <el-button :type="favorited ? 'warning' : 'default'" round @click="onToggleFavorite">
            <el-icon><collection-tag /></el-icon>
            {{ favorited ? '已收藏' : '收藏' }}
          </el-button>
        </div>
        <!-- 正文由 markdown-it 渲染，html:false 保证原始 HTML 被转义 -->
        <div class="markdown-body" v-html="renderedContent" />
      </el-card>

      <el-card class="comments-card">
        <h3 class="comments-title">评论（{{ comments.length }}）</h3>
        <div v-if="replyTo" class="reply-banner">
          回复 @{{ replyTo.author?.nickname || '佚名' }}
          <el-button link size="small" @click="cancelReply">取消</el-button>
        </div>
        <div class="comment-editor">
          <el-input
            v-model="commentContent"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="写下你的评论…"
          />
          <el-button type="primary" :loading="submitting" class="comment-submit" @click="onSubmitComment">
            发表评论
          </el-button>
        </div>
        <el-empty v-if="comments.length === 0" description="还没有评论，来抢沙发" :image-size="80" />
        <CommentItem v-for="c in comments" :key="c.id" :comment="c" @reply="onReply" />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.title {
  margin-top: 0;
}
.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.owner-actions {
  margin-left: auto;
}
.action-bar {
  display: flex;
  gap: 12px;
  margin: 16px 0;
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.markdown-body {
  line-height: 1.8;
}
.markdown-body :deep(img) {
  max-width: 100%;
}
.markdown-body :deep(pre) {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
}
.markdown-body :deep(code) {
  background: var(--el-fill-color-light);
  padding: 2px 4px;
  border-radius: 3px;
}
.comments-card {
  margin-top: 16px;
}
.comments-title {
  margin-top: 0;
}
.reply-banner {
  margin-bottom: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 14px;
}
.comment-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
  margin-bottom: 16px;
}
.comment-editor :deep(.el-textarea) {
  width: 100%;
}
</style>
```

- [ ] **Step 3: 构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0

- [ ] **Step 4: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/components/CommentItem.vue frontend/src/views/ArticleDetail.vue
git commit -m "feat(frontend): 文章详情页（Markdown 渲染/点赞收藏/评论树）"
```

---

### Task 10: 发布/编辑文章页（Markdown 分栏编辑器）

**Files:**
- Create: `frontend/src/views/ArticleEdit.vue`

**Interfaces:**
- Consumes: Task 5 的 `getArticleDetail()`（编辑模式回填）、`publishArticle()`、`updateArticle()`；返回的 `ArticleVO.id` 用于发布后跳转。
- Produces: 路由 `/publish`（新建）、`/publish/:id`（编辑），均要求登录（Task 12 注册）。

- [ ] **Step 1: 创建 `src/views/ArticleEdit.vue`**

```vue
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { getArticleDetail, publishArticle, updateArticle } from '@/api/article'
import type { ArticleFormData } from '@/api/article'

const md = new MarkdownIt({ breaks: true })

const route = useRoute()
const router = useRouter()

/** 有 id 即编辑模式 */
const articleId = route.params.id as string | undefined

const formRef = ref<FormInstance>()
const pageLoading = ref(false)
const saving = ref(false)
const form = reactive<ArticleFormData>({
  title: '',
  category: '',
  tags: '',
  content: '',
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' },
  ],
  category: [{ max: 50, message: '分类不能超过 50 个字符', trigger: 'blur' }],
  tags: [{ max: 255, message: '标签不能超过 255 个字符', trigger: 'blur' }],
  content: [{ required: true, message: '请输入文章内容', trigger: 'blur' }],
}

const previewHtml = computed(() => md.render(form.content))

async function loadForEdit() {
  if (!articleId) return
  pageLoading.value = true
  try {
    const article = await getArticleDetail(articleId)
    form.title = article.title
    form.category = article.category ?? ''
    form.tags = article.tags ?? ''
    form.content = article.content
  } finally {
    pageLoading.value = false
  }
}

async function onSubmit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const saved = articleId
      ? await updateArticle(articleId, form)
      : await publishArticle(form)
    ElMessage.success(articleId ? '更新成功' : '发布成功')
    await router.push(`/articles/${saved.id}`)
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

onMounted(loadForEdit)
</script>

<template>
  <div v-loading="pageLoading" class="article-edit">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
      <el-form-item prop="title">
        <el-input v-model="form.title" size="large" placeholder="文章标题（必填，最多 200 字）" />
      </el-form-item>
      <el-form-item prop="category">
        <el-input v-model="form.category" placeholder="分类（选填，如：技术 / 生活）" style="max-width: 240px" />
      </el-form-item>
      <el-form-item prop="tags">
        <el-input v-model="form.tags" placeholder="标签（选填，英文逗号分隔，如：vue,spring）" style="max-width: 360px" />
      </el-form-item>
      <el-form-item prop="content">
        <div class="editor">
          <el-input
            v-model="form.content"
            type="textarea"
            class="editor-input"
            :autosize="{ minRows: 20, maxRows: 30 }"
            placeholder="支持 Markdown 语法…"
          />
          <div class="editor-preview markdown-body" v-html="previewHtml" />
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="large" :loading="saving" @click="onSubmit">
          {{ articleId ? '保存修改' : '发布文章' }}
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.editor {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
}
.editor-input :deep(.el-textarea__inner) {
  height: 100%;
  font-family: Consolas, Monaco, monospace;
}
.editor-preview {
  min-height: 480px;
  padding: 12px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  overflow-wrap: break-word;
}
.editor-preview :deep(img) {
  max-width: 100%;
}
@media (max-width: 900px) {
  .editor {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 2: 构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0

- [ ] **Step 3: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/views/ArticleEdit.vue
git commit -m "feat(frontend): 发布/编辑文章页（Markdown 分栏实时预览）"
```

---

### Task 11: 个人中心页（我的文章 / 我的收藏）

**Files:**
- Create: `frontend/src/views/Profile.vue`

**Interfaces:**
- Consumes: Task 5 的 `getMyArticles()`、`deleteArticle()`、`getMyFavorites()`、`unfavoriteArticle()`、`useUserStore`、`formatDateTime`。
- Produces: 路由 `/profile`（要求登录，Task 12 注册）。编辑跳 `/publish/:id`，标题点击跳 `/articles/:id`。

- [ ] **Step 1: 创建 `src/views/Profile.vue`**

```vue
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteArticle, getMyArticles } from '@/api/article'
import { getMyFavorites, unfavoriteArticle } from '@/api/favorite'
import type { ArticleVO, FavoriteVO } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

// ---- 我的作品 Tab 状态 ----
const myList = ref<ArticleVO[]>([])
const myTotal = ref(0)
const myPage = reactive({ pageNum: 1, pageSize: 10 })
const myLoading = ref(false)

async function loadMyArticles() {
  myLoading.value = true
  try {
    const page = await getMyArticles(myPage)
    myList.value = page.list
    myTotal.value = Number(page.total)
  } finally {
    myLoading.value = false
  }
}

async function onDeleteArticle(article: ArticleVO) {
  await ElMessageBox.confirm(`确定删除《${article.title}》吗？`, '删除确认', { type: 'warning' })
  await deleteArticle(article.id)
  ElMessage.success('删除成功')
  await loadMyArticles()
}

// ---- 我的收藏 Tab 状态 ----
const favList = ref<FavoriteVO[]>([])
const favTotal = ref(0)
const favPage = reactive({ pageNum: 1, pageSize: 10 })
const favLoading = ref(false)

async function loadFavorites() {
  favLoading.value = true
  try {
    const page = await getMyFavorites(favPage)
    favList.value = page.list
    favTotal.value = Number(page.total)
  } finally {
    favLoading.value = false
  }
}

async function onCancelFavorite(item: FavoriteVO) {
  await unfavoriteArticle(item.articleId)
  ElMessage.success('已取消收藏')
  await loadFavorites()
}

onMounted(() => {
  loadMyArticles()
  loadFavorites()
})
</script>

<template>
  <div class="profile">
    <h2 class="welcome">你好，{{ userStore.user?.nickname || userStore.user?.username }}</h2>
    <el-tabs>
      <el-tab-pane label="我的文章">
        <el-table v-loading="myLoading" :data="myList">
          <el-table-column label="标题" min-width="220">
            <template #default="{ row }">
              <el-link type="primary" @click="router.push(`/articles/${row.id}`)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="110" />
          <el-table-column prop="viewCount" label="浏览量" width="90" />
          <el-table-column prop="likeCount" label="点赞数" width="90" />
          <el-table-column label="发布时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/publish/${row.id}`)">编辑</el-button>
              <el-button link type="danger" @click="onDeleteArticle(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="myPage.pageNum"
          v-model:page-size="myPage.pageSize"
          :total="myTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="pagination"
          @current-change="loadMyArticles"
          @size-change="myPage.pageNum = 1; loadMyArticles()"
        />
      </el-tab-pane>

      <el-tab-pane label="我的收藏">
        <el-table v-loading="favLoading" :data="favList">
          <el-table-column label="标题" min-width="220">
            <template #default="{ row }">
              <el-link type="primary" @click="router.push(`/articles/${row.articleId}`)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="作者" width="130">
            <template #default="{ row }">{{ row.author?.nickname || '佚名' }}</template>
          </el-table-column>
          <el-table-column label="文章发布时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.articleCreateTime) }}</template>
          </el-table-column>
          <el-table-column label="收藏时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.favoriteTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="danger" @click="onCancelFavorite(row)">取消收藏</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="favPage.pageNum"
          v-model:page-size="favPage.pageSize"
          :total="favTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="pagination"
          @current-change="loadFavorites"
          @size-change="favPage.pageNum = 1; loadFavorites()"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.welcome {
  margin-top: 0;
}
.pagination {
  justify-content: center;
  margin-top: 16px;
}
</style>
```

- [ ] **Step 2: 构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0

- [ ] **Step 3: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/views/Profile.vue
git commit -m "feat(frontend): 个人中心页（我的文章/我的收藏）"
```

---

### Task 12: 路由注册、全局守卫与最终验证

**Files:**
- Modify: `frontend/src/router/index.ts`（整体替换，现为空 routes）

**Interfaces:**
- Consumes: 前 11 个任务的全部产出——六个视图组件、`useUserStore.isLoggedIn`。
- Produces: 完整可用的 SPA 路由。守卫规则：`requiresAuth` 页面未登录 → `/login?redirect=目标`；`guestOnly` 页面（登录/注册）已登录 → `/articles`。

- [ ] **Step 1: 重写 `src/router/index.ts`**

```ts
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/articles' },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Register.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/articles',
      name: 'article-list',
      component: () => import('@/views/ArticleList.vue'),
    },
    {
      path: '/articles/:id',
      name: 'article-detail',
      component: () => import('@/views/ArticleDetail.vue'),
    },
    {
      // 可选数字参数：无 id 为新建，有 id 为编辑
      path: '/publish/:id(\\d+)?',
      name: 'article-edit',
      component: () => import('@/views/ArticleEdit.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/Profile.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

// 全局前置守卫：
// 1. 需要登录的页面未登录 → 登录页并记录回跳地址
// 2. 已登录访问登录/注册页 → 直接回文章列表
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && userStore.isLoggedIn) {
    return { path: '/articles' }
  }
  return true
})

export default router
```

- [ ] **Step 2: 最终构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0，vite 产物包含各视图 chunk

- [ ] **Step 3: Lint 校验（脚手架自带，顺手确认不引入新问题）**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run lint`
Expected: 退出码 0（若有纯风格类报错，修复后重跑）

- [ ] **Step 4: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/router/index.ts
git commit -m "feat(frontend): 路由注册与全局登录守卫"
```

- [ ] **Step 5: 人工冒烟（需要全套中间件，向用户交付时逐项核对）**

前置：MySQL(blog 库)、Redis(Docker 容器映射 16379)、RabbitMQ、ES 已就绪；`cd blog && ./mvnw.cmd spring-boot:run`；`cd frontend && npm run dev`。

核对项（浏览器访问 http://localhost:5173 ）：
1. 游客打开 /articles 能看到文章列表，分页/最新最热切换正常
2. 游客打开文章详情能看到渲染后的 Markdown 和评论列表；点点赞/收藏被引导到登录页且登录后回到原文章
3. 注册新用户 → 自动带用户名到登录页 → 登录成功回列表，顶栏出现用户名和发布按钮
4. 发布一篇文章（Markdown 内容）→ 跳详情，左侧编辑右侧预览正常
5. 详情页点赞/收藏按钮状态正确切换，数量增减正确；刷新页面后激活态保持
6. 发表评论、回复评论，树形展示正确
7. 个人中心：我的文章分页/编辑/删除正常；我的收藏展示并可取消收藏
8. 搜索框搜关键词，命中高亮显示；关闭标签恢复完整列表
9. 手动清掉 localStorage 的 blog_token 后刷新，访问 /profile 被重定向登录页

## Self-Review 记录

- 规格覆盖：设计文档 §一 三处后端补充 → Task 1/2/3；§二 前端结构全部文件 → Task 4-6；§三 路由权限 → Task 12；§四 六个页面功能明细 → Task 7-11；§五 错误处理 → Task 5 拦截器 + 各页面 catch 注释；§六 依赖 → Task 4；§七 验证 → 各任务构建验证 + Task 12 Step 5。无遗漏。
- 占位符扫描：无 TBD/TODO/“适当处理”类表述，所有代码均为完整可落地内容。
- 类型一致性：API 函数名与签名在 Task 5「Produces」与各视图任务的「Consumes」逐一比对一致（`getMyArticles`、`getFavoriteStatus`、`resolveParentId` 约定等）；store 字段 `isLoggedIn/setLogin/clearLogin` 全文一致。
