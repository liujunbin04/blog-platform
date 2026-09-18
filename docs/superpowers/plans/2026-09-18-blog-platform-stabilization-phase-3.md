# 博客平台搜索可靠性与通知幂等 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 确保隐藏文章不会通过 Elasticsearch 搜索泄露，并使用稳定事件键消除重复通知。

**Architecture:** Elasticsearch 只作为派生搜索索引，搜索结果返回前按 MySQL 可见性批量过滤。通知消息携带业务事件键，数据库唯一索引作为最终幂等边界。

**Tech Stack:** Java 17、Spring Boot、Spring Data Elasticsearch、RabbitMQ、MySQL、JUnit 5、Mockito、Vue 3、Vitest

---

## 文件结构

**创建：**

- `blog/src/main/java/com/example/blog/service/ArticleSearchVisibilityService.java`：封装搜索结果到 MySQL 可见性的过滤。
- `blog/src/main/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImpl.java`：搜索可见性实现。
- `blog/src/test/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImplTest.java`：验证隐藏文章过滤。
- `blog/src/test/java/com/example/blog/service/impl/NotificationServiceImplTest.java`：验证 eventKey 幂等。

**修改：**

- `blog/src/main/java/com/example/blog/controller/ArticleController.java`：搜索改为使用可见性过滤服务。
- `blog/src/main/java/com/example/blog/entity/ArticleDocument.java`：增加 `authorNickname` 和标签搜索字段。
- `blog/src/main/java/com/example/blog/entity/Article.java`：增加昵称/标签序列化所需访问方式的任务保持不变。
- `blog/src/main/java/com/example/blog/repository/ArticleSearchRepositoryCustomImpl.java`：扩展搜索字段。
- `blog/src/main/java/com/example/blog/task/ArticleSyncTask.java`：启动对账时清理非公开 ES 文档。
- `blog/src/main/java/com/example/blog/dto/NotificationMessage.java`：增加 `eventKey`。
- `blog/src/main/java/com/example/blog/entity/Notification.java`：增加 `eventKey`。
- `blog/src/main/java/com/example/blog/service/impl/NotificationServiceImpl.java`：使用唯一键处理重复消息。
- `blog/src/main/java/com/example/blog/consumer/NotificationConsumer.java`：传递 `eventKey`。
- `blog/src/main/resources/sql/init.sql`：增加 `event_key` 和唯一索引。

**迁移：**

- `blog/src/main/resources/sql/migrations/migration-2026-09-18.sql`：为已有数据库增加事件键。

---

### Task 1: 搜索结果按 MySQL 可见性过滤

**Files:**
- Create: `blog/src/main/java/com/example/blog/service/ArticleSearchVisibilityService.java`
- Create: `blog/src/main/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/controller/ArticleController.java`
- Test: `blog/src/test/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImplTest.java`

- [ ] **Step 1: 写隐藏文章过滤失败测试**

创建 `blog/src/test/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImplTest.java`：

```java
package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.service.ArticleService;
import com.example.blog.vo.ArticleSearchVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSearchVisibilityServiceImplTest {

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private ArticleSearchVisibilityServiceImpl service;

    @Test
    void removesHiddenDocumentsFromVisibleResult() {
        ArticleSearchVO hidden = new ArticleSearchVO();
        hidden.setId(1L);
        ArticleSearchVO visible = new ArticleSearchVO();
        visible.setId(2L);

        Article hiddenArticle = new Article();
        hiddenArticle.setId(1L);
        hiddenArticle.setStatus(1);
        hiddenArticle.setHidden(1);
        Article visibleArticle = new Article();
        visibleArticle.setId(2L);
        visibleArticle.setStatus(1);
        visibleArticle.setHidden(0);
        when(articleService.listByIds(List.of(1L, 2L)))
                .thenReturn(List.of(hiddenArticle, visibleArticle));

        List<ArticleSearchVO> result = service.filterVisible(List.of(hidden, visible));

        assertEquals(List.of(2L), result.stream().map(ArticleSearchVO::getId).toList());
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSearchVisibilityServiceImplTest test
```

