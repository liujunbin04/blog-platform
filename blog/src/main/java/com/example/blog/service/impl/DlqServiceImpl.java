package com.example.blog.service.impl;

import com.example.blog.common.BusinessException;
import com.example.blog.config.RabbitConfig;
import com.example.blog.service.DlqService;
import com.example.blog.vo.DlqSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 死信队列管理实现
 * <p>重放刻意用 {@code basicGet} + 手动 ack，而不是监听器：监听器会和自己正在
 * 监听的队列打架，重放本身也不该触发自动重试。</p>
 */
@Slf4j
@Service
public class DlqServiceImpl implements DlqService {

    private final RabbitTemplate rabbitTemplate;

    private final AmqpAdmin amqpAdmin;

    public DlqServiceImpl(RabbitTemplate rabbitTemplate, AmqpAdmin amqpAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.amqpAdmin = amqpAdmin;
    }

    @Override
    public List<DlqSummaryVO> summary() {
        List<DlqSummaryVO> result = new ArrayList<>();
        for (String queue : RabbitConfig.DEAD_LETTER_QUEUES) {
            result.add(new DlqSummaryVO(queue, messageCount(queue)));
        }
        return result;
    }

    @Override
    public int replay(String queue, int count) {
        if (!RabbitConfig.DEAD_LETTER_QUEUES.contains(queue)) {
            throw new BusinessException(400, "不是受支持的死信队列: " + queue);
        }
        if (count <= 0) {
            return 0;
        }
        int replayed = 0;
        for (int i = 0; i < count; i++) {
            // execute 的回调里取消息 + ack 在同一连接上完成
            Message message = rabbitTemplate.receive(queue, 1000);
            if (message == null) {
                break;
            }
            String target = originalQueueOf(queue);
            try {
                rabbitTemplate.send("", target, message);
                replayed++;
            } catch (Exception e) {
                // 放不回业务队列就丢掉这份副本，避免消息被静默吞掉：
                // 记日志后继续处理后续消息
                log.error("死信重放失败，消息已丢弃: queue={} messageId={}",
                        queue, message.getMessageProperties().getMessageId(), e);
            }
        }
        if (replayed > 0) {
            log.info("死信重放完成: 来源={} 条数={}", queue, replayed);
        }
        return replayed;
    }

    private long messageCount(String queue) {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(queue);
            return info == null ? 0 : info.getMessageCount();
        } catch (Exception e) {
            log.warn("读取队列信息失败: {}", queue, e);
            return 0;
        }
    }

    /**
     * 死信队列 → 原业务队列的映射（与 RabbitConfig 的绑定保持一致）
     */
    private String originalQueueOf(String dlq) {
        return switch (dlq) {
            case RabbitConfig.COMMENT_NOTIFY_DLQ -> RabbitConfig.COMMENT_NOTIFY_QUEUE;
            case RabbitConfig.LIKE_NOTIFY_DLQ -> RabbitConfig.LIKE_NOTIFY_QUEUE;
            case RabbitConfig.HOT_SCORE_DLQ -> RabbitConfig.HOT_SCORE_QUEUE;
            default -> throw new BusinessException(400, "没有对应的业务队列: " + dlq);
        };
    }
}