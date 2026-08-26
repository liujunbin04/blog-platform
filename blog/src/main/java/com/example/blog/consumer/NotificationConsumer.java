package com.example.blog.consumer;

import com.example.blog.dto.NotificationMessage;
import com.example.blog.service.NotificationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
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
