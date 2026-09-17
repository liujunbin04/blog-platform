# 博客平台热门榜与缓存一致性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一文章浏览量、热门榜和文章缓存的更新路径，消除旧数据、残缺榜单和计数缓存过期问题。

**Architecture:** Redis 保存实时浏览量和热门榜，MySQL 保存浏览量快照。单个维护任务按“回写、构建、原子替换”顺序刷新，列表页和侧栏统一读取 `HotArticleService`。

**Tech Stack:** Java 17、Spring Boot、Spring Data Redis、Redisson、MySQL、JUnit 5、Mockito、Vue 3、Vitest

---

## 文件结构

**创建：**

- `blog/src/main/java/com/example/blog/task/BlogMaintenanceTask.java`：统一执行浏览量回写和热门榜刷新。
- `blog/src/test/java/com/example/blog/task/BlogMaintenanceTaskTest.java`：验证任务顺序和单篇失败隔离。
- `blog/src/test/java/com/example/blog/service/impl/HotArticleServiceImplTest.java`：验证原子替换、候选回填和数据库回源。
- `blog/src/test/java/com/example/blog/service/impl/LikeRecordServiceImplTest.java`：验证点赞事务提交后缓存失效。

**修改：**

- `blog/src/main/java/com/example/blog/service/ViewCountService.java`：暴露当前全部浏览量计数的只读快照。
- `blog/src/main/java/com/example/blog/service/impl/ViewCountServiceImpl.java`：返回绝对浏览量并支持文章删除清理。
- `blog/src/main/java/com/example/blog/service/HotArticleService.java`：增加原子刷新和候选回填契约。
- `blog/src/main/java/com/example/blog/service/impl/HotArticleServiceImpl.java`：使用临时 ZSet 构建并原子替换。
- `blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java`：热门排序统一委托热门服务。
- `blog/src/main/java/com/example/blog/service/impl/LikeRecordServiceImpl.java`：点赞、取消点赞后清除详情缓存。
- `blog/src/main/java/com/example/blog/config/RedisCacheConfig.java`：文章缓存 TTL 调整为 10 分钟。
- `blog/src/main/resources/sql/init.sql`：增加热门回源索引。
**删除：**

- `blog/src/main/java/com/example/blog/task/HotArticleTask.java`
- `blog/src/main/java/com/example/blog/task/ViewCountFlushTask.java`

---

### Task 1: 统一维护任务和浏览量回写顺序

**Files:**
- Create: `blog/src/main/java/com/example/blog/task/BlogMaintenanceTask.java`
- Create: `blog/src/test/java/com/example/blog/task/BlogMaintenanceTaskTest.java`
- Delete: `blog/src/main/java/com/example/blog/task/HotArticleTask.java`
- Delete: `blog/src/main/java/com/example/blog/task/ViewCountFlushTask.java`

- [ ] **Step 1: 写任务顺序失败测试**

创建 `blog/src/test/java/com/example/blog/task/BlogMaintenanceTaskTest.java`：

```java
package com.example.blog.task;

import com.example.blog.service.HotArticleService;
import com.example.blog.service.ViewCountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogMaintenanceTaskTest {

    @Mock
    private ViewCountService viewCountService;

    @Mock
    private HotArticleService hotArticleService;

    @InjectMocks
    private BlogMaintenanceTask task;

    @Test
    void flushesViewCountsBeforeRefreshingHotArticles() {
        when(viewCountService.flushToDatabase()).thenReturn(3);

        task.runMaintenance();

        InOrder order = inOrder(viewCountService, hotArticleService);
        order.verify(viewCountService).flushToDatabase();
        order.verify(hotArticleService).refreshHotArticles();
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=BlogMaintenanceTaskTest test
```

Expected: FAIL，`BlogMaintenanceTask` 尚未创建。

- [ ] **Step 3: 实现统一任务**

创建 `blog/src/main/java/com/example/blog/task/BlogMaintenanceTask.java`：

```java
package com.example.blog.task;

import com.example.blog.service.HotArticleService;
import com.example.blog.service.ViewCountService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlogMaintenanceTask {

    private final ViewCountService viewCountService;
    private final HotArticleService hotArticleService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void runMaintenance() {
        try {
            int flushed = viewCountService.flushToDatabase();
            hotArticleService.refreshHotArticles();
            log.info("统一维护完成，浏览量回写 {} 篇", flushed);
        } catch (Exception e) {
            log.error("统一维护任务失败", e);
        }
    }

    @PreDestroy
    public void flushOnShutdown() {
        try {
            viewCountService.flushToDatabase();
        } catch (Exception e) {
            log.warn("停机前浏览量回写失败: {}", e.getMessage());
        }
    }
}
```

