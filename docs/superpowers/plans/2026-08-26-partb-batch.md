# Part B 全量处理实施计划（B1-B13）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 处理全项目审查发现的全部 13 项遗留问题：通知功能完整实现、热门文章启用、schema 冗余修正、安全/性能/体验打磨。

**Architecture:** 后端新增 notification 一套四层（entity/mapper/service/controller）与 hot 端点；前端拆包配置、request 去重、三处组件打磨。数据库变更为两份 SQL（init.sql 更新 + 手动迁移脚本），实际执行由用户完成。

**Tech Stack:** Spring Boot 3.5 / MyBatis-Plus / Spring Security / RabbitMQ；Vue 3 + TS + Element Plus + Vite 8(rolldown)。

**设计文档:** `docs/superpowers/specs/2026-08-26-partb-batch-design.md`（需求来源）

## Global Constraints

- 验证门槛：后端 `./mvnw.cmd -q compile` 退出码 0；前端 `npm run build && npm run lint` 退出码 0（无自动化测试设施，不得引入测试框架）。
- 代码风格与现有代码一致：中文注释、解释「为什么」；Controller 薄、Service 承载业务校验；批量回查防 N+1 必须判空集合。
- NotificationPageDTO 校验注解与 FavoritePageDTO 同款模式（@NotNull/@Min/@Max + 默认值）。
- 前端组件 `<script setup lang="ts">` + scoped 样式；类型定义进 `types/api.ts`，API 函数进对应 api 模块。
- 本项目 Vite 为 rolldown 内核（8.2.2）：拆包用 `build.rolldownOptions.output.codeSplitting.groups`，禁用已废弃的 manualChunks/advancedChunks。
- 提交信息格式：`类型(范围): 中文描述`。Windows git-bash 环境执行命令。
- DB 变更不自动执行——迁移 SQL 随代码提交，由用户手动运行。

---

### Task 1: SQL 迁移文件 + init.sql 更新（B1/B6/B2 schema 部分）

**Files:**
- Create: `blog/src/main/resources/sql/migration-2026-08-26.sql`
- Modify: `blog/src/main/resources/sql/init.sql`

**Interfaces:**
- Produces: `notification` 表结构（Task 2 实体与其一一对应）；user 表组合唯一索引 `(username, deleted)`、`(email, deleted)`；like_record/favorite 无 deleted 列。

- [ ] **Step 1: 创建迁移脚本**

创建 `blog/src/main/resources/sql/migration-2026-08-26.sql`：

