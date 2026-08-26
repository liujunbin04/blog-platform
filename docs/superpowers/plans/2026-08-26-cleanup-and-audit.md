# 遗留优化落实实施计划（Part A）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落实设计文档 Part A 的 10 项遗留优化——后端 4 项（草稿可见性、编辑数据接口、搜索补作者、安全收紧）+ 前端 6 项（baseURL 环境变量、列表页去重加载、rejection 清理、评论计数、编辑页路由响应）。

**Architecture:** 后端改动集中在 ArticleServiceImpl/ArticleController 与两个配置类；前端集中在三个视图文件 + request.ts + env.d.ts。全部为增量修改，无新文件（除 env.d.ts 内补类型段）。

**Tech Stack:** Spring Boot 3.5 / MyBatis-Plus / Spring Security；Vue 3 + TS + Element Plus + axios。

**设计文档:** `docs/superpowers/specs/2026-08-26-cleanup-and-audit-design.md`（Part A 为需求来源）

## Global Constraints

- 验证门槛：后端 `./mvnw.cmd -q compile` 退出码 0；前端 `npm run build` 与 `npm run lint` 均退出码 0（本项目无自动化测试设施，不得引入测试框架）。
- 代码风格与现有代码一致：中文注释、解释「为什么」；前端 `<script setup lang="ts">` + scoped 样式。
- 匿名访问草稿必须得到 404 而非 401（不暴露草稿存在性）；游客浏览已发布文章的路径不能受影响。
- SecurityConfig 现有放行规则 `/api/articles/*` 仅匹配单段路径，`GET /api/articles/{id}/edit-data` 是两段路径、天然需认证——本计划不需要也不得修改 SecurityConfig。
- localStorage key 不变（blog_token/blog_user）；axios baseURL 默认值仍是 `http://localhost:8080/api`。
- 提交信息格式：`类型(范围): 中文描述`。
- 所有命令在 Windows git-bash 下执行。

---

### Task 1: 后端草稿可见性修复 + 编辑数据接口

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/service/ArticleService.java`
- Modify: `blog/src/main/java/com/example/blog/controller/ArticleController.java`
- Modify: `frontend/src/api/article.ts`
- Modify: `frontend/src/views/ArticleEdit.vue`

**Interfaces:**
- Consumes: 现有 `getDetail(id)`、`getArticleById(id)`（@Cacheable 纯读）、`SecurityUtil.getLoginUser()`（匿名时抛 BusinessException(401)）、`LoginUser.getUserId()`。
- Produces: `GET /api/articles/{id}/edit-data` 返回 `Result<ArticleVO>`（需登录+仅作者）；前端 API 函数 `getArticleEditData(id: number | string): Promise<ArticleVO>`。

- [ ] **Step 1: ArticleService 接口不加方法（说明）**

编辑数据接口的权限校验放在 Controller 层（原因见 Step 2 的注释），Service 层无需新增方法，直接复用现有 `getById` 与 `getArticleById`。

- [ ] **Step 1a: ArticleServiceImpl.getDetail 加草稿校验**

在 `getDetail` 方法中，第一个 `if (article == null) throw ...` 之后插入：

```java
        // 草稿（status=0）仅作者本人可见：其余任何人（含匿名访客）一律按
        // 「不存在」处理——返回 404 而非 401/403，避免向未授权者暴露草稿的存在性
        boolean isOwner = false;
        try {
            isOwner = article.getAuthorId().equals(SecurityUtil.getCurrentUserId());
        } catch (BusinessException e) {
            // 匿名访客：isOwner 保持 false，走下面的 404 分支
        }
        if (article.getStatus() != 1 && !isOwner) {
            throw new BusinessException(404, "文章不存在");
        }
```

说明：`getDetail` 上的 `@CachePut(value = "article", key = "#id")` 缓存的是 VO 结果；草稿对非作者抛异常不会被缓存，作者与游客看到的已发布文章内容相同，缓存正确性不受影响。

- [ ] **Step 1b: ArticleController 新增 edit-data 端点**

在 `detail` 端点之前插入：

```java
    /**
     * 文章编辑数据（仅作者本人）：GET /api/articles/{id}/edit-data
     * <p>与详情接口的区别：走纯读缓存、浏览量不自增；
     * 权限校验在此处做而非 Service 层，保证 getArticleById 经代理调用、@Cacheable 生效
     * （类内直调缓存不生效，见 ArticleService.getArticleById 的注释）</p>
     */
    @GetMapping("/{id}/edit-data")
    public Result<ArticleVO> editData(@PathVariable Long id) {
        Article article = articleService.getById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (!article.getAuthorId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(403, "无权操作他人的文章");
        }
        return Result.success(articleService.getArticleById(id));
    }
