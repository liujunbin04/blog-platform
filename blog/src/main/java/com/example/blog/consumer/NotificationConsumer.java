package com.example.blog.consumer;

import com.example.blog.dto.NotificationMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 通知消息消费者
 * <p>监听评论/点赞通知队列。JSON 反序列化由生产者侧同一个
 * Jackson2JsonMessageConverter 完成（按消息头 __TypeId__ 还原 NotificationMessage）</p>
 * <p>当前只打印日志；后续落库站内信时，在 TODO 处插入 notification 表
 * （设计文档的 uk_dedup 唯一索引做幂等），并把 basicAck 换成手动确认模式</p>
 */
@Slf4j
@Component
public class NotificationConsumer {

    /**
     * 评论通知：回复 → 被回复人；一级评论 → 文章作者
     */
    @RabbitListener(queues = "comment.notify.queue")
    public void onComment(NotificationMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("[评论通知] {} 给 {} 留言: {}（文章 id={}）",
                    message.getSenderId(), message.getRecipientId(),
                    message.getContent(), message.getArticleId());
            // TODO 站内信落库：notificationMapper.insert(...)，uk_dedup 冲突则忽略（幂等）
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[评论通知] 处理失败: {}", message, e);
            // requeue 重试；反复失败会死循环，落库版应改 basicNack 进死信队列
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
            log.info("[点赞通知] {} 点赞了 {} 的文章（文章 id={}）",
                    message.getSenderId(), message.getRecipientId(), message.getArticleId());
            // TODO 站内信落库同上
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[点赞通知] 处理失败: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