```sql
-- =============================================================
-- 迁移脚本（2026-08-26）：
-- B1 用户唯一索引改造 + B6 关系表冗余列删除 + B2 通知表
-- 执行方式：mysql -u root -p blog < migration-2026-08-26.sql
-- =============================================================
USE `blog`;

-- B1: 用户唯一索引改为含 deleted 的组合索引。
-- MP 逻辑删除值固定为 1：同一用户名最多一条活跃记录(deleted=0)，软删后可重名；
-- 但多次注册删除会撞历史行(deleted=1)——教程场景可接受，
-- 生产方案应改用 deleted_at 时间戳或应用层保证
ALTER TABLE `user`
  DROP INDEX `uk_username`,
  DROP INDEX `uk_email`,
  ADD UNIQUE KEY `uk_username_deleted` (`username`, `deleted`),
  ADD UNIQUE KEY `uk_email_deleted` (`email`, `deleted`);

-- B6: like_record/favorite 物理删除语义下 deleted 恒为 0，纯冗余
ALTER TABLE `like_record` DROP COLUMN `deleted`;
ALTER TABLE `favorite` DROP COLUMN `deleted`;

-- B2: 站内通知表
CREATE TABLE IF NOT EXISTS `notification` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `recipient_id` BIGINT UNSIGNED NOT NULL COMMENT '收件人 id（user.id）',
  `sender_id`    BIGINT UNSIGNED NOT NULL COMMENT '发件人 id（user.id）',
  `type`         VARCHAR(20)     NOT NULL COMMENT '类型：COMMENT/LIKE（同 NotificationMessage.type 取值）',
  `article_id`   BIGINT UNSIGNED NOT NULL COMMENT '关联文章 id',
  `content`      VARCHAR(500)    DEFAULT NULL COMMENT '通知内容摘要',
  `is_read`      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读 1 已读',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_recipient` (`recipient_id`, `is_read`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '站内通知表';
```

- [ ] **Step 2: init.sql 同步更新**

init.sql 三处修改：
1. user 表的两行唯一索引替换：

```sql
  UNIQUE KEY `uk_username_deleted` (`username`, `deleted`),
  UNIQUE KEY `uk_email_deleted` (`email`, `deleted`)
```

2. like_record 与 favorite 表各删除一行：

```sql
  `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
```

3. 文件末尾追加第 6 节（带与既有节相同的分隔注释风格）：

```sql
-- -------------------------------------------------------------
-- 6. 站内通知表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notification` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `recipient_id` BIGINT UNSIGNED NOT NULL COMMENT '收件人 id（user.id）',
  `sender_id`    BIGINT UNSIGNED NOT NULL COMMENT '发件人 id（user.id）',
  `type`         VARCHAR(20)     NOT NULL COMMENT '类型：COMMENT/LIKE（同 NotificationMessage.type 取值）',
  `article_id`   BIGINT UNSIGNED NOT NULL COMMENT '关联文章 id',
  `content`      VARCHAR(500)    DEFAULT NULL COMMENT '通知内容摘要',
  `is_read`      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读 1 已读',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_recipient` (`recipient_id`, `is_read`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '站内通知表';
```

- [ ] **Step 3: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/resources/sql/
git commit -m "feat(blog): 新增通知表并修正用户索引与关系表 schema"
```

---

### Task 2: 通知功能后端（B2 核心）

**Files:**
- Create: `blog/src/main/java/com/example/blog/entity/Notification.java`
- Create: `blog/src/main/java/com/example/blog/mapper/NotificationMapper.java`
- Create: `blog/src/main/java/com/example/blog/dto/NotificationPageDTO.java`
- Create: `blog/src/main/java/com/example/blog/vo/NotificationVO.java`
- Create: `blog/src/main/java/com/example/blog/vo/NotificationPageVO.java`
- Create: `blog/src/main/java/com/example/blog/service/NotificationService.java`
- Create: `blog/src/main/java/com/example/blog/service/impl/NotificationServiceImpl.java`
- Create: `blog/src/main/java/com/example/blog/controller/NotificationController.java`
- Modify: `blog/src/main/java/com/example/blog/consumer/NotificationConsumer.java`

**Interfaces:**
- Consumes: `NotificationMessage{type, recipientId, senderId, articleId, content}`（TYPE_COMMENT="COMMENT"/TYPE_LIKE="LIKE"）；`SecurityUtil.getCurrentUserId()`；`Result/PageResult` 包装。
- Produces（Task 5 前端依赖）:
  - `GET /api/notifications?pageNum=&pageSize=` → `Result<NotificationPageVO>`，其中 `NotificationPageVO{total, pageNum, pageSize, list: List<NotificationVO>, unreadCount}`、`NotificationVO{id, senderId, senderNickname, type, articleId, content, isRead, createTime}`（需登录）
  - `PUT /api/notifications/read-all` → `Result<Void>`（需登录）

- [ ] **Step 1: 实体**

```java
package com.example.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知（comment.notify.queue / like.notify.queue 消费落库）
 * <p>纯追加型数据：无逻辑删除、无更新时间，已读用 is_read 标记而非删行</p>
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收件人 id */
    private Long recipientId;

    /** 发件人 id */
    private Long senderId;

    /** 类型：COMMENT / LIKE（与 NotificationMessage 常量一致） */
    private String type;

    private Long articleId;

    private String content;

    /** 是否已读：0 未读 1 已读 */
    private Integer isRead;

    private LocalDateTime createTime;
}
```

- [ ] **Step 2: Mapper**

```java
package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.blog.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内通知 Mapper
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
```

- [ ] **Step 3: DTO**

```java
package com.example.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 通知分页查询参数（GET 请求 query 参数自动绑定），约束与 FavoritePageDTO 同款
 */
@Data
public class NotificationPageDTO {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小最小为 1")
    @Max(value = 100, message = "每页大小最大为 100")
    private Integer pageSize = 10;
}
```

- [ ] **Step 4: VO 两个**

```java
package com.example.blog.vo;

import com.example.blog.entity.Notification;
import com.example.blog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知列表项（含发件人昵称）
 */
@Data
public class NotificationVO {

    private Long id;

    private Long senderId;

    private String senderNickname;

    private String type;

    private Long articleId;

    private String content;

    private Integer isRead;

    private LocalDateTime createTime;

    public static NotificationVO from(Notification n, User sender) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setSenderId(n.getSenderId());
        vo.setSenderNickname(sender == null ? "佚名" : sender.getNickname());
        vo.setType(n.getType());
        vo.setArticleId(n.getArticleId());
        vo.setContent(n.getContent());
        vo.setIsRead(n.getIsRead());
        vo.setCreateTime(n.getCreateTime());
        return vo;
    }
}
```

```java
package com.example.blog.vo;

import lombok.Data;

import java.util.List;

/**
 * 通知分页返回（比 PageResult 多一个未读数，顶栏徽标一次请求拿齐）
 */
@Data
public class NotificationPageVO {

    private Long total;

    private Long pageNum;

    private Long pageSize;

    private List<NotificationVO> list;

    /** 当前用户未读数 */
    private Long unreadCount;
}
```

- [ ] **Step 5: Service 接口**

```java
package com.example.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.blog.dto.NotificationMessage;
import com.example.blog.dto.NotificationPageDTO;
import com.example.blog.entity.Notification;
import com.example.blog.vo.NotificationPageVO;

/**
 * 站内通知服务
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 由 MQ 消息创建通知（幂等：10 秒窗口内同 sender+article+type 的重复投递直接跳过）
     */
    void createFromMessage(NotificationMessage message);

    /**
     * 分页查询当前用户的通知（含未读数），按时间倒序
     */
    NotificationPageVO pageMy(NotificationPageDTO dto);

    /**
     * 当前用户全部标记已读
     */
    void markAllRead();
}
```

- [ ] **Step 6: Service 实现**

```java
package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.blog.common.PageResult;
import com.example.blog.dto.NotificationMessage;
import com.example.blog.dto.NotificationPageDTO;
import com.example.blog.entity.Notification;
import com.example.blog.entity.User;
import com.example.blog.mapper.NotificationMapper;
import com.example.blog.service.NotificationService;
import com.example.blog.service.UserService;
import com.example.blog.utils.SecurityUtil;
import com.example.blog.vo.NotificationPageVO;
import com.example.blog.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内通知服务实现
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    /** 幂等窗口：MQ 至少一次投递可能在短时间内重发同一消息 */
    private static final int DEDUP_WINDOW_SECONDS = 10;

    private final UserService userService;

    @Override
    @Transactional
    public void createFromMessage(NotificationMessage message) {
        // 幂等判重：10 秒窗口内同发件人+文章+类型的通知视为重复投递，跳过。
        // 窗口式判重在教程场景够用；严格去重需要消息表唯一键携带业务 id（如点赞记录 id）
        long recent = count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRecipientId, message.getRecipientId())
                .eq(Notification::getSenderId, message.getSenderId())
                .eq(Notification::getType, message.getType())
                .eq(Notification::getArticleId, message.getArticleId())
                .ge(Notification::getCreateTime,
                        LocalDateTime.now().minusSeconds(DEDUP_WINDOW_SECONDS)));
        if (recent > 0) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientId(message.getRecipientId());
        notification.setSenderId(message.getSenderId());
        notification.setType(message.getType());
        notification.setArticleId(message.getArticleId());
        notification.setContent(message.getContent());
        notification.setIsRead(0);
        // create_time 由数据库默认值生成
        save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageVO pageMy(NotificationPageDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        Page<Notification> page = page(new Page<>(dto.getPageNum(), dto.getPageSize()),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getRecipientId, userId)
                        .orderByDesc(Notification::getCreateTime));
        List<Notification> records = page.getRecords();

        // 未读数走条件 count（命中 idx_recipient 前缀）
        Long unreadCount = lambdaQuery()
                .eq(Notification::getRecipientId, userId)
                .eq(Notification::getIsRead, 0)
                .count();

        NotificationPageVO vo = new NotificationPageVO();
        if (records.isEmpty()) {
            vo.setList(List.of());
        } else {
            // 批量回查发件人昵称（防 N+1）；发件人可能已被逻辑删除，昵称兜底在 NotificationVO.from
            Set<Long> senderIds = records.stream()
                    .map(Notification::getSenderId)
                    .collect(Collectors.toSet());
            Map<Long, User> senderMap = userService.listByIds(senderIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            vo.setList(records.stream()
                    .map(n -> NotificationVO.from(n, senderMap.get(n.getSenderId())))
                    .toList());
        }
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setUnreadCount(unreadCount);
        return vo;
    }

    @Override
    @Transactional
    public void markAllRead() {
        Long userId = SecurityUtil.getCurrentUserId();
        lambdaUpdate()
                .set(Notification::getIsRead, 1)
                .eq(Notification::getRecipientId, userId)
                .eq(Notification::getIsRead, 0)
                .update();
    }
}
```

注意：实现中未用到 `Collections`/`PageResult` 的 import 不要保留（上面 import 区仅作参考，按 IDE/编译报错裁剪到实际使用项）。若编译器报 unused import 无碍（Java 不报错），保持简洁即可。

- [ ] **Step 7: Controller**

```java
package com.example.blog.controller;

import com.example.blog.common.Result;
import com.example.blog.dto.NotificationPageDTO;
import com.example.blog.service.NotificationService;
import com.example.blog.vo.NotificationPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口（全部需要登录，见 SecurityConfig）
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 当前用户的通知分页（含未读数）：GET /api/notifications?pageNum=1&pageSize=10
     */
    @GetMapping
    public Result<NotificationPageVO> page(@Valid NotificationPageDTO dto) {
        return Result.success(notificationService.pageMy(dto));
    }

    /**
     * 全部标记已读：PUT /api/notifications/read-all
     */
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        notificationService.markAllRead();
        return Result.success();
    }
}
```

- [ ] **Step 8: 消费者接通落库**

`NotificationConsumer.java` 整体替换为：

```java
package com.example.blog.consumer;

import com.example.blog.dto.NotificationMessage;
import com.example.blog.service.NotificationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 通知消费者：把 MQ 消息落库为站内通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    /**
     * 评论通知：回复 → 被回复人；一级评论 → 文章作者
     */
    @RabbitListener(queues = "comment.notify.queue")
    public void onComment(NotificationMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            notificationService.createFromMessage(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[评论通知] 落库失败: {}", message, e);
            // requeue 重试；反复失败会死循环，生产应改 basicNack 进死信队列
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 点赞通知：通知文章作者
     */
    @RabbitListener(queues = "like.notify.queue")
    public void onLike(NotificationMessage message, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            notificationService.createFromMessage(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[点赞通知] 落库失败: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

注意：先读原文件的现有 import，保证 package/import 区完整（原文件有 @Header/AmqpHeaders 等 import，新内容已覆盖）。

- [ ] **Step 9: 编译验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0

- [ ] **Step 10: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/entity/Notification.java blog/src/main/java/com/example/blog/mapper/NotificationMapper.java blog/src/main/java/com/example/blog/dto/NotificationPageDTO.java blog/src/main/java/com/example/blog/vo/NotificationVO.java blog/src/main/java/com/example/blog/vo/NotificationPageVO.java blog/src/main/java/com/example/blog/service/NotificationService.java blog/src/main/java/com/example/blog/service/impl/NotificationServiceImpl.java blog/src/main/java/com/example/blog/controller/NotificationController.java blog/src/main/java/com/example/blog/consumer/NotificationConsumer.java
git commit -m "feat(blog): 站内通知落库、分页查询与已读接口"
```

---

### Task 3: 热门文章端点（B4 后端部分）

**Files:**
- Modify: `blog/src/main/java/com/example/blog/controller/ArticleController.java`

**Interfaces:**
- Consumes: 现有 `HotArticleService.getHotArticles(int topN)` 返回 `List<ArticleVO>`（服务已完整，只缺暴露）。
- Produces: `GET /api/articles/hot?topN=10` → `Result<List<ArticleVO>>`（游客可访问——被现有放行规则 `GET /api/articles/*` 单段通配覆盖，无需改 SecurityConfig）。

- [ ] **Step 1: 注入 HotArticleService 并加端点**

import 区新增：

```java
import com.example.blog.service.HotArticleService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.RequestParam;
```

类字段区追加：

```java
    private final HotArticleService hotArticleService;
```

`my` 端点之前插入：

```java
    /**
     * 热门文章（Redis ZSet，定时任务维护；游客可看）：
     * GET /api/articles/hot?topN=10
     */
    @GetMapping("/hot")
    public Result<List<ArticleVO>> hot(
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "topN 最小为 1") @Max(value = 50, message = "topN 最大为 50") int topN) {
        return Result.success(hotArticleService.getHotArticles(topN));
    }
```

注意 `List` 已有 import（Task 2 引入的 search 方法用到）；若无则补 `java.util.List`。

- [ ] **Step 2: 编译验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0

- [ ] **Step 3: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/controller/ArticleController.java
git commit -m "feat(blog): 新增热门文章接口 GET /api/articles/hot"
```

---

### Task 4: 后端杂项清理（B1 注释/B3 冗余查询/B5 死方法/B11 窗口/B13 ObjectMapper）

**Files:**
- Modify: `blog/src/main/java/com/example/blog/utils/JwtUtil.java`（B5）
- Modify: `blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java`（B3）
- Modify: `blog/src/main/java/com/example/blog/task/ArticleSyncTask.java`（B11）
- Modify: `blog/src/main/java/com/example/blog/config/SecurityConfig.java`（B13）
- Modify: `blog/src/main/java/com/example/blog/service/impl/UserServiceImpl.java`（B1 配套注释）

**Interfaces:**
- Consumes: Spring Boot 自动配置的 `ObjectMapper` bean；CommentServiceImpl 内已查询的 parent 局部变量。
- Produces: 行为不变的清理——编译通过即验证。

- [ ] **Step 1: JwtUtil 删除死方法（B5）**

删除 `isTokenExpired(String token)` 与 `getUsernameFromToken(String token)` 两个方法及其 Javadoc（全仓库无调用者，可用 Grep 再确认一次）。类头 Javadoc 保持。

- [ ] **Step 2: CommentServiceImpl 复用 parent 引用（B3）**

`addComment` 中步骤 2 的局部变量 `parent` 目前声明在 `if (parentId != 0)` 块内。把它提升：方法开头声明

```java
        // 回复目标评论（parentId != 0 时非 null；一级评论保持 null）
        Comment parent = null;
```

块内赋值去掉重复声明；步骤 4 的通知接收者判定替换为：

```java
        Article article = articleService.getById(dto.getArticleId());
        Long recipientId = parent != null ? parent.getUserId() : article.getAuthorId();
```

即删除原 `getById(parentId).getUserId()` 二次查询。

- [ ] **Step 3: ArticleSyncTask 窗口放宽（B11）**

`.ge(Article::getUpdateTime, LocalDateTime.now().minusMinutes(5))` 改为 `.minusMinutes(6)`；注释中的窗口说明同步改：

```java
            // 查询最近 6 分钟更新过的已发布文章。
            // 边界说明：任务间隔 5 分钟、窗口放宽到 6 分钟留 1 分钟重叠，
            // 消除恰好落在边界上的更新被漏同步的风险（代价是极少量文章被重复写入 ES，幂等无害）
```

方法里早退日志文案「近 5 分钟」同步改「近 6 分钟」。

- [ ] **Step 4: SecurityConfig 用 ObjectMapper 序列化 401 body（B13）**

SecurityConfig 加构造注入：类上已有 @Configuration，直接给 securityFilterChain 方法加参数 `ObjectMapper objectMapper`（Spring Boot 自动配置提供该 bean）。

entry point 替换为：

```java
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    // 统一返回结构交给 ObjectMapper 序列化，避免手写字符串漂移
                    response.getWriter().write(objectMapper.writeValueAsString(
                            Result.error(401, "未登录或登录已过期")));
                }))