```

需要补充 import：`com.example.blog.common.BusinessException`、`com.example.blog.entity.Article`、`com.example.blog.utils.SecurityUtil`。

安全说明：该路径是 `/api/articles/{id}/edit-data` 两段路径，现有放行规则 `GET /api/articles/*` 只匹配单段路径，不会被误放行——SecurityConfig 无需改动。

- [ ] **Step 4: 后端编译验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0

- [ ] **Step 5: 前端 API 函数**

`frontend/src/api/article.ts` 在 `getMyArticles` 之后追加：

```ts
/** 文章编辑数据（仅作者本人，不增加浏览量） */
export function getArticleEditData(id: number | string) {
  return request.get<never, ArticleVO>(`/articles/${id}/edit-data`)
}
```

- [ ] **Step 6: 编辑页切换到新接口**

`frontend/src/views/ArticleEdit.vue`：
import 行把 `getArticleDetail` 改为 `getArticleEditData`：

```ts
import { getArticleEditData, publishArticle, updateArticle } from '@/api/article'
```

`loadForEdit` 中调用处同步替换：

```ts
    const article = await getArticleEditData(articleId)
```

- [ ] **Step 7: 前端构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0

- [ ] **Step 8: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java blog/src/main/java/com/example/blog/controller/ArticleController.java frontend/src/api/article.ts frontend/src/views/ArticleEdit.vue
git commit -m "fix(blog): 草稿仅作者可见并新增编辑数据接口避免回填虚增浏览量"
```

---

### Task 2: 搜索结果补作者

**Files:**
- Modify: `blog/src/main/java/com/example/blog/vo/ArticleSearchVO.java`
- Modify: `blog/src/main/java/com/example/blog/controller/ArticleController.java`
- Modify: `frontend/src/types/api.ts`

**Interfaces:**
- Consumes: ES 文档已有的 `doc.getAuthorId()`；`userService.listByIds(...)` 批量查询（Controller 需注入 UserService）。
- Produces: `ArticleSearchVO.author: AuthorVO | null`（Java 与前端 TS 类型同步增加）；列表页搜索结果显示真实昵称（模板无需改动，已有兜底）。

- [ ] **Step 1: ArticleSearchVO 增加 author 字段与组装逻辑**

import 区新增：

```java
import com.example.blog.entity.User;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
```

类中在 `likeCount` 字段之后追加：

```java
    /** 作者信息（由 controller 按 authorId 批量回查填充，作者已删除则为 null） */
    private AuthorVO author;
```

类末尾追加静态方法：

```java
    /**
     * 批量回查作者并填充：一次 IN 查询代替逐条查（防 N+1），顺序保持命中顺序
     */
    public static List<ArticleSearchVO> fillAuthors(List<ArticleSearchVO> vos,
            Map<Long, User> usersById) {
        for (ArticleSearchVO vo : vos) {
            User author = vo.getAuthorId() == null ? null : usersById.get(vo.getAuthorId());
            vo.setAuthor(author == null ? null : AuthorVO.from(author));
        }
        return vos;
    }
```

同时类中需要暂存 authorId 才能回查——在字段区追加：

```java
    /** 作者 id（ES 文档自带，用于批量回查作者；不出现在最终 JSON 之外的场景无需隐藏） */
    private Long authorId;
```

并在 `from` 方法中 `vo.setLikeCount(doc.getLikeCount());` 之后加：

```java
        vo.setAuthorId(doc.getAuthorId());
```

- [ ] **Step 2: ArticleController.search 回查作者**

import 区新增 `com.example.blog.service.UserService`、`com.example.blog.entity.User`、`com.example.blog.vo.AuthorVO`（如尚未有）。

类字段区在 `articleSearchRepository` 之后追加：

```java
    private final UserService userService;
```

`search` 方法体替换为：