Expected: FAIL，`ArticleSearchVisibilityService` 和 `filterVisible` 尚不存在。

- [ ] **Step 3: 实现搜索可见性服务**

创建 `blog/src/main/java/com/example/blog/service/ArticleSearchVisibilityService.java`：

```java
package com.example.blog.service;

import com.example.blog.vo.ArticleSearchVO;

import java.util.List;

public interface ArticleSearchVisibilityService {

    List<ArticleSearchVO> filterVisible(List<ArticleSearchVO> hits);
}
```

创建 `blog/src/main/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImpl.java`：

```java
package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.service.ArticleService;
import com.example.blog.service.ArticleSearchVisibilityService;
import com.example.blog.vo.ArticleSearchVO;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ArticleSearchVisibilityServiceImpl implements ArticleSearchVisibilityService {

    private final ArticleService articleService;

    @Override
    public List<ArticleSearchVO> filterVisible(List<ArticleSearchVO> hits) {
        Set<Long> ids = hits.stream().map(ArticleSearchVO::getId).collect(Collectors.toSet());
        Map<Long, Article> byId = articleService.listByIds(ids).stream()
                .collect(Collectors.toMap(Article::getId, a -> a));
        return hits.stream()
                .filter(v -> byId.containsKey(v.getId()))
                .filter(v -> byId.get(v.getId()).getStatus() == 1)
                .filter(v -> byId.get(v.getId()).getHidden() == 0)
                .toList();
    }
}
```

在 `ArticleController.search` 中，将 `list` 构造后追加：

```java
list = articleSearchVisibilityService.filterVisible(list);
```

并注入：

```java
private final ArticleSearchVisibilityService articleSearchVisibilityService;
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSearchVisibilityServiceImplTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/ArticleSearchVisibilityService.java blog/src/main/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImpl.java blog/src/main/java/com/example/blog/controller/ArticleController.java blog/src/test/java/com/example/blog/service/impl/ArticleSearchVisibilityServiceImplTest.java
git commit -m "fix(search): 增加搜索结果可见性过滤"
```

---

### Task 2: 扩展搜索字段和作者标签查询

**Files:**
- Modify: `blog/src/main/java/com/example/blog/entity/ArticleDocument.java`
- Modify: `blog/src/main/java/com/example/blog/repository/ArticleSearchRepositoryCustomImpl.java`
- Modify: `blog/src/main/java/com/example/blog/vo/ArticleSearchVO.java`
- Test: `blog/src/test/java/com/example/blog/repository/ArticleSearchRepositoryCustomImplTest.java`

- [ ] **Step 1: 写字段映射失败测试**

创建 `blog/src/test/java/com/example/blog/repository/ArticleSearchRepositoryCustomImplTest.java`：

```java
package com.example.blog.repository;

import com.example.blog.entity.ArticleDocument;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleSearchRepositoryCustomImplTest {

    @Test
    void articleDocumentContainsSearchableAuthorAndTagsFields() {
        assertTrue(Arrays.stream(ArticleDocument.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("authorNickname")));
        assertTrue(Arrays.stream(ArticleDocument.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("tags")));
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSearchRepositoryCustomImplTest test
```

Expected: FAIL，字段尚未定义。

- [ ] **Step 3: 增加字段和查询**

在 `ArticleDocument` 增加：

```java
@Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
private String authorNickname;

@Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
private List<String> tagNames;
```

在 `from(Article article)` 中保留现有 `tags` 字符串，并增加：

```java
doc.setTagNames(article.getTags() == null
        ? List.of()
        : Arrays.stream(article.getTags().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList());
```

在查询字段中改为：

```java
.fields("title", "content", "authorNickname", "tagNames")
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSearchRepositoryCustomImplTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/entity/ArticleDocument.java blog/src/main/java/com/example/blog/repository/ArticleSearchRepositoryCustomImpl.java blog/src/test/java/com/example/blog/repository/ArticleSearchRepositoryCustomImplTest.java
git commit -m "feat(search): 支持作者和标签搜索"
```

---

### Task 3: 启动索引对账清理幽灵文档