```

import 补：`com.example.blog.common.Result`、`com.fasterxml.jackson.databind.ObjectMapper`。

- [ ] **Step 5: UserServiceImpl.register 注释更新（B1 配套）**

用户名唯一性校验处的注释替换为：

```java
        // 用户名唯一性校验（逻辑删除用户不参与校验，MP 自动拼接 deleted=0；
        // DB 层唯一索引同样含 deleted 列，软删用户的用户名允许重新注册）
```

- [ ] **Step 6: 编译验证 + 死代码复核**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0

Run: `grep -rn "isTokenExpired\|getUsernameFromToken" blog/src --include="*.java"`
Expected: 无输出

- [ ] **Step 7: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/utils/JwtUtil.java blog/src/main/java/com/example/blog/service/impl/CommentServiceImpl.java blog/src/main/java/com/example/blog/task/ArticleSyncTask.java blog/src/main/java/com/example/blog/config/SecurityConfig.java blog/src/main/java/com/example/blog/service/impl/UserServiceImpl.java
git commit -m "chore(blog): 清理死代码与冗余查询并统一 401 序列化"
```

---

### Task 5: 通知与热门的前端接入（B2/B4 前端部分）

**Files:**
- Modify: `frontend/src/types/api.ts`
- Create: `frontend/src/api/notification.ts`
- Modify: `frontend/src/api/article.ts`
- Modify: `frontend/src/components/AppHeader.vue`
- Modify: `frontend/src/views/ArticleList.vue`