```java
    @GetMapping("/search")
    public Result<PageResult<ArticleSearchVO>> search(@Valid ArticleSearchDTO dto) {
        SearchHits<ArticleDocument> hits = articleSearchRepository
                .searchByKeyword(dto.getKeyword(), dto.getPage(), dto.getSize());
        List<ArticleSearchVO> list = hits.getSearchHits().stream()
                .map(ArticleSearchVO::from)
                .toList();
        // 批量回查作者：与列表页相同的防 N+1 模式；
        // 全部作者已被逻辑删除时 authorIds 可能为空集合，listByIds 传空会生成非法 SQL，必须判空
        Set<Long> authorIds = list.stream()
                .map(ArticleSearchVO::getAuthorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = authorIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        ArticleSearchVO.fillAuthors(list, userMap);
        return Result.success(PageResult.of(
                list,
                hits.getTotalHits(),
                dto.getPage().longValue(),
                dto.getSize().longValue()
        ));
    }
```

需要补充 import：`java.util.Collections`、`java.util.Objects`（或直接写 `Objects::nonNull` 并正常 import）、`java.util.Set`、`java.util.stream.Collectors`、`java.util.function.Function`、`java.util.Map`。

- [ ] **Step 3: 后端编译验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0

- [ ] **Step 4: 前端类型同步**

`frontend/src/types/api.ts` 中 `ArticleSearchVO` 接口的 `likeCount: number` 之后追加：

```ts
  authorId?: number | null
  author: AuthorVO | null
```

说明：authorId 标可选（旧 ES 文档重建前可能缺失该字段不影响展示）；author 由后端始终回填（可能为 null）。

- [ ] **Step 5: 前端构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0（列表页模板已用 `item.author?.nickname || '佚名'`，无需改动即显示真实作者）

- [ ] **Step 6: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/vo/ArticleSearchVO.java blog/src/main/java/com/example/blog/controller/ArticleController.java frontend/src/types/api.ts
git commit -m "feat(blog): 搜索结果批量回查并返回作者信息"
```

---

### Task 3: 安全收紧（CORS + 敏感配置环境变量化）

**Files:**
- Modify: `blog/src/main/java/com/example/blog/config/CorsConfig.java`
- Modify: `blog/src/main/resources/application.yml`

**Interfaces:**
- Consumes: 现有 CorsConfig 注册结构。
- Produces: CORS 不再携带凭证；DB 密码/JWT secret 支持环境变量覆盖（开发默认值不变，行为兼容）。

- [ ] **Step 1: CorsConfig 移除 allowCredentials**

`.allowCredentials(true)` 行删除，并把其上方注释：

```java
                // 允许所有来源：携带凭证(allowCredentials=true)时不允许使用 allowedOrigins("*")，
                // 必须用 allowedOriginPatterns("*")
```

替换为：

```java
                // 允许所有来源：本项目 token 走 Authorization 头，浏览器凭证机制
                // （Cookie 等）无用武之地，不开 allowCredentials，收紧放行面
```

- [ ] **Step 2: application.yml 环境变量化**

`password: 123456` 行改为（含上方 TODO 注释一并更新）：

```yaml
    # 生产环境通过 DB_PASSWORD 环境变量注入，冒号后为开发默认值
    password: ${DB_PASSWORD:123456}
```

`secret: 627d...` 一行改为：

```yaml
  # 签名密钥：生产环境通过 JWT_SECRET 环境变量注入（HS512 要求 ≥64 字节），
  # 冒号后为开发默认值
  secret: ${JWT_SECRET:627d361efd9f4feff10aecfe60d3f848773ae6a82660f3eeec0515ad24f2053e9152d3d7b8a7f8f5c285f5c90522c3800642b16cfc80af772a361d018fb35cd4}
```

- [ ] **Step 3: 编译验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0

- [ ] **Step 4: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/config/CorsConfig.java blog/src/main/resources/application.yml
git commit -m "chore(blog): 收紧 CORS 并支持敏感配置环境变量注入"
```

---

### Task 4: 前端 baseURL 环境变量化

**Files:**
- Modify: `frontend/src/utils/request.ts:7`
- Modify: `frontend/env.d.ts`

**Interfaces:**
- Produces: `VITE_API_BASE` 可选环境变量；未设置时行为与现状完全一致。

- [ ] **Step 1: request.ts baseURL**

第 7 行 `baseURL: 'http://localhost:8080/api',` 改为：

```ts
  // 生产部署通过 VITE_API_BASE 指向后端；开发环境缺省直连本地服务
  baseURL: import.meta.env.VITE_API_BASE ?? 'http://localhost:8080/api',
```

- [ ] **Step 2: env.d.ts 类型声明**

整体替换 `frontend/env.d.ts` 为：

```ts
/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 基地址（如 https://api.example.com/api），未设置时用本地默认值 */
  readonly VITE_API_BASE?: string
}
```

