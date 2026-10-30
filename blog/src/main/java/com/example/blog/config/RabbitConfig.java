package com.example.blog.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * RabbitMQ 配置：通知功能的交换机、队列与绑定
 * <p>Spring Boot 自动配置了 RabbitAdmin（AmqpAdmin），应用与 broker 建立首个连接时
 * 会自动声明这里的交换机/队列/绑定，无需手动触发</p>
 */
@Configuration
public class RabbitConfig {

    /** 交换机（topic 类型，支持通配符路由） */
    public static final String BLOG_EXCHANGE = "blog.exchange";

    /** 评论通知队列 */
    public static final String COMMENT_NOTIFY_QUEUE = "comment.notify.queue";

    /** 点赞通知队列 */
    public static final String LIKE_NOTIFY_QUEUE = "like.notify.queue";

    /** 路由键：发表评论 */
    public static final String ROUTING_COMMENT_ADD = "comment.add";

    /** 路由键：点赞 */
    public static final String ROUTING_LIKE_ADD = "like.add";

    /** 通知死信交换机：消费重试耗尽的消息转投 DLQ，避免毒消息无限 requeue */
    public static final String NOTIFY_DLX = "blog.notify.dlx";

    /** 评论通知死信队列 */
    public static final String COMMENT_NOTIFY_DLQ = "comment.notify.dlq";

    /** 点赞通知死信队列 */
    public static final String LIKE_NOTIFY_DLQ = "like.notify.dlq";

    /** 路由键：热度加分（点赞 / 评论 / 收藏共用，具体权重看消息的 type 字段） */
    public static final String ROUTING_HOT_SCORE = "hot.score";

    /** 热度加分队列 */
    public static final String HOT_SCORE_QUEUE = "hot.score.queue";

    /** 热度加分死信队列 */
    public static final String HOT_SCORE_DLQ = "hot.score.dlq";

    /** 全部死信队列：DLQ 管理端点与死信监控据此遍历 */
    public static final List<String> DEAD_LETTER_QUEUES =
            List.of(COMMENT_NOTIFY_DLQ, LIKE_NOTIFY_DLQ, HOT_SCORE_DLQ);

    /**
     * 消息转换器：JSON 序列化。
     * 默认的 SimpleMessageConverter 只支持 String/byte[]/Serializable，
     * 发送 POJO（如 NotificationMessage）会抛 IllegalArgumentException；
     * JSON 格式跨语言可读，消费者侧用同一个转换器即可按 __TypeId__ 反序列化
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange blogExchange() {
        // durable：broker 重启后交换机保留
        return new TopicExchange(BLOG_EXCHANGE, true, false);
    }

    @Bean
    public Queue commentNotifyQueue() {
        return QueueBuilder.durable(COMMENT_NOTIFY_QUEUE)
                .deadLetterExchange(NOTIFY_DLX)
                .deadLetterRoutingKey(ROUTING_COMMENT_ADD)
                .build();
    }

    @Bean
    public Queue likeNotifyQueue() {
        return QueueBuilder.durable(LIKE_NOTIFY_QUEUE)
                .deadLetterExchange(NOTIFY_DLX)
                .deadLetterRoutingKey(ROUTING_LIKE_ADD)
                .build();
    }

    @Bean
    public Binding commentNotifyBinding(Queue commentNotifyQueue, TopicExchange blogExchange) {
        return BindingBuilder.bind(commentNotifyQueue).to(blogExchange).with(ROUTING_COMMENT_ADD);
    }

    @Bean
    public Binding likeNotifyBinding(Queue likeNotifyQueue, TopicExchange blogExchange) {
        return BindingBuilder.bind(likeNotifyQueue).to(blogExchange).with(ROUTING_LIKE_ADD);
    }

    @Bean
    public Queue hotScoreQueue() {
        return QueueBuilder.durable(HOT_SCORE_QUEUE)
                .deadLetterExchange(NOTIFY_DLX)
                .deadLetterRoutingKey(ROUTING_HOT_SCORE)
                .build();
    }

    @Bean
    public Binding hotScoreBinding(Queue hotScoreQueue, TopicExchange blogExchange) {
        return BindingBuilder.bind(hotScoreQueue).to(blogExchange).with(ROUTING_HOT_SCORE);
    }

    @Bean
    public DirectExchange notifyDlx() {
        return new DirectExchange(NOTIFY_DLX, true, false);
    }

    @Bean
    public Queue hotScoreDlq() {
        return QueueBuilder.durable(HOT_SCORE_DLQ).build();
    }

    @Bean
    public Queue commentNotifyDlq() {
        return QueueBuilder.durable(COMMENT_NOTIFY_DLQ).build();
    }

    @Bean
    public Queue likeNotifyDlq() {
        return QueueBuilder.durable(LIKE_NOTIFY_DLQ).build();
    }

    @Bean
    public Binding commentNotifyDlqBinding(Queue commentNotifyDlq, DirectExchange notifyDlx) {
        return BindingBuilder.bind(commentNotifyDlq).to(notifyDlx).with(ROUTING_COMMENT_ADD);
    }

    @Bean
    public Binding likeNotifyDlqBinding(Queue likeNotifyDlq, DirectExchange notifyDlx) {
        return BindingBuilder.bind(likeNotifyDlq).to(notifyDlx).with(ROUTING_LIKE_ADD);
    }

    @Bean
    public Binding hotScoreDlqBinding(Queue hotScoreDlq, DirectExchange notifyDlx) {
        return BindingBuilder.bind(hotScoreDlq).to(notifyDlx).with(ROUTING_HOT_SCORE);
    }
}