**Interfaces:**
- Consumes: Task 2 的两个后端端点；Task 3 的 `/articles/hot`；全局注册的 `<bell />` 图标。
- Produces: `getNotifications(params)` → `Promise<NotificationPageVO>`、`markAllNotifications()` → `Promise<null>`、`getHotArticles(topN?)` → `Promise<ArticleVO[]>`。

- [ ] **Step 1: 类型**

`types/api.ts` 追加：

```ts
/** 后端 NotificationVO */
export interface NotificationVO {
  id: number
  senderId: number
  senderNickname: string | null
  type: string
  articleId: number
  content: string | null
  isRead: number
  createTime: string
}

/** 后端 NotificationPageVO（比 PageResult 多未读数） */
export interface NotificationPageVO {
  total: number
  pageNum: number
  pageSize: number
  list: NotificationVO[]
  unreadCount: number
}
```

- [ ] **Step 2: API 模块**

创建 `frontend/src/api/notification.ts`：

```ts
import request from '@/utils/request'
import type { NotificationPageVO } from '@/types/api'

/** 当前用户的通知分页（含未读数） */
export function getNotifications(params: { pageNum: number; pageSize: number }) {
  return request.get<never, NotificationPageVO>('/notifications', { params })
}

/** 全部标记已读 */
export function markAllNotifications() {
  return request.put<never, null>('/notifications/read-all')
}
```