删除：

```text
blog/src/main/java/com/example/blog/task/HotArticleTask.java
blog/src/main/java/com/example/blog/task/ViewCountFlushTask.java
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=BlogMaintenanceTaskTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/task blog/src/test/java/com/example/blog/task
git commit -m "fix(hot): 统一浏览量与热门榜维护顺序"
```

---

### Task 2: 使用临时 ZSet 原子替换热门榜

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/impl/HotArticleServiceImpl.java`
- Test: `blog/src/test/java/com/example/blog/service/impl/HotArticleServiceImplTest.java`

- [ ] **Step 1: 写原子替换失败测试**

创建 `blog/src/test/java/com/example/blog/service/impl/HotArticleServiceImplTest.java`：

```java
package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.service.ArticleService;
import com.example.blog.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotArticleServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ArticleService articleService;

    @Mock
    private UserService userService;

    @InjectMocks
    private HotArticleServiceImpl service;

    @Test
    void refreshBuildsTemporaryKeyThenRenamesIt() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service.refreshHotArticles();

        verify(redisTemplate).rename(
                HotArticleServiceImpl.HOT_KEY_TEMP,
                HotArticleServiceImpl.HOT_KEY
        );
    }
}
```

为避免测试耦合 MyBatis-Plus 查询链，实际实现应把“查询热门文章”抽成
`protected List<Article> loadTopArticles()`，测试通过 spy 覆盖该方法并返回两条文章。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=HotArticleServiceImplTest test
```

Expected: FAIL，`HOT_KEY_TEMP` 和 `rename` 行为尚不存在。

- [ ] **Step 3: 实现临时 ZSet 构建和原子替换**

在 `HotArticleServiceImpl` 中增加：

```java
public static final String HOT_KEY_TEMP = HOT_KEY + ":building";
```

先把数据库查询抽成方法：

```java
protected List<Article> loadTopArticles() {
    return articleService.lambdaQuery()
            .eq(Article::getStatus, 1)
            .eq(Article::getHidden, 0)
            .orderByDesc(Article::getViewCount)
            .last("LIMIT " + HOT_TOP_N)
            .list();
}
```

将 `refreshHotArticles()` 改为调用 `loadTopArticles()`，并把 `delete(HOT_KEY)` 和循环 `ZADD` 替换为：

```java
stringRedisTemplate.delete(HOT_KEY_TEMP);
for (Article article : top) {
    stringRedisTemplate.opsForZSet().add(
            HOT_KEY_TEMP,
            String.valueOf(article.getId()),
            article.getViewCount().doubleValue()
    );
}
if (!top.isEmpty()) {
    stringRedisTemplate.rename(HOT_KEY_TEMP, HOT_KEY);
} else {
    stringRedisTemplate.delete(HOT_KEY);
}
```

保留数据库回源和失效文章过滤。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=HotArticleServiceImplTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/impl/HotArticleServiceImpl.java blog/src/test/java/com/example/blog/service/impl/HotArticleServiceImplTest.java
git commit -m "fix(hot): 原子替换热门文章榜单"
```

---

### Task 3: 候选回填和统一热门接口

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/HotArticleService.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/HotArticleServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/ArticleServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/controller/ArticleController.java`
- Test: `blog/src/test/java/com/example/blog/service/impl/HotArticleServiceImplTest.java`

- [ ] **Step 1: 写候选回填失败测试**

在 `HotArticleServiceImplTest` 增加：

```java
@Test
void readsMoreCandidatesWhenExpiredMembersAreSkipped() {
    when(zSetOperations.reverseRange(HotArticleServiceImpl.HOT_KEY, 0, 4))
            .thenReturn(Set.of("1", "2"));
    when(zSetOperations.reverseRange(HotArticleServiceImpl.HOT_KEY, 0, 9))
            .thenReturn(Set.of("1", "2", "3"));

    List<Long> ids = service.getHotArticleIds(3);

    assertTrue(ids.contains(3L));
}
```

补充必要的 imports：

```java
import org.springframework.data.redis.core.ZSetOperations;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=HotArticleServiceImplTest test
```

Expected: FAIL，当前只读取 `topN` 个成员。

- [ ] **Step 3: 实现候选回填**

将 `getHotArticleIds(int topN)` 改为读取 `topN * 3` 个候选，并在组装 VO 时按有效文章补足：