- [ ] **Step 3: 构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0

- [ ] **Step 4: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/utils/request.ts frontend/env.d.ts
git commit -m "refactor(frontend): API 基地址支持环境变量配置"
```

---

### Task 5: 列表页加载去重（消除双重请求）

**Files:**
- Modify: `frontend/src/views/ArticleList.vue`

**Interfaces:**
- Consumes: 现有 `load()` / `resetAndLoad()` 结构。
- Produces: watch 驱动的统一加载入口 + 参数快照去重；对外行为不变（排序/关键词变更回到第一页）。

- [ ] **Step 1: 重构 script 触发逻辑**

script 中从 `const keyword = ref(...)` 之后到 `onMounted(load)` 之前的部分替换为：

```ts
/** 关键词非空走 ES 搜索接口，否则走普通分页接口 */
async function load() {
  const params = JSON.stringify({
    kw: keyword.value.trim(),
    page: pageNum.value,
    size: pageSize.value,
    sort: sort.value,
  })
  // 参数快照去重：pageSize 变化引发的 current-change + size-change 连发、
  // 以及 watch 对同批变化的重复触发，都收敛为一次网络请求
  if (params === lastParams.value) return
  loading.value = true
  try {
    const kw = keyword.value.trim()
    const page = kw
      ? await searchArticles({ keyword: kw, page: pageNum.value, size: pageSize.value })
      : await getArticlePage({ pageNum: pageNum.value, pageSize: pageSize.value, sort: sort.value })
    lastParams.value = params
    list.value = page.list as ListItem[]
    total.value = Number(page.total)
  } catch {
    // 错误已由响应拦截器提示
  } finally {
    loading.value = false
  }
}

const lastParams = ref('')

/** 条件变化（换排序 / 换每页大小）从第一页重新加载：只改 pageNum，由 watch 统一触发 */
function resetAndLoad() {
  pageNum.value = 1
}

// 用户停留在列表页时再次通过顶部导航搜索：响应 query 变化
watch(
  () => route.query.keyword,
  (kw) => {
    keyword.value = (kw as string) ?? ''
    resetAndLoad()
  },
)

// 任一查询条件变化统一走 load()；参数快照保证同批变化只发一次请求
watch([pageNum, pageSize, () => sort.value], load)

onMounted(load)
```

说明：`lastParams` 声明在 `load` 之后仍安全——`load` 首次被调用发生在 `onMounted`（setup 同步段之后），届时 `lastParams` 已初始化。pageSize 显式纳入 watch：非第一页改每页大小时 el-pagination 连带复位 pageNum，Vue 的 watch 会把同批变化合并为一次回调；第一页单独改 pageSize 也被覆盖。

- [ ] **Step 2: 模板移除事件回调**

`<el-radio-group v-model="sort" @change="resetAndLoad">` 去掉 `@change="resetAndLoad"`；`<el-pagination ...>` 去掉 `@current-change="load"` 与 `@size-change="resetAndLoad"` 两行。

- [ ] **Step 3: 构建验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build`
Expected: 退出码 0

- [ ] **Step 4: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/views/ArticleList.vue
git commit -m "fix(frontend): 列表页查询条件变化合并为单次请求"
```

---

### Task 6: rejection 清理 + 评论计数 + 编辑页路由响应

**Files:**
- Modify: `frontend/src/views/Login.vue`
- Modify: `frontend/src/views/Register.vue`
- Modify: `frontend/src/views/ArticleDetail.vue`
- Modify: `frontend/src/views/Profile.vue`
- Modify: `frontend/src/views/ArticleEdit.vue`

**Interfaces:**
- Consumes: 各页面现有函数结构。
- Produces: 全部 async 入口无未处理 rejection；评论计数含子评论；编辑页 computed articleId。

- [ ] **Step 1: 三个表单页 validate() 防护**

`Login.vue` / `Register.vue` / `ArticleEdit.vue` 的 `await formRef.value?.validate()` 行统一改为：

```ts
  // validate() 在校验失败时 reject：捕获并早退，字段错误提示由表单自行展示
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
```

- [ ] **Step 2: 详情页与个人中心删除确认取消静默**

`ArticleDetail.vue` `onDelete` 改为：

```ts
async function onDelete() {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确定删除该文章吗？', '删除确认', { type: 'warning' })
  } catch {
    return // 用户取消确认：静默返回
  }
  await deleteArticle(articleId)
  ElMessage.success('删除成功')
  await router.push('/articles')
}
```

`Profile.vue` 的 `onDeleteArticle` 同型修改：

```ts
async function onDeleteArticle(article: ArticleVO) {
  try {
    await ElMessageBox.confirm(`确定删除《${article.title}》吗？`, '删除确认', { type: 'warning' })
  } catch {
    return // 用户取消确认：静默返回
  }
  await deleteArticle(article.id)
  ElMessage.success('删除成功')
  await loadMyArticles()
}
```

- [ ] **Step 3: 详情页点赞/收藏 toggle 容错**

`onToggleLike` 与 `onToggleFavorite` 的接口调用包 try/catch，失败时本地状态不动：

```ts
async function onToggleLike() {
  if (!requireLogin() || !article.value) return
  try {
    if (liked.value) {
      await unlikeArticle(articleId)
      liked.value = false
      article.value.likeCount -= 1
    } else {
      await likeArticle(articleId)
      liked.value = true
      article.value.likeCount += 1
    }
  } catch {
    // 失败时保持原状态（提示已由拦截器弹出）
  }
}

