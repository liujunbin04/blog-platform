# 博客平台后端结构收敛 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除文章可见性、作者回填、ES 同步和搜索编排的重复实现，并移除旧评论整树路径。

**Architecture:** 新增四个边界清晰的服务组件：可见性策略、作者装配器、ES 同步服务和搜索查询服务。现有接口保持不变，只删除已被评论分页替代的旧评论读取接口。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis-Plus、Spring Data Elasticsearch、JUnit 5、Mockito、Vue 3、TypeScript

---

## 文件结构

**创建：**

- `blog/src/main/java/com/example/blog/service/ArticleVisibilityPolicy.java`：文章公开性和作者可见性规则。
- `blog/src/main/java/com/example/blog/service/AuthorAssembler.java`：用户批量查询和作者 VO 转换。
- `blog/src/main/java/com/example/blog/service/ArticleSearchSyncService.java`：Elasticsearch 同步契约。
- `blog/src/main/java/com/example/blog/service/impl/ArticleSearchSyncServiceImpl.java`：ES 文档构建、同步、删除和对账。
- `blog/src/main/java/com/example/blog/service/ArticleSearchQueryService.java`：搜索查询契约。
- `blog/src/main/java/com/example/blog/service/impl/ArticleSearchQueryServiceImpl.java`：ES 查询、可见性过滤和作者回填。
- `blog/src/test/java/com/example/blog/service/ArticleVisibilityPolicyTest.java`
- `blog/src/test/java/com/example/blog/service/AuthorAssemblerTest.java`
- `blog/src/test/java/com/example/blog/service/impl/ArticleSearchSyncServiceImplTest.java`
- `blog/src/test/java/com/example/blog/service/impl/ArticleSearchQueryServiceImplTest.java`

**修改：**

- `blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java`
- `blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java`
- `blog/src/main/java/com/example/blog/service/impl/HotArticleServiceImpl.java`
- `blog/src/main/java/com/example/blog/service/impl/FavoriteServiceImpl.java`
- `blog/src/main/java/com/example/blog/service/impl/NotificationServiceImpl.java`
- `blog/src/main/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImpl.java`
- `blog/src/main/java/com/example/blog/task/ArticleSyncTask.java`
- `blog/src/main/java/com/example/blog/controller/ArticleController.java`
- `blog/src/main/java/com/example/blog/controller/CommentController.java`
- `blog/src/main/java/com/example/blog/service/CommentService.java`
- `frontend/src/api/comment.ts`

**删除：**

- `CommentService.getCommentTree`
- `CommentServiceImpl.getCommentTree`
- `CommentController.GET /api/comments`
- `frontend.getCommentTree`

---

### Task 1: 统一文章可见性规则

**Files:**
- Create: `blog/src/main/java/com/example/blog/service/ArticleVisibilityPolicy.java`
- Create: `blog/src/test/java/com/example/blog/service/ArticleVisibilityPolicyTest.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/task/ArticleSyncTask.java`

- [ ] **Step 1: 写可见性策略失败测试**

创建 `blog/src/test/java/com/example/blog/service/ArticleVisibilityPolicyTest.java`：

```java
package com.example.blog.service;

import com.example.blog.entity.Article;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleVisibilityPolicyTest {

    private final ArticleVisibilityPolicy policy = new ArticleVisibilityPolicy();

    @Test
    void publishedVisibleArticleIsPublic() {
        Article article = article(1, 0);

        assertTrue(policy.isPublic(article));
    }

    @Test
    void draftOrHiddenArticleIsNotPublic() {
        assertFalse(policy.isPublic(article(0, 0)));
        assertFalse(policy.isPublic(article(1, 1)));
        assertFalse(policy.isPublic(article(null, 0)));
    }

    @Test
    void privateArticleIsVisibleToAuthorOnly() {
        Article article = article(1, 1);
        article.setAuthorId(9L);

        assertTrue(policy.isVisibleTo(article, 9L));
        assertFalse(policy.isVisibleTo(article, 10L));
        assertFalse(policy.isVisibleTo(article, null));
    }

    private Article article(Integer status, Integer hidden) {
        Article article = new Article();
        article.setStatus(status);
        article.setHidden(hidden);
        return article;
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleVisibilityPolicyTest test
```