```java
int candidateSize = Math.max(topN, topN * 3);
Set<String> ids = stringRedisTemplate.opsForZSet()
        .reverseRange(HOT_KEY, 0, candidateSize - 1L);
```

在 `getHotArticles` 的循环中使用：

```java
if (list.size() >= topN) {
    break;
}
```

在 `ArticleServiceImpl.pageArticles` 中，当 `sort=hot` 时，委托：

```java
return PageResult.of(
        hotArticleService.getHotArticles(dto.getPageSize()),
        dto.getPageSize().longValue(),
        dto.getPageNum().longValue(),
        dto.getPageSize().longValue()
);
```

给 `ArticleServiceImpl` 注入：

```java
private final HotArticleService hotArticleService;
```

将 `ArticleController` 初始化的 `HotArticleService` 保留；侧栏继续调用接口。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=HotArticleServiceImplTest,ArticleServiceImplTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service blog/src/main/java/com/example/blog/controller/ArticleController.java blog/src/test/java/com/example/blog/service/impl/HotArticleServiceImplTest.java
git commit -m "fix(hot): 回填候选并统一热门数据源"
```

---

### Task 4: 浏览量服务支持快照和清理

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/ViewCountService.java`
- Modify: `blog/src/main/java/com/example/blog/service/impl/ViewCountServiceImpl.java`
- Test: `blog/src/test/java/com/example/blog/service/impl/ViewCountServiceImplTest.java`

- [ ] **Step 1: 写快照测试**

创建 `blog/src/test/java/com/example/blog/service/impl/ViewCountServiceImplTest.java`：

```java
package com.example.blog.service.impl;

import com.example.blog.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ViewCountServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ArticleMapper articleMapper;

    @InjectMocks
    private ViewCountServiceImpl service;

    @Test
    void incrementUsesExistingValueAsAbsoluteCount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("blog:view:count:9", "5")).thenReturn(true);
        when(valueOperations.increment("blog:view:count:9")).thenReturn(6L);

        long value = service.incrementAndGet(9L, 5);

        verify(valueOperations).setIfAbsent("blog:view:count:9", "5");
    }
}
```

- [ ] **Step 2: 运行测试并确认失败行为**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ViewCountServiceImplTest test
```

Expected: PASS 或 FAIL，确认当前实现行为后再继续；若测试通过，保留测试并只调整服务接口。

- [ ] **Step 3: 增加快照方法**

在 `ViewCountService` 增加：

```java
Map<Long, Long> snapshot();
```

在实现中增加：

```java
@Override
public Map<Long, Long> snapshot() {
    Map<Long, Long> values = new LinkedHashMap<>();
    for (String key : scanKeys()) {
        Long id = parseId(key);
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (id == null || raw == null) {
            continue;
        }
        try {
            values.put(id, Long.parseLong(raw));
        } catch (NumberFormatException e) {
            log.warn("跳过非法浏览量值: key={} value={}", key, raw);
        }
    }
    return values;
}
```

保留 `flushToDatabase()` 和 `remove()`。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=ViewCountServiceImplTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/ViewCountService.java blog/src/main/java/com/example/blog/service/impl/ViewCountServiceImpl.java blog/src/test/java/com/example/blog/service/impl/ViewCountServiceImplTest.java
git commit -m "fix(view): 提供实时浏览量快照"
```

---

### Task 5: 热门读取使用实时快照并增加索引

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/impl/HotArticleServiceImpl.java`
- Modify: `blog/src/main/resources/sql/init.sql`

- [ ] **Step 1: 写回源索引测试**

在 `HotArticleServiceImplTest` 增加：

```java
@Test
void snapshotFallsBackToDatabase() {
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.reverseRange(HotArticleServiceImpl.HOT_KEY, 0, 99))
            .thenReturn(Set.of());

    service.getHotArticleIds(10);

    verify(articleService).lambdaQuery();
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=HotArticleServiceImplTest test
```

Expected: PASS 或 FAIL；若测试通过，只补索引和断言。

- [ ] **Step 3: 增加热门索引**

在 `init.sql` 的文章表索引中加入：

```sql
  KEY `idx_status_hidden_view` (`status`, `hidden`, `view_count`),
  KEY `idx_status_hidden_created` (`status`, `hidden`, `create_time`)
```

确保唯一名称不与现有索引冲突。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=HotArticleServiceImplTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/impl/HotArticleServiceImpl.java blog/src/main/resources/sql/init.sql blog/src/test/java/com/example/blog/service/impl/HotArticleServiceImplTest.java
git commit -m "perf(hot): 优化热门榜回源查询"
```

