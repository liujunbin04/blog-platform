package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.config.RabbitConfig;
import com.example.blog.dto.NotificationMessage;
import com.example.blog.entity.Article;
import com.example.blog.entity.LikeRecord;
import com.example.blog.mapper.LikeRecordMapper;
import com.example.blog.service.ArticleService;
import com.example.blog.service.LikeRecordService;
import com.example.blog.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 点赞服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeRecordServiceImpl extends ServiceImpl<LikeRecordMapper, LikeRecord> implements LikeRecordService {

    private final ArticleService articleService;

    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public void like(Long articleId) {
        // 1. 校验文章存在（MP 自动过滤已删除文章）
        if (articleService.getById(articleId) == null) {
            throw new BusinessException(404, "文章不存在");
        }
        Long userId = SecurityUtil.getCurrentUserId();
        // 2. 先查后插：常规重复点赞在这里拦截，给出友好提示
        long count = count(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getArticleId, articleId));
        if (count > 0) {
            throw new BusinessException(400, "请勿重复点赞");
        }
        // 3. 插入点赞记录：并发场景下唯一索引 uk_user_article 是最终防线，
        //    两个请求同时通过步骤 2 时，只有一个能插入成功，另一个撞索引抛
        //    DuplicateKeyException，同样转成友好提示
        LikeRecord record = new LikeRecord();
        record.setUserId(userId);
        record.setArticleId(articleId);
        try {
            save(record);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "请勿重复点赞");
        }
        // 4. 文章点赞数 +1（原子自增；与步骤 3 同一事务，保证记录与计数一致，
        //    任何一步失败整体回滚）
        articleService.lambdaUpdate()
                .setSql("like_count = like_count + 1")
                .eq(Article::getId, articleId)
                .update();
        // 5. 通知文章作者（自己赞自己文章不发）。事务提交后再投递，
        //    避免消息先于提交到达、消费者查库查不到点赞记录
        Article article = articleService.getById(articleId);
        if (article != null && !article.getAuthorId().equals(userId)) {
            NotificationMessage message = new NotificationMessage(
                    NotificationMessage.TYPE_LIKE,
                    article.getAuthorId(),
                    userId,
                    articleId,
                    "你的文章收到一个新点赞"
            );
            sendAfterCommit(RabbitConfig.ROUTING_LIKE_ADD, message);
        }
    }

    /**
     * 事务提交后投递 MQ 消息（事务回滚则不发送）
     */
    private void sendAfterCommit(String routingKey, NotificationMessage message) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(RabbitConfig.BLOG_EXCHANGE, routingKey, message);
                    log.debug("通知消息已投递: {}", message);
                } catch (Exception e) {
                    // 通知失败不影响主流程，记日志即可（生产可用 MQ 发送确认 + 重试）
                    log.error("通知消息投递失败: {}", message, e);
                }
            }
        });
    }

    @Override
    @Transactional
    public void unlike(Long articleId) {
        if (articleService.getById(articleId) == null) {
            throw new BusinessException(404, "文章不存在");
        }
        Long userId = SecurityUtil.getCurrentUserId();
        // 1. 物理删除点赞记录。
        //    为什么不用逻辑删除？LikeRecord 没有 @TableLogic —— 若取消点赞只把
        //    deleted 置 1，唯一索引 (user_id, article_id) 仍被占坑，
        //    用户再次点赞会撞索引；点赞记录是"关系"而非"内容"，物理删除是正确选择
        boolean removed = remove(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getArticleId, articleId));
        if (!removed) {
            throw new BusinessException(400, "尚未点赞，无法取消");
        }
        // 2. 点赞数 -1（IF 兜底不为负；与步骤 1 同一事务）
        articleService.lambdaUpdate()
                .setSql("like_count = IF(like_count > 0, like_count - 1, 0)")
                .eq(Article::getId, articleId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasLiked(Long articleId) {
        return count(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, SecurityUtil.getCurrentUserId())
                .eq(LikeRecord::getArticleId, articleId)) > 0;
    }
}