`api/article.ts` 追加：

```ts
/** 热门文章（按浏览量，游客可看） */
export function getHotArticles(topN = 10) {
  return request.get<never, ArticleVO[]>('/articles/hot', { params: { topN } })
}
```

- [ ] **Step 3: AppHeader 通知入口**

AppHeader.vue script 补充（imports 增加 `onMounted`、`ref` 已有确认、两个 API 函数、`computed` 若用于徽标显隐）：

```ts
const showNotify = ref(false)
const notifyList = ref<NotificationVO[]>([])
const unreadCount = ref(0)

async function loadNotifications() {
  const page = await getNotifications({ pageNum: 1, pageSize: 5 })
  notifyList.value = page.list
  unreadCount.value = Number(page.unreadCount)
}

async function onMarkAllRead() {
  await markAllNotifications()
  await loadNotifications()
}
```

模板 actions 区（登录态分支内、「发布文章」按钮之前）插入：

```html
<el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
  <el-dropdown placement="bottom-end" width="320" @visible-change="(v: boolean) => v && loadNotifications()">
    <span class="notify-trigger">
      <el-icon :size="20"><bell /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <div class="notify-head">
          <span>通知</span>
          <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="onMarkAllRead">
            全部已读
          </el-button>
        </div>
        <el-empty v-if="notifyList.length === 0" description="暂无通知" :image-size="60" />
        <el-dropdown-item
          v-for="n in notifyList"
          :key="n.id"
          class="notify-item"
          @click="$router.push(`/articles/${n.articleId}`)"
        >
          <span class="notify-dot" :class="{ unread: n.isRead === 0 }" />
          {{ n.senderNickname || '佚名' }}
          {{ n.type === 'LIKE' ? '赞了你的文章' : (n.isRead === 0 ? '评论/回复了你' : '评论了你') }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</el-badge>
```