Expected: FAIL，`ArticleVisibilityPolicy` 尚不存在。

- [ ] **Step 3: 实现策略组件**

创建 `blog/src/main/java/com/example/blog/service/ArticleVisibilityPolicy.java`：

```java
package com.example.blog.service;

import com.example.blog.entity.Article;
import org.springframework.stereotype.Component;

@Component
public class ArticleVisibilityPolicy {

    public boolean isDraft(Article article) {
        return article == null || article.getStatus() == null || article.getStatus() == 0;
    }

    public boolean isHidden(Article article) {
        return article == null || (article.getHidden() != null && article.getHidden() == 1);
    }

    public boolean isPublic(Article article) {
        return article != null
                && article.getStatus() != null
                && article.getStatus() == 1
                && (article.getHidden() == null || article.getHidden() == 0);
    }

    public boolean isVisibleTo(Article article, Long userId) {
        return isPublic(article)
                || (article != null && userId != null && userId.equals(article.getAuthorId()));
    }
}
```

- [ ] **Step 4: 替换文章服务中的重复实现**

在 `ArticleServiceImpl` 注入：

```java
private final ArticleVisibilityPolicy visibilityPolicy;
```

将 `publish`、`updateArticle`、`requirePublicArticle` 中的 `isPublic(saved)` 或
`isPublic(article)` 改为 `visibilityPolicy.isPublic(...)`，并删除私有
`isPublic(Article)` 方法。

将 `requirePublicArticle` 中的缺失与公开判断改为：

```java
if (article == null || !visibilityPolicy.isPublic(article)) {
    throw new BusinessException(404, "文章不存在");
}
```

- [ ] **Step 5: 替换评论、搜索可见性和同步任务中的判断**

在 `CommentServiceImpl` 注入 `ArticleVisibilityPolicy`，将 `requireVisibleArticle` 中
`restricted` 计算改为：

```java
boolean restricted = !visibilityPolicy.isPublic(article);
```

在 `ArticleSearchVisibilityServiceImpl` 注入同一策略，删除私有 `isPublic`，过滤条件改为：

```java
.filter(hit -> visibilityPolicy.isPublic(byId.get(hit.getId())))
```

在 `ArticleSyncTask` 注入同一策略，删除私有 `isPublic`，对账条件改为：

```java
if (!visibilityPolicy.isPublic(article)) {
    articleSearchRepository.deleteById(document.getId());
}
```

- [ ] **Step 6: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleVisibilityPolicyTest,ArticleServiceImplTest,CommentServiceImplTest,ArticleSearchVisibilityServiceImplTest,ArticleSyncTaskTest test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/ArticleVisibilityPolicy.java blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java blog/src/main/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImpl.java blog/src/main/java/com/example/blog/task/ArticleSyncTask.java blog/src/test/java/com/example/blog/service/ArticleVisibilityPolicyTest.java
git commit -m "refactor: 统一文章可见性规则"
```

---

### Task 2: 统一作者信息回填

**Files:**
- Create: `blog/src/main/java/com/example/blog/service/AuthorAssembler.java`
- Create: `blog/src/test/java/com/example/blog/service/AuthorAssemblerTest.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/HotArticleServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/FavoriteServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/NotificationServiceImpl.java`

- [ ] **Step 1: 写作者装配失败测试**

创建 `blog/src/test/java/com/example/blog/service/AuthorAssemblerTest.java`：

```java
package com.example.blog.service;