**Files:**
- Modify: `blog/src/main/java/com/example/blog/repository/ArticleSearchRepository.java`
- Modify: `blog/src/main/java/com/example/blog/repository/ArticleSearchRepositoryCustomImpl.java`
- Modify: `blog/src/main/java/com/example/blog/task/ArticleSyncTask.java`
- Test: `blog/src/test/java/com/example/blog/task/ArticleSyncTaskTest.java`

- [ ] **Step 1: 写对账测试**

创建 `blog/src/test/java/com/example/blog/task/ArticleSyncTaskTest.java`：

```java
package com.example.blog.task;

import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSyncTaskTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private ArticleSearchRepository searchRepository;

    @InjectMocks
    private ArticleSyncTask task;

    @Test
    void backfillTriggersRepositoryReconciliation() {
        when(searchRepository.findAll()).thenReturn(List.of());
        when(articleService.lambdaQuery()).thenThrow(new RuntimeException("stop after reconciliation"));

        task.backfillSearchIndex();

        verify(searchRepository).findAll();
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSyncTaskTest test
```

Expected: FAIL，仓库还没有对账方法。

- [ ] **Step 3: 实现对账接口**

在 `ArticleSyncTask` 内部实现对账，避免给 Spring Data repository 接口增加自定义默认方法：

```java
private void reconcileSearchVisibility() {
    articleSearchRepository.findAll().forEach(document -> {
        Article article = articleService.getById(document.getId());
        if (article == null
                || article.getStatus() == null
                || article.getStatus() != 1
                || (article.getHidden() != null && article.getHidden() == 1)) {
            articleSearchRepository.deleteById(document.getId());
        }
    });
}
```

测试中对 `articleSearchRepository.findAll()` 返回空列表，并验证后续回填不报错。

在 `ArticleSyncTask.backfillSearchIndex()` 的 `saveAll` 前调用：

`reconcileSearchVisibility()`。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ArticleSyncTaskTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/task/ArticleSyncTask.java blog/src/test/java/com/example/blog/task/ArticleSyncTaskTest.java
git commit -m "fix(search): 启动时对账 ES 可见性"
```

---

### Task 4: 通知事件键和唯一索引

**Files:**
- Modify: `blog/src/main/java/com/example/blog/dto/NotificationMessage.java`
- Modify: `blog/src/main/java/com/example/blog/entity/Notification.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/NotificationServiceImpl.java`
- Modify: `blog/src/main/resources/sql/init.sql`
- Create: `blog/src/main/resources/sql/migrations/migration-2026-09-18.sql`
- Test: `blog/src/test/java/com/example/blog/service/impl/NotificationServiceImplTest.java`

- [ ] **Step 1: 写重复事件失败测试**

创建 `blog/src/test/java/com/example/blog/service/impl/NotificationServiceImplTest.java`：

```java
package com.example.blog.service.impl;