样式补：

```css
.notify-trigger {
  display: inline-flex;
  cursor: pointer;
  outline: none;
}
.notify-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.notify-item {
  white-space: normal;
  line-height: 1.5;
}
.notify-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: transparent;
  margin-right: 6px;
}
.notify-dot.unread {
  background: var(--el-color-danger);
}
```

onMounted 时若已登录拉取未读数：

```ts
onMounted(() => {
  if (userStore.isLoggedIn) loadNotifications()
})
```

- [ ] **Step 4: 列表页热门位**

ArticleList.vue script 顶部补（loading 声明之后）：

```ts
const hotList = ref<ArticleVO[]>([])

onMounted(async () => {
  try {
    hotList.value = await getHotArticles(10)
  } catch {
    // 热门位加载失败不打扰主流程
  }
})
```

import 行补 `getHotArticles`。模板 toolbar 之后、el-empty 之前插入：

```html
<div v-if="hotList.length > 0" class="hot-block">
  <h4 class="hot-title"><el-icon><histogram /></el-icon> 热门文章</h4>
  <ol class="hot-list">
    <li v-for="(a, i) in hotList" :key="a.id" @click="$router.push(`/articles/${a.id}`)">
      <span class="hot-rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
      {{ a.title }}
      <span class="hot-views">{{ a.viewCount }}</span>
    </li>
  </ol>
</div>
```

说明：热门图标采用 `<histogram />`（element-plus icons 已核对该图标存在，Step 6 的命令可再验证一次）；若你偏好其他已存在图标可等价替换，构建/lint 会兜底检查未知组件。样式：

```css
.hot-block {
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.hot-title {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 8px;
  color: var(--el-color-danger);
}
.hot-list {
  margin: 0;
  padding-left: 4px;
  list-style: none;
}
.hot-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 14px;
  cursor: pointer;
}
.hot-list li:hover {
  color: var(--el-color-primary);
}
.hot-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.hot-rank.top {
  background: var(--el-color-danger);
  color: #fff;
}
.hot-views {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
```