import com.example.blog.entity.User;
import com.example.blog.vo.AuthorVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthorAssemblerTest {

    private final UserService userService = mock(UserService.class);
    private final AuthorAssembler assembler = new AuthorAssembler(userService);

    @Test
    void emptyIdsReturnEmptyMapWithoutQuery() {
        assertTrue(assembler.loadUsers(List.of()).isEmpty());
        verifyNoInteractions(userService);
    }

    @Test
    void loadsAuthorsByUserId() {
        User user = new User();
        user.setId(3L);
        user.setNickname("tester");
        when(userService.listByIds(List.of(3L))).thenReturn(List.of(user));

        Map<Long, AuthorVO> authors = assembler.loadAuthors(List.of(3L));

        assertEquals("tester", authors.get(3L).getNickname());
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=AuthorAssemblerTest test
```

Expected: FAIL，`AuthorAssembler` 尚不存在。

- [ ] **Step 3: 实现作者装配器**

创建 `blog/src/main/java/com/example/blog/service/AuthorAssembler.java`：

```java
package com.example.blog.service;

import com.example.blog.entity.User;
import com.example.blog.vo.AuthorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuthorAssembler {

    private final UserService userService;

    public Map<Long, User> loadUsers(Collection<Long> userIds) {
        Set<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    public AuthorVO toAuthor(User user) {
        return user == null ? null : AuthorVO.from(user);
    }

    public Map<Long, AuthorVO> loadAuthors(Collection<Long> userIds) {
        return loadUsers(userIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toAuthor(entry.getValue())));
    }
}
```

- [ ] **Step 4: 替换文章列表和热门列表**

在 `ArticleServiceImpl` 注入 `AuthorAssembler`，将 `pageArticles` 的作者查询替换为：

```java
Map<Long, User> authorMap = authorAssembler.loadUsers(
        page.getRecords().stream().map(Article::getAuthorId).toList()
);
```

删除 `Collections.emptyMap()` 和 `userService.listByIds(...)` 的重复分支。

在 `HotArticleServiceImpl` 用同样方式替换作者查询，并保留只有存在的作者才放入 Map 的行为。

- [ ] **Step 5: 替换收藏、评论和通知**

在 `FavoriteServiceImpl` 注入 `AuthorAssembler`，将作者 ID 查询替换为：

```java
Map<Long, User> userMap = authorAssembler.loadUsers(
        articleMap.values().stream().map(Article::getAuthorId).toList()
);
```

在 `CommentServiceImpl` 注入 `AuthorAssembler`，将 `userService.listByIds(userIds)` 替换为：

```java
Map<Long, User> userMap = authorAssembler.loadUsers(userIds);
```

在 `NotificationServiceImpl` 注入 `AuthorAssembler`，将发件人查询替换为：

```java
Map<Long, User> senderMap = authorAssembler.loadUsers(
        records.stream().map(Notification::getSenderId).toList()
);
```

- [ ] **Step 6: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=AuthorAssemblerTest,ArticleServiceImplTest,HotArticleServiceImplTest,CommentPagingServiceImplTest test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/AuthorAssembler.java blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java blog/src/main/java/com/example/blog/service/impl/HotArticleServiceImpl.java blog/src/main/java/com/example/blog/service/impl/FavoriteServiceImpl.java blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java blog/src/main/java/com/example/blog/service/impl/NotificationServiceImpl.java blog/src/test/java/com/example/blog/service/AuthorAssemblerTest.java
git commit -m "refactor: 统一作者信息回填"
```

---

### Task 3: 收口 Elasticsearch 同步

**Files:**
- Create: `blog/src/main/java/com/example/blog/service/ArticleSearchSyncService.java`
- Create: `blog/src/main/java/com/example/blog/service/impl/ArticleSearchSyncServiceImpl.java`
- Create: `blog/src/test/java/com/example/blog/service/impl/ArticleSearchSyncServiceImplTest.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/task/ArticleSyncTask.java`

- [ ] **Step 1: 写 ES 同步服务失败测试**

创建 `blog/src/test/java/com/example/blog/service/impl/ArticleSearchSyncServiceImplTest.java`：

```java
package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.AuthorAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSearchSyncServiceImplTest {

    @Mock
    private ArticleSearchRepository repository;

    @Mock
    private AuthorAssembler authorAssembler;

    @InjectMocks
    private ArticleSearchSyncServiceImpl service;

    @Test
    void syncAllIncludesAuthorNickname() {
        Article article = article();
        com.example.blog.entity.User author = new com.example.blog.entity.User();
        author.setId(3L);
        author.setNickname("tester");
        when(authorAssembler.loadUsers(List.of(3L))).thenReturn(Map.of(3L, author));

        service.syncAll(List.of(article));

        verify(repository).saveAll(argThat(documents ->
                documents.iterator().hasNext()
                        && "tester".equals(documents.iterator().next().getAuthorNickname())
        ));
    }

    private Article article() {
        Article article = new Article();
        article.setId(9L);
        article.setAuthorId(3L);
        article.setTitle("title");
        article.setContent("content");
        article.setViewCount(0);
        article.setLikeCount(0);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        return article;
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSearchSyncServiceImplTest test
```

Expected: FAIL，`ArticleSearchSyncService` 尚不存在。

- [ ] **Step 3: 实现同步服务和批量构建**

创建 `ArticleSearchSyncService`：

```java
package com.example.blog.service;

import com.example.blog.entity.Article;

import java.util.List;

public interface ArticleSearchSyncService {

    void sync(Article article);

    void remove(Long articleId);

    void syncAll(List<Article> articles);

    void reconcileVisibility();
}
```

创建 `ArticleSearchSyncServiceImpl`，先补齐 imports：

```java
package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.entity.User;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleSearchSyncService;
import com.example.blog.service.ArticleService;
import com.example.blog.service.ArticleVisibilityPolicy;
import com.example.blog.service.AuthorAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
```

类声明和注入：

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSearchSyncServiceImpl implements ArticleSearchSyncService {

    private final ArticleSearchRepository articleSearchRepository;
    private final AuthorAssembler authorAssembler;
    private final ArticleService articleService;
    private final ArticleVisibilityPolicy visibilityPolicy;
```

方法实现：

```java
@Override
public void sync(Article article) {
    try {
        articleSearchRepository.save(toDocument(article, authorAssembler.loadUsers(List.of(article.getAuthorId()))));
    } catch (Exception e) {
        log.error("文章同步到 ES 失败: articleId={}", article.getId(), e);
    }
}

@Override
public void remove(Long articleId) {
    try {
        articleSearchRepository.deleteById(articleId);
    } catch (Exception e) {
        log.error("删除 ES 文档失败: articleId={}", articleId, e);
    }
}

@Override
public void syncAll(List<Article> articles) {
    if (articles.isEmpty()) {
        return;
    }
    Map<Long, User> authors = authorAssembler.loadUsers(
            articles.stream().map(Article::getAuthorId).toList()
    );
    articleSearchRepository.saveAll(articles.stream()
            .map(article -> toDocument(article, authors))
            .toList());
}

@Override
public void reconcileVisibility() {
    articleSearchRepository.findAll().forEach(document -> {
        Article article = articleService.getById(document.getId());
        if (!visibilityPolicy.isPublic(article)) {
            articleSearchRepository.deleteById(document.getId());
        }
    });
}

private ArticleDocument toDocument(Article article, Map<Long, User> authors) {
    User author = authors.get(article.getAuthorId());
    return ArticleDocument.from(article, author == null ? null : author.getNickname());
}
```

保持异常语义：`sync`、`remove` 和 `reconcileVisibility` 内部记录错误；
`syncAll` 允许调用方决定是否捕获。

- [ ] **Step 4: 替换文章服务中的 ES 操作**

在 `ArticleServiceImpl` 将 `ArticleSearchRepository` 替换为：

```java
private final ArticleSearchSyncService articleSearchSyncService;
```

将 `indexToEsQuietly(saved)` 替换为 `articleSearchSyncService.sync(saved)`。
将 `deleteFromEsQuietly(id)` 替换为 `articleSearchSyncService.remove(id)`。
删除两个私有 ES helper。

- [ ] **Step 5: 简化同步任务**

在 `ArticleSyncTask` 注入 `ArticleSearchSyncService`，删除 `UserService`、
`ArticleSearchRepository` 和 `toDocuments`。

启动回填改为：

```java
articleSearchSyncService.reconcileVisibility();
```

和：

```java
articleSearchSyncService.syncAll(articles);
```

增量同步改为：

```java
articleSearchSyncService.syncAll(articles);
```

- [ ] **Step 6: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSearchSyncServiceImplTest,ArticleSyncTaskTest,ArticleServiceImplTest test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/ArticleSearchSyncService.java blog/src/main/java/com/example/blog/service/impl/ArticleSearchSyncServiceImpl.java blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java blog/src/main/java/com/example/blog/task/ArticleSyncTask.java blog/src/test/java/com/example/blog/service/impl/ArticleSearchSyncServiceImplTest.java blog/src/test/java/com/example/blog/task/ArticleSyncTaskTest.java
git commit -m "refactor: 收口搜索索引同步"
```

---

### Task 4: 搜索编排移出 Controller

**Files:**
- Create: `blog/src/main/java/com/example/blog/service/ArticleSearchQueryService.java`
- Create: `blog/src/main/java/com/example/blog/service/impl/ArticleSearchQueryServiceImpl.java`
- Create: `blog/src/test/java/com/example/blog/service/impl/ArticleSearchQueryServiceImplTest.java`
- Modify: `blog/src/main/java/com/example/blog/controller/ArticleController.java`

- [ ] **Step 1: 写搜索查询服务失败测试**

创建 `blog/src/test/java/com/example/blog/service/impl/ArticleSearchQueryServiceImplTest.java`：

```java
package com.example.blog.service.impl;

import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleSearchDTO;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleSearchVisibilityService;
import com.example.blog.service.AuthorAssembler;
import com.example.blog.vo.ArticleSearchVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSearchQueryServiceImplTest {

    @Mock
    private ArticleSearchRepository repository;

    @Mock
    private ArticleSearchVisibilityService visibilityService;

    @Mock
    private AuthorAssembler authorAssembler;

    @InjectMocks
    private ArticleSearchQueryServiceImpl service;

    @Test
    void searchReturnsEmptyPageWhenNoHits() {
        SearchHits<com.example.blog.entity.ArticleDocument> hits =
                org.mockito.Mockito.mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(hits.getTotalHits()).thenReturn(0L);
        when(repository.searchByKeyword("spring", 1, 10)).thenReturn(hits);
        when(visibilityService.filterVisible(List.of())).thenReturn(List.of());

        ArticleSearchDTO dto = new ArticleSearchDTO();
        dto.setKeyword("spring");
        dto.setPage(1);
        dto.setSize(10);

        PageResult<ArticleSearchVO> result = service.search(dto);

        assertEquals(0L, result.getTotal());
        assertEquals(List.of(), result.getList());
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSearchQueryServiceImplTest test
```

Expected: FAIL，`ArticleSearchQueryService` 尚不存在。

- [ ] **Step 3: 实现搜索查询服务**

创建 `ArticleSearchQueryService`：

```java
package com.example.blog.service;

import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleSearchDTO;
import com.example.blog.vo.ArticleSearchVO;

public interface ArticleSearchQueryService {

    PageResult<ArticleSearchVO> search(ArticleSearchDTO dto);
}
```

创建 `ArticleSearchQueryServiceImpl`，把 `ArticleController.search` 中从 ES 查询到
`PageResult` 组装的代码原样移入：

```java
@Override
public PageResult<ArticleSearchVO> search(ArticleSearchDTO dto) {
    SearchHits<ArticleDocument> hits = repository.searchByKeyword(
            dto.getKeyword(), dto.getPage(), dto.getSize()
    );
    List<ArticleSearchVO> list = hits.getSearchHits().stream()
            .map(ArticleSearchVO::from)
            .toList();
    list = visibilityService.filterVisible(list);
    Map<Long, User> authorMap = authorAssembler.loadUsers(
            list.stream().map(ArticleSearchVO::getAuthorId).toList()
    );
    ArticleSearchVO.fillAuthors(list, authorMap);
    return PageResult.of(
            list,
            hits.getTotalHits(),
            dto.getPage().longValue(),
            dto.getSize().longValue()
    );
}
```

- [ ] **Step 4: 精简 Controller**

在 `ArticleController` 注入：

```java
private final ArticleSearchQueryService articleSearchQueryService;
```

将 `search` 方法改为：

```java
@GetMapping("/search")
public Result<PageResult<ArticleSearchVO>> search(@Valid ArticleSearchDTO dto) {
    return Result.success(articleSearchQueryService.search(dto));
}
```

删除 `ArticleController` 中不再使用的 ES、用户查询和集合 imports。

- [ ] **Step 5: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSearchQueryServiceImplTest test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/ArticleSearchQueryService.java blog/src/main/java/com/example/blog/service/impl/ArticleSearchQueryServiceImpl.java blog/src/main/java/com/example/blog/controller/ArticleController.java blog/src/test/java/com/example/blog/service/impl/ArticleSearchQueryServiceImplTest.java
git commit -m "refactor: 抽出文章搜索编排"
```

---

### Task 5: 删除旧评论整树路径

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/CommentService.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/controller/CommentController.java`
- Modify: `frontend/src/api/comment.ts`

- [ ] **Step 1: 删除后端整树契约**

从 `CommentService` 删除：

```java
List<CommentVO> getCommentTree(Long articleId);
```

从 `CommentServiceImpl` 删除完整 `getCommentTree` 方法。

保留 `requireVisibleArticle`、`buildTree`、`attachChildren` 和
`fillReplyToNicknames`，因为分页接口仍使用它们。

- [ ] **Step 2: 删除旧 Controller 路由**

从 `CommentController` 删除：

```java
@GetMapping
public Result<List<CommentVO>> tree(@RequestParam Long articleId) {
    return Result.success(commentService.getCommentTree(articleId));
}
```

删除不再使用的 `List`、`CommentVO` 和 `RequestParam` imports。

- [ ] **Step 3: 删除前端旧接口**

从 `frontend/src/api/comment.ts` 删除：

```typescript
export function getCommentTree(articleId: number | string) {
  return request.get<never, CommentVO[]>('/comments', { params: { articleId } })
}
```

若 `CommentVO` 仍被其他函数使用则保留 import，否则删除。

- [ ] **Step 4: 运行编译和前端类型检查**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -DskipTests compile

cd ..\frontend
npm run type-check
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/CommentService.java blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java blog/src/main/java/com/example/blog/controller/CommentController.java frontend/src/api/comment.ts
git commit -m "refactor: 移除旧评论整树接口"
```

---

### Task 6: 完整回归

**Files:**
- Modify: `docs/superpowers/plans/2026-09-18-blog-platform-backend-structure.md`

- [ ] **Step 1: 运行后端完整测试**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q test
```

Expected: PASS。

- [ ] **Step 2: 运行前端测试和静态检查**

Run:

```powershell
cd frontend
npm test
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

- [ ] **Step 3: 确认重复实现已删除**

Run:

```powershell
rg -n "private boolean isPublic|getCommentTree|ArticleDocument::from" blog/src/main/java frontend/src
```

Expected: 输出不包含 `getCommentTree`。允许 `ArticleDocument::from` 出现在
`ArticleSearchSyncServiceImpl` 之外的非同步代码中，但不得出现在
`ArticleServiceImpl` 或 `ArticleSyncTask`。

- [ ] **Step 4: 勾选验收项并提交**

```powershell
git add -- docs/superpowers/plans/2026-09-18-blog-platform-backend-structure.md
git commit -m "docs: 完成后端结构收敛验证"
```

---

## 验收清单

- [ ] 文章公开性判断只通过 `ArticleVisibilityPolicy`。
- [ ] 作者批量查询和作者 VO 转换只通过 `AuthorAssembler`。
- [ ] ES 同步、删除和对账只通过 `ArticleSearchSyncService`。
- [ ] 搜索 Controller 不再直接处理 ES 与作者回填。
- [ ] 旧评论整树接口和前端调用已删除。
- [ ] 评论分页、文章列表、热门、搜索、收藏和通知行为不变。
- [ ] 后端测试、前端测试和静态检查全部通过。