---

### Task 6: 点赞后失效文章缓存

**Files:**
- Modify: `blog/src/main/java/com/example/blog/service/impl/LikeRecordServiceImpl.java`
- Modify: `blog/src/main/java/com/example/blog/config/RedisCacheConfig.java`
- Test: `blog/src/test/java/com/example/blog/service/impl/LikeRecordServiceImplTest.java`

- [ ] **Step 1: 写缓存失效失败测试**

创建 `blog/src/test/java/com/example/blog/service/impl/LikeRecordServiceImplTest.java`：

```java
package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.service.ArticleCacheService;
import com.example.blog.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeRecordServiceImplTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private ArticleCacheService articleCacheService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private LikeRecordServiceImpl service;

    @Test
    void evictsArticleCacheAfterLike() {
        Article article = new Article();
        article.setId(5L);
        article.setAuthorId(1L);
        when(articleService.requirePublicArticle(5L)).thenReturn(article);

        service.like(5L);

        verify(articleCacheService).evict(5L);
    }
}
```

为避免依赖 Spring Security 上下文，实际实现应把“读取当前用户 id”抽成
`protected Long currentUserId()`，测试使用 spy 返回固定用户 id。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=LikeRecordServiceImplTest test
```

Expected: FAIL，当前点赞后没有调用缓存失效。

- [ ] **Step 3: 注入缓存服务并失效**

在 `LikeRecordServiceImpl` 注入：

```java
private final ArticleCacheService articleCacheService;
```

增加可见性方法：

```java
protected Long currentUserId() {
    return SecurityUtil.getCurrentUserId();
}
```

把 `like` 和 `unlike` 内部对 `SecurityUtil.getCurrentUserId()` 的直接调用改为
`currentUserId()`，测试即可通过 spy 避开发言。

在 `like` 和 `unlike` 的数据库更新后调用：

```java
articleCacheService.evict(articleId);
```

确保缓存失效发生在同一事务方法内；这是本项目接受的最终一致策略。

将 `RedisCacheConfig` 的默认 TTL 改为：

```java
.entryTtl(Duration.ofMinutes(10))
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
cd blog
$env:MAVEN_USER_HOME='C:\Users\vannnn\Desktop\blog-platform\.mvn-home'
$env:MAVEN_OPTS='-Duser.home=C:\Users\vannnn'
.\mvnw.cmd -q -Dtest=LikeRecordServiceImplTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- blog/src/main/java/com/example/blog/service/impl/LikeRecordServiceImpl.java blog/src/main/java/com/example/blog/config/RedisCacheConfig.java blog/src/test/java/com/example/blog/service/impl/LikeRecordServiceImplTest.java
git commit -m "fix(cache): 点赞后失效文章详情缓存"
```

---

### Task 7: 批次验证

**Files:**
- Modify: `docs/superpowers/plans/2026-09-17-blog-platform-stabilization-phase-2.md`

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

- [ ] **Step 3: 验证数据库新索引**

Run:

```powershell
$env:MYSQL_PORT='43306'
$env:JWT_SECRET='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
$dockerCli='C:\Users\vannnn\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
& $dockerCli compose -f docker-compose.yml -f docker-compose.init-test.yml -p blog-hot-test up -d --wait mysql
& $dockerCli exec blog-mysql-init-test mysql -uroot -pblog_dev_pwd -e "SHOW INDEX FROM article;"
& $dockerCli compose -f docker-compose.yml -f docker-compose.init-test.yml -p blog-hot-test down -v
```

Expected: 输出包含 `idx_status_hidden_view`。

- [ ] **Step 4: 提交验证记录**

```powershell
git add -- docs/superpowers/plans/2026-09-17-blog-platform-stabilization-phase-2.md
git commit -m "docs: 完成热门榜与缓存修复验证"
```

---

## 批次验收清单

- [ ] 浏览量和热门榜由同一任务按顺序维护。
- [ ] 热门榜使用临时 ZSet 原子替换。
- [ ] 失效文章会跳过并回填候选。
- [ ] 列表页和侧栏使用同一热门数据源。
- [ ] 浏览量支持实时快照和数据库回源。
- [ ] 热门查询具备匹配的联合索引。
- [ ] 点赞和取消点赞后清除详情缓存。
- [ ] 文章缓存 TTL 为 10 分钟。
- [ ] 后端测试、前端测试和静态检查全部通过。