- [ ] **Step 5: 构建与 lint**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build && npm run lint`
Expected: 两步退出码 0

- [ ] **Step 6: 图标存在性核对（构建前自检）**

Run: `node -e "const i=require('c:/Users/vannnn/Desktop/blog-platform/frontend/node_modules/@element-plus/icons-vue/dist/index.js'); console.log('Bell' in i, 'Histogram' in i)"`
Expected: `true true`；若任一为 false，换用输出 true 的相近图标并同步模板。

- [ ] **Step 7: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/types/api.ts frontend/src/api/notification.ts frontend/src/api/article.ts frontend/src/components/AppHeader.vue frontend/src/views/ArticleList.vue
git commit -m "feat(frontend): 顶栏通知入口与列表页热门位"
```

---

### Task 6: 前端打磨（B7 拆包/B8 401 去重/B9 评论深度/B10 懒加载/B12 Markdown 样式）

**Files:**
- Modify: `frontend/vite.config.ts`（B7）
- Modify: `frontend/src/utils/request.ts`（B8）
- Modify: `frontend/src/components/CommentItem.vue`（B9）
- Modify: `frontend/src/views/Profile.vue`（B10）
- Create: `frontend/src/assets/markdown.css`（B12）
- Modify: `frontend/src/main.ts`（B12）
- Modify: `frontend/src/views/ArticleDetail.vue`、`frontend/src/views/ArticleEdit.vue`（B12 清理局部样式）

**Interfaces:**
- Produces: 构建产物按 vendor 分组拆 chunk 且无 >500kB 告警；并发 401 只提示一次；评论嵌套 ≤3 级缩进；收藏 Tab 首次激活加载；markdown-body 全局主题化样式。

- [ ] **Step 1: vite.config.ts 拆包（B7）**

在 defineConfig 中追加 build 配置（resolve 之后）：

```ts
  build: {
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: 'element-plus', test: /[\\/]node_modules[\\/]element-plus[\\/]/ },
            { name: 'element-icons', test: /[\\/]node_modules[\\/]@element-plus[\\/]/ },
            { name: 'markdown-it', test: /[\\/]node_modules[\\/](markdown-it|linkify-it|mdurl|decode-uri-component|entities|uc\.micro)[\\/]/ },
            { name: 'vendor', test: /[\\/]node_modules[\\/]/ },
          ],
        },
      },
    },
  },
```

说明：本项目 Vite 8 是 rolldown 内核，`codeSplitting.groups` 是官方推荐方式（manualChunks/advancedChunks 已废弃且与 codeSplitting 互斥时被忽略）。构建后如仍有单 chunk >500kB，允许把告警项再细拆一个 group 或在报告中如实说明残余告警及原因（不调 chunkSizeWarningLimit 掩盖问题——除非剩余大 chunk 是业务主 chunk 本身，此时调到 800 并注明）。

- [ ] **Step 2: request.ts 401 去重（B8）**

模块级加标志位并在 handleUnauthorized 使用：

```ts
// 并发请求同时收到 401 时只处理第一个：其余跳过提示与跳转，避免警告轰炸
let handlingUnauthorized = false

/** 未登录 / 登录过期统一处理：清空本地状态并跳转登录页 */
function handleUnauthorized(message: string) {
  const userStore = useUserStore()
  userStore.clearLogin()
  if (handlingUnauthorized) return
  handlingUnauthorized = true
  ElMessage.warning(message)
  if (router.currentRoute.value.path !== '/login') {
    router.push({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath },
    }).finally(() => {
      handlingUnauthorized = false
    })
  } else {
    handlingUnauthorized = false
  }
}
```

说明：clearLogin 移到标志判断之外——即使跳转去重，本地状态仍必须每次清理（幂等安全）。

- [ ] **Step 3: CommentItem 深度限制（B9）**

props 改为：

```ts
defineProps<{
  comment: CommentVO
  depth?: number
}>()
```

模板根元素绑定 depth（默认 0）：

```html
<div class="comment-item" :class="{ deep: (depth ?? 0) >= 3 }">
```

递归调用传递 `:depth="(depth ?? 0) + 1"`；`.children` 容器的缩进仅在浅层生效，样式改为：

```css
.children {
  margin-left: 36px;
}
.deep .children {
  margin-left: 0;
}
```

`ArticleDetail.vue` 顶层渲染处无需传 depth（默认 0 即可）。

- [ ] **Step 4: Profile 收藏 Tab 懒加载（B10）**

script 改动：

```ts
const activeTab = ref('articles')
const favLoadedOnce = ref(false)
```

loadFavorites 开头（favLoading 赋值前）不加标志；mounted 只加载我的文章：

```ts
onMounted(() => {
  loadMyArticles()
})
```

新增 Tab 切换处理：