async function onToggleFavorite() {
  if (!requireLogin()) return
  try {
    if (favorited.value) {
      await unfavoriteArticle(articleId)
      favorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteArticle(articleId)
      favorited.value = true
      ElMessage.success('收藏成功')
    }
  } catch {
    // 失败时保持原状态
  }
}
```

- [ ] **Step 4: 评论计数含子评论**

`ArticleDetail.vue` script 中（`resolveParentId` 之前）追加：

```ts
/** 递归统计评论总数（含各级子评论） */
function countComments(list: CommentVO[]): number {
  return list.reduce((sum, c) => sum + 1 + countComments(c.children), 0)
}
```

模板中 `<h3 class="comments-title">评论（{{ comments.length }}）</h3>` 改为：

```html
<h3 class="comments-title">评论（{{ countComments(comments) }}）</h3>
```

- [ ] **Step 5: 编辑页响应路由参数**

`ArticleEdit.vue`：

`const articleId = route.params.id as string | undefined` 改为：

```ts
/** 有 id 即编辑模式；computed 使 /publish ↔ /publish/:id 互跳时组件复用也能正确切换 */
const articleId = computed(() =>
  route.params.id ? (route.params.id as string) : undefined,
)
```

import 行补入 `watch`：`import { computed, onMounted, reactive, ref, watch } from 'vue'`。

`loadForEdit` 开头判空不变；编辑→新建切换时清空表单。`onMounted(loadForEdit)` 替换为：

```ts
watch(
  articleId,
  (id, oldId) => {
    if (!id) {
      // 进入新建模式：清空上一次编辑残留
      form.title = ''
      form.category = ''
      form.tags = ''
      form.content = ''
    } else if (id !== oldId) {
      loadForEdit()
    }
  },
  { immediate: true },
)
```

同时删除原来的 `onMounted(loadForEdit)` 行。注意：`loadForEdit` 内部 `const article = await getArticleEditData(articleId)` 处的 `articleId` 现在是 computed ref，需改为 `articleId.value`（判空行同理 `if (!articleId.value) return`）。

- [ ] **Step 6: 构建与 lint 验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build && npm run lint`
Expected: 两步均退出码 0

- [ ] **Step 7: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/views/Login.vue frontend/src/views/Register.vue frontend/src/views/ArticleDetail.vue frontend/src/views/Profile.vue frontend/src/views/ArticleEdit.vue
git commit -m "fix(frontend): 清理未处理的 promise rejection 并完善评论计数与编辑页路由响应"
```

## Self-Review 记录

- 规格覆盖：Part A 十项 → A1+A2=Task 1、A3=Task 2、A4=Task 3、A5=Task 4、A6=Task 5、A7+A8+A9=Task 6、A10 并入 A7（spec 已注明无需单独改动）。无遗漏。
- 撰写中发现的 @Cacheable 类内直调失效问题已在 Task 1 就地解决：权限校验放 Controller 层，`getArticleById` 经代理调用，缓存生效；SecurityConfig 无需改动（两段路径不被单段通配误放行）。
- 类型一致性：`getArticleEditData(id)` 前后端签名一致；`fillAuthors(vos, usersById)` 与 Controller 调用一致；`countComments(comments: CommentVO[])` 与模板用法一致；`lastParams` 声明时序安全（首次调用晚于 setup 同步段）。
- 占位符扫描：无 TBD/TODO/作废块残留，所有代码块均为最终实现。
