# 博客平台前端生命周期与评论分页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复文章详情页切换复用旧数据的问题，并将评论接口改为分页加载。

**Architecture:** 前端监听路由文章 ID 并清理旧状态，评论接口返回一级评论分页和当前页子回复。后端负责分页查询，前端负责分页状态和加载更多。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、JUnit 5、Mockito、Vue 3、TypeScript、Vitest

---

## 文件结构

**创建：**

- `blog/src/main/java/com/example/blog/dto/CommentPageDTO.java`：评论分页参数。
- `blog/src/main/java/com/example/blog/vo/CommentPageVO.java`：一级评论分页结果和总数。
- `blog/src/test/java/com/example/blog/service/impl/CommentPagingServiceImplTest.java`：验证一级评论分页和子回复加载。
- `frontend/src/views/ArticleDetail.test.ts`：验证路由 ID 切换和旧请求保护。

**修改：**

- `blog/src/main/java/com/example/blog/service/CommentService.java`：分页方法。
- `blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java`：分页查询和子回复批量查询。
- `blog/src/main/java/com/example/blog/controller/CommentController.java`：分页接口。
- `blog/src/main/resources/sql/init.sql`：评论分页索引。
- `frontend/src/api/comment.ts`：分页请求类型。
- `frontend/src/types/api.ts`：评论分页类型。
- `frontend/src/views/ArticleDetail.vue`：监听文章 ID、旧请求保护、评论分页。
- `frontend/index.html`：页面语言和标题。

**删除：**

- 无。

---

### Task 1: 评论分页 DTO 和返回结构

**Files:**
- Create: `blog/src/main/java/com/example/blog/dto/CommentPageDTO.java`
- Create: `blog/src/main/java/com/example/blog/vo/CommentPageVO.java`
- Modify: `blog/src/main/java/com/example/blog/service/CommentService.java`
- Modify: `blog/src/main/java/com/example/blog/controller/CommentController.java`

- [ ] **Step 1: 创建分页 DTO**

创建 `blog/src/main/java/com/example/blog/dto/CommentPageDTO.java`：

```java
package com.example.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentPageDTO {

    @NotNull(message = "文章 id 不能为空")
    private Long articleId;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小最小为 1")
    @Max(value = 100, message = "每页大小最大为 100")
    private Integer pageSize = 20;
}
```

- [ ] **Step 2: 创建分页 VO**

创建 `blog/src/main/java/com/example/blog/vo/CommentPageVO.java`：

```java
package com.example.blog.vo;

import lombok.Data;

import java.util.List;

@Data
public class CommentPageVO {

    private Long total;

    private Long pageNum;

    private Long pageSize;

    private List<CommentVO> list;
}
```

- [ ] **Step 3: 更新接口契约**

在 `CommentService` 增加：

```java
CommentPageVO pageComments(CommentPageDTO dto);
```

保留现有 `getCommentTree` 供详情页过渡使用，后续 Task 3 切换。

在 `CommentController` 增加：

```java
@GetMapping("/page")
public Result<CommentPageVO> page(@Valid CommentPageDTO dto) {
    return Result.success(commentService.pageComments(dto));
}
```

- [ ] **Step 4: 编译验证**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -DskipTests compile
```

Expected: 编译失败，`pageComments` 尚未实现。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/dto/CommentPageDTO.java blog/src/main/java/com/example/blog/vo/CommentPageVO.java blog/src/main/java/com/example/blog/service/CommentService.java blog/src/main/java/com/example/blog/controller/CommentController.java
git commit -m "feat(comment): 增加评论分页接口契约"
```

---

### Task 2: 一级评论分页和子回复批量加载

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java`
- Test: `blog/src/test/java/com/example/blog/service/impl/CommentPagingServiceImplTest.java`

- [ ] **Step 1: 写分页失败测试**

创建 `blog/src/test/java/com/example/blog/service/impl/CommentPagingServiceImplTest.java`：

```java
package com.example.blog.service.impl;