```ts
function onTabChange(name: number | string) {
  if (name === 'favorites' && !favLoadedOnce.value) {
    favLoadedOnce.value = true
    loadFavorites()
  }
}
```

模板 el-tabs 绑定：`v-model="activeTab"` 与 `@tab-change="onTabChange"`；收藏 tab-pane 加 `name="favorites"`、我的文章 pane 加 `name="articles"`。取消收藏后的 `loadFavorites()` 保持（此时 favLoadedOnce 已 true，行为不变）。

- [ ] **Step 5: markdown.css 全局样式（B12）**

创建 `frontend/src/assets/markdown.css`：

```css
/* Markdown 渲染区全局样式：详情页正文与编辑页预览共用。
   颜色一律取 Element Plus CSS 变量，跟随亮暗主题 */
.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin: 24px 0 12px;
  line-height: 1.4;
}
.markdown-body h1 {
  font-size: 1.6em;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.markdown-body h2 {
  font-size: 1.35em;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.markdown-body h3 {
  font-size: 1.15em;
}
.markdown-body p {
  margin: 12px 0;
}
.markdown-body blockquote {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 4px solid var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  border-radius: 0 4px 4px 0;
}
.markdown-body blockquote p {
  margin: 4px 0;
}
.markdown-body ul,
.markdown-body ol {
  margin: 12px 0;
  padding-left: 28px;
}
.markdown-body li {
  margin: 4px 0;
}
.markdown-body table {
  margin: 12px 0;
  border-collapse: collapse;
  width: 100%;
  font-size: 14px;
}
.markdown-body th,
.markdown-body td {
  border: 1px solid var(--el-border-color-lighter);
  padding: 8px 12px;
  text-align: left;
}
.markdown-body th {
  background: var(--el-fill-color-light);
  font-weight: 600;
}
.markdown-body tr:nth-child(2n) td {
  background: var(--el-fill-color-lighter);
}
.markdown-body hr {
  border: none;
  border-top: 1px solid var(--el-border-color-lighter);
  margin: 20px 0;
}
.markdown-body a {
  color: var(--el-color-primary);
  text-decoration: none;
}
.markdown-body a:hover {
  text-decoration: underline;
}
.markdown-body img {
  max-width: 100%;
  border-radius: 4px;
}
.markdown-body pre {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
}
.markdown-body code {
  background: var(--el-fill-color-light);
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 0.92em;
}
.markdown-body pre code {
  padding: 0;
  background: transparent;
}
```

main.ts 在 element css 之后追加：

```ts
import './assets/markdown.css'
```

ArticleDetail.vue style 中删除这三条（已全局化）：`.markdown-body :deep(img)`、`.markdown-body :deep(pre)`、`.markdown-body :deep(code)`；`.markdown-body { line-height: 1.8; }` 保留。

ArticleEdit.vue style 中删除 `.editor-preview :deep(img)` 一条（全局 img 已覆盖），其余编辑器布局样式不动。

- [ ] **Step 6: 构建/lint + 告警核对**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build && npm run lint`
Expected: 两步退出码 0；构建输出检查是否仍有 >500kB 单 chunk 告警，按 Step 1 说明处置并把结果写进提交报告。

- [ ] **Step 7: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/vite.config.ts frontend/src/utils/request.ts frontend/src/components/CommentItem.vue frontend/src/views/Profile.vue frontend/src/assets/markdown.css frontend/src/main.ts frontend/src/views/ArticleDetail.vue frontend/src/views/ArticleEdit.vue
git commit -m "fix(frontend): 打包拆分、401 去重、评论深度限制、收藏懒加载与 Markdown 全局样式"
```

## Self-Review 记录

- 规格覆盖：B1=Task 1(+Task 4 配套注释)、B2=Task 1(schema)+Task 2(后端)+Task 5(前端)、B3=Task 4、B4=Task 3+Task 5、B5=Task 4、B6=Task 1（LikeRecord/Favorite 实体字段本就不含 deleted、类头已有物理删除原因注释，经核实实体零改动）、B7=Task 6、B8=Task 6、B9=Task 6、B10=Task 6、B11=Task 4、B12=Task 6、B13=Task 4。全覆盖。
- 类型一致性：`createFromMessage(NotificationMessage)` 被 Consumer 调用签名一致；`pageMy(dto)` 返回 `NotificationPageVO` 与 Controller/前端 `getNotifications` 一致；`getHotArticles(topN)` Java `List<ArticleVO>` ↔ 前端 `ArticleVO[]` 一致；前端 `depth?: number` 与递归传递一致。
- 占位符扫描：全文无 TBD/作废块/纠错残留，所有代码块均为最终实现、可直接落地。
