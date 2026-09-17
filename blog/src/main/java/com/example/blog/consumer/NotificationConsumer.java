package com.example.blog.consumer;

import com.example.blog.dto.NotificationMessage;
import com.example.blog.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 通知消费者：把 MQ 消息落库为站内通知
 * <p>不手动 ack/nack：异常直接抛给容器，按 spring.rabbitmq.listener.simple.retry
 * 自动重试；超过 max-attempts 后按 default-requeue-rejected=false 拒绝，
 * 经死信交换机进入对应 DLQ，避免毒消息无限 requeue 死循环</p>
 */
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    /**
     * 评论通知：回复 → 被回复人；一级评论 → 文章作者
     */
    @RabbitListener(queues = "comment.notify.queue")
    public void onComment(NotificationMessage message) {
        notificationService.createFromMessage(message);
    }

    /**
     * 点赞通知：通知文章作者
     */
    @RabbitListener(queues = "like.notify.queue")
    public void onLike(NotificationMessage message) {
        notificationService.createFromMessage(message);
    }
}