import com.example.blog.dto.CommentPageDTO;
import com.example.blog.entity.Article;
import com.example.blog.entity.Comment;
import com.example.blog.mapper.CommentMapper;
import com.example.blog.service.ArticleService;
import com.example.blog.service.UserService;
import com.example.blog.vo.CommentPageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentPagingServiceImplTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private UserService userService;

    @Mock
    private CommentMapper commentMapper;

    @InjectMocks
    private CommentServiceImpl service;

    @Test
    void pageCommentsReturnsOnlyRootComments() {
        Article article = new Article();
        article.setId(7L);
        article.setStatus(1);
        article.setHidden(0);
        when(articleService.getById(7L)).thenReturn(article);
        ReflectionTestUtils.setField(service, "baseMapper", commentMapper);

        CommentPageDTO dto = new CommentPageDTO();
        dto.setArticleId(7L);
        dto.setPageNum(1);
        dto.setPageSize(20);

        CommentPageVO result = service.pageComments(dto);

        assertEquals(0L, result.getTotal());
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=CommentPagingServiceImplTest test
```

Expected: FAIL，`pageComments` 尚未实现。

- [ ] **Step 3: 实现分页**

在 `CommentServiceImpl` 增加所需 imports：

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.blog.dto.CommentPageDTO;
import com.example.blog.vo.CommentPageVO;
```

增加实现：

```java
@Override
@Transactional(readOnly = true)
public CommentPageVO pageComments(CommentPageDTO dto) {
    requireVisibleArticle(dto.getArticleId());
    Page<Comment> page = page(
            new Page<>(dto.getPageNum(), dto.getPageSize()),
            new LambdaQueryWrapper<Comment>()
                    .eq(Comment::getArticleId, dto.getArticleId())
                    .eq(Comment::getParentId, 0L)
                    .orderByAsc(Comment::getCreateTime)
    );
    List<Comment> roots = page.getRecords();
    List<Comment> all = new ArrayList<>(roots);
    if (!roots.isEmpty()) {
        List<Long> rootIds = roots.stream().map(Comment::getId).toList();
        all.addAll(lambdaQuery()
                .in(Comment::getParentId, rootIds)
                .orderByAsc(Comment::getCreateTime)
                .list());
    }
    Set<Long> userIds = new HashSet<>();
    all.forEach(comment -> {
        userIds.add(comment.getUserId());
        if (comment.getReplyToUserId() != null && comment.getReplyToUserId() != 0) {
            userIds.add(comment.getReplyToUserId());
        }
    });
    Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
            : userService.listByIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
    List<CommentVO> vos = all.stream()
            .map(comment -> CommentVO.from(comment, userMap.get(comment.getUserId())))
            .toList();
    fillReplyToNicknames(vos, userMap);
    List<CommentVO> tree = buildTree(vos);
    CommentPageVO vo = new CommentPageVO();
    vo.setTotal(page.getTotal());
    vo.setPageNum(page.getCurrent());
    vo.setPageSize(page.getSize());
    vo.setList(tree);
    return vo;
}
```

若 `requireVisibleArticle` 尚不存在，则复用 `getCommentTree` 中现有的文章存在性和可见性判断，
抽成同名私有方法，避免分页接口绕过权限。

需要补充：

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=CommentPagingServiceImplTest test
```

Expected: PASS。

- [ ] **Step 5: 增加数据库索引**

将 `init.sql` 的评论索引改为：

```sql
  KEY `idx_article_parent_time` (`article_id`, `parent_id`, `create_time`),
  KEY `idx_user` (`user_id`)
```

- [ ] **Step 6: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java blog/src/main/resources/sql/init.sql blog/src/test/java/com/example/blog/service/impl/CommentPagingServiceImplTest.java
git commit -m "feat(comment): 分页加载一级评论"
```

---

### Task 3: 前端评论分页 API 和类型

**Files:**
- Modify: `frontend/src/api/comment.ts`
- Modify: `frontend/src/types/api.ts`

- [ ] **Step 1: 更新类型**

在 `frontend/src/types/api.ts` 增加：

```typescript
export interface CommentPageVO {
  total: number
  pageNum: number
  pageSize: number
  list: CommentVO[]
}
```

- [ ] **Step 2: 更新 API**

将 `frontend/src/api/comment.ts` 改为：

```typescript
import request from '@/utils/request'
import type { CommentPageVO, CommentVO } from '@/types/api'

export function getCommentPage(params: {
  articleId: number | string
  pageNum: number
  pageSize: number
}) {
  return request.get<never, CommentPageVO>('/comments/page', { params })
}

export function addComment(data: { articleId: number | string; replyToId?: number; content: string }) {
  return request.post<never, CommentVO>('/comments', data)
}

export function deleteComment(id: number | string) {
  return request.delete<never, null>(`/comments/${id}`)
}
```

- [ ] **Step 3: 类型检查**

Run:

```powershell
cd frontend
npm run type-check
```

Expected: FAIL，`ArticleDetail.vue` 仍导入旧 API。

- [ ] **Step 4: 提交**

```powershell
git add -- frontend/src/api/comment.ts frontend/src/types/api.ts
git commit -m "feat(frontend): 添加评论分页类型和接口"
```

---

### Task 4: 文章详情路由生命周期和评论分页

**Files:**
- Modify: `frontend/src/views/ArticleDetail.vue`

- [ ] **Step 1: 改造文章 ID**

将：

```typescript
const articleId = route.params.id as string
```

改为：

```typescript
const articleId = computed(() => String(route.params.id ?? ''))
```

- [ ] **Step 2: 增加请求序号**

加入：

```typescript
let articleRequestSeq = 0
```

在 `loadArticle` 开始处：

```typescript
const requestSeq = ++articleRequestSeq
```

拿到数据后：

```typescript
if (requestSeq !== articleRequestSeq) return
```

- [ ] **Step 3: 重置状态并监听路由**

增加：

```typescript
function resetArticleState() {
  article.value = null
  liked.value = false
  favorited.value = false
  comments.value = []
  commentTotal.value = 0
  commentPage.value = 1
  replyTo.value = null
}

watch(articleId, () => {
  resetArticleState()
  window.scrollTo({ top: 0, behavior: 'smooth' })
  loadArticle()
  loadComments()
  loadInteractionStatus()
})
```

首次 `onMounted` 继续加载一次，避免重复监听立即触发。

- [ ] **Step 4: 接入评论分页**

引入：

```typescript
import { getCommentPage } from '@/api/comment'
```

增加：

```typescript
const commentPage = ref(1)
const commentPageSize = 20
const commentTotal = ref(0)
```

将 `loadComments` 改为：

```typescript
async function loadComments() {
  const page = await getCommentPage({
    articleId: articleId.value,
    pageNum: commentPage.value,
    pageSize: commentPageSize,
  })
  comments.value = page.list
  commentTotal.value = Number(page.total)
}
```

- [ ] **Step 5: 更新模板**

将评论计数改为 `commentTotal`，并在评论列表后加入：

```vue
<el-pagination
  v-if="commentTotal > commentPageSize"
  v-model:current-page="commentPage"
  :page-size="commentPageSize"
  :total="commentTotal"
  layout="prev, pager, next"
  @current-change="loadComments"
/>
```

- [ ] **Step 6: 前端测试**

Run:

```powershell
cd frontend
npm test
npm run type-check
```

Expected: PASS。

- [ ] **Step 7: 提交**

```powershell
git add -- frontend/src/views/ArticleDetail.vue
git commit -m "fix(frontend): 切换文章时重置详情状态"
```

---

### Task 5: 页面元信息和批次验证

**Files:**
- Modify: `frontend/index.html`
- Modify: `docs/superpowers/plans/2026-09-18-blog-platform-stabilization-phase-4.md`

- [ ] **Step 1: 更新页面元信息**

将 `frontend/index.html` 改为：

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8">
    <link rel="icon" href="/favicon.ico">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>博客平台</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 2: 运行完整验证**

Run:

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

Expected: PASS。

- [ ] **Step 3: 验证评论索引**

Run:

```powershell
$env:MYSQL_PORT='43306'
$env:JWT_SECRET='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
$dockerCli='C:\Users\vannnn\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
& $dockerCli compose -f docker-compose.yml -f docker-compose.init-test.yml -p blog-comment-test up -d --wait mysql
& $dockerCli exec blog-mysql-init-test mysql -uroot -pblog_dev_pwd -e "SHOW INDEX FROM blog.comment;"
& $dockerCli compose -f docker-compose.yml -f docker-compose.init-test.yml -p blog-comment-test down -v
```

Expected: 输出包含 `idx_article_parent_time`。

- [ ] **Step 4: 勾选验收项并提交**

```powershell
git add -- frontend/index.html docs/superpowers/plans/2026-09-18-blog-platform-stabilization-phase-4.md
git commit -m "docs: 完成前端生命周期与评论分页验证"
```

---

## 批次验收清单

- [ ] 评论接口支持一级评论分页。
- [ ] 当前页子评论批量加载。
- [ ] 文章详情监听路由 ID。
- [ ] 旧请求迟到不会覆盖新文章。
- [ ] 评论分页状态正确。
- [ ] 页面语言为 `zh-CN`。
- [ ] 页面标题为实际站点名称。
- [ ] 后端测试、前端测试和静态检查全部通过。