import com.example.blog.dto.NotificationMessage;
import com.example.blog.entity.Notification;
import com.example.blog.mapper.NotificationMapper;
import com.example.blog.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl service;

    @Test
    void duplicateEventKeyIsIgnored() {
        NotificationMessage message = new NotificationMessage(
                "LIKE",
                1L,
                2L,
                3L,
                "content",
                "LIKE:8"
        );
        doThrow(new DuplicateKeyException("duplicate"))
                .when(notificationMapper)
                .insert(any(Notification.class));

        service.createFromMessage(message);
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=NotificationServiceImplTest test
```

Expected: FAIL，构造函数和事件键尚未实现。

- [ ] **Step 3: 增加事件键和唯一约束**

在 `NotificationMessage` 增加：

```java
private String eventKey;
```

并保留六参数构造器，按新的顺序传入 `eventKey`。

在 `Notification` 增加：

```java
private String eventKey;
```

在 `NotificationServiceImpl.createFromMessage` 中移除时间窗口查询，改为：

```java
Notification notification = new Notification();
notification.setEventKey(message.getEventKey());
notification.setRecipientId(message.getRecipientId());
notification.setSenderId(message.getSenderId());
notification.setType(message.getType());
notification.setArticleId(message.getArticleId());
notification.setContent(truncate(message.getContent(), 500));
notification.setIsRead(0);
try {
    save(notification);
} catch (DuplicateKeyException ignored) {
    // 唯一索引已经保证同一事件只写入一次
}
```

在 `init.sql` 的 notification 表增加：

```sql
  `event_key`    VARCHAR(100)    NOT NULL COMMENT '业务事件唯一键',
  UNIQUE KEY `uk_event_key` (`event_key`)
```

创建 `migration-2026-09-18.sql`：

```sql
USE `blog`;

ALTER TABLE `notification`
  ADD COLUMN `event_key` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务事件唯一键' AFTER `article_id`,
  ADD UNIQUE KEY `uk_event_key` (`event_key`);
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=NotificationServiceImplTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/dto/NotificationMessage.java blog/src/main/java/com/example/blog/entity/Notification.java blog/src/main/java/com/example/blog/service/impl/NotificationServiceImpl.java blog/src/main/resources/sql/init.sql blog/src/main/resources/sql/migrations/migration-2026-09-18.sql blog/src/test/java/com/example/blog/service/impl/NotificationServiceImplTest.java
git commit -m "fix(notification): 使用事件键保证幂等"
```

---

### Task 5: 点赞和评论消息携带事件键

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/impl/LikeRecordServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java`

- [ ] **Step 1: 写构造器失败测试**

在 `NotificationServiceImplTest` 增加：

```java
@Test
void messageEventKeyUsesStableBusinessId() {
    NotificationMessage message = new NotificationMessage(
            "LIKE", 1L, 2L, 3L, "content", "LIKE:8"
    );

    assertEquals("LIKE:8", message.getEventKey());
}
```

补充：

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=NotificationServiceImplTest test
```

Expected: FAIL，构造器仍为五参数。

- [ ] **Step 3: 生成事件键**

在 `LikeRecordServiceImpl.like` 的 `save(record)` 后使用：

```java
"LIKE:" + record.getId()
```

在 `CommentServiceImpl.addComment` 的 `save(comment)` 后使用：

```java
"COMMENT:" + comment.getId()
```

两处 `NotificationMessage` 构造参数补上事件键。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=NotificationServiceImplTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/impl/LikeRecordServiceImpl.java blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java blog/src/test/java/com/example/blog/service/impl/NotificationServiceImplTest.java
git commit -m "fix(notification): 绑定点赞和评论事件键"
```

---

### Task 6: 批次验证

**Files:**
- Modify: `docs/superpowers/plans/2026-09-18-blog-platform-stabilization-phase-3.md`

- [ ] **Step 1: 运行后端测试**

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

- [ ] **Step 3: 验证新数据库字段**

Run:

```powershell
$env:MYSQL_PORT='43306'
$env:JWT_SECRET='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
$dockerCli='C:\Users\vannnn\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
& $dockerCli compose -f docker-compose.yml -f docker-compose.init-test.yml -p blog-search-test up -d --wait mysql
& $dockerCli exec blog-mysql-init-test mysql -uroot -pblog_dev_pwd -e "SHOW COLUMNS FROM blog.notification; SHOW INDEX FROM blog.notification;"
& $dockerCli compose -f docker-compose.yml -f docker-compose.init-test.yml -p blog-search-test down -v
```

Expected: 输出包含 `event_key` 和 `uk_event_key`。

- [ ] **Step 4: 提交验证记录**

```powershell
git add -- docs/superpowers/plans/2026-09-18-blog-platform-stabilization-phase-3.md
git commit -m "docs: 完成搜索与通知修复验证"
```

---

## 批次验收清单

- [x] 搜索结果按 MySQL 可见性过滤。
- [x] 搜索支持作者昵称和标签。
- [x] 启动对账会删除非公开 ES 文档。
- [x] 通知消息携带稳定事件键。
- [x] 数据库唯一索引阻止重复事件。
- [x] 点赞和评论消息使用业务记录 ID。
- [x] 后端测试、前端测试和静态检查全部通过。
