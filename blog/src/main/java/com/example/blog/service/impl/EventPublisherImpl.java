package com.example.blog.service.impl;

import com.example.blog.config.RabbitConfig;
import com.example.blog.dto.EventMessage;
import com.example.blog.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事件投递实现
 * <p>四个业务服务（评论/点赞/收藏/通知）都走这里，避免各自复制一份
 * {@code TransactionSynchronization} 样板代码。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisherImpl implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishAfterCommit(String routingKey, EventMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(routingKey, message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(routingKey, message);
            }
        });
    }

    @Override
    public void publish(String routingKey, EventMessage message) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.BLOG_EXCHANGE, routingKey, message);
            log.debug("事件已投递: {} {}", routingKey, message);
        } catch (Exception e) {
            // 投递失败不影响主流程（通知/热度都是最终一致）。broker 侧的 nack
            // 由 ConfirmCallback 另行记录，这里只兜住「连接不上」这类同步异常
            log.error("事件投递失败: {} {}", routingKey, message, e);
        }
    }
}