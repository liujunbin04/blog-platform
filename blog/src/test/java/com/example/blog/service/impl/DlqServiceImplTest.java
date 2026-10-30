package com.example.blog.service.impl;

import com.example.blog.common.BusinessException;
import com.example.blog.config.RabbitConfig;
import com.example.blog.vo.DlqSummaryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqServiceImplTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private AmqpAdmin amqpAdmin;

    @Test
    void summaryReportsEveryDeadLetterQueue() {
        when(amqpAdmin.getQueueInfo(anyString())).thenReturn(new QueueInformation("q", 3, 0));

        List<DlqSummaryVO> result = new DlqServiceImpl(rabbitTemplate, amqpAdmin).summary();

        assertEquals(RabbitConfig.DEAD_LETTER_QUEUES.size(), result.size());
        assertEquals(3L, result.get(0).getMessageCount());
    }

    @Test
    void summaryToleratesMissingQueue() {
        when(amqpAdmin.getQueueInfo(anyString())).thenReturn(null);

        List<DlqSummaryVO> result = new DlqServiceImpl(rabbitTemplate, amqpAdmin).summary();

        assertEquals(0L, result.get(0).getMessageCount());
    }

    @Test
    void replayMovesMessagesBackToOriginalQueue() {
        Message message = new Message("{}".getBytes(), new MessageProperties());
        when(rabbitTemplate.receive(RabbitConfig.HOT_SCORE_DLQ, 1000)).thenReturn(message, (Message) null);

        int replayed = new DlqServiceImpl(rabbitTemplate, amqpAdmin)
                .replay(RabbitConfig.HOT_SCORE_DLQ, 10);

        assertEquals(1, replayed);
        // 回到业务队列而不是交换机：消息已经带过 routingKey，直接投队列不会再次走死信判定
        verify(rabbitTemplate).send("", RabbitConfig.HOT_SCORE_QUEUE, message);
    }

    @Test
    void replayStopsAtCount() {
        Message message = new Message("{}".getBytes(), new MessageProperties());
        when(rabbitTemplate.receive(RabbitConfig.COMMENT_NOTIFY_DLQ, 1000)).thenReturn(message);

        int replayed = new DlqServiceImpl(rabbitTemplate, amqpAdmin)
                .replay(RabbitConfig.COMMENT_NOTIFY_DLQ, 2);

        assertEquals(2, replayed);
    }

    @Test
    void replayRejectsQueueNameThatIsNotADeadLetterQueue() {
        DlqServiceImpl service = new DlqServiceImpl(rabbitTemplate, amqpAdmin);

        // 任意队列名都能传进来就等于给了「从业务队列偷消息」的能力
        assertThrows(BusinessException.class, () -> service.replay("hot.score.queue", 1));
        verify(rabbitTemplate, never()).receive(anyString(), anyInt());
    }

    @Test
    void replayReturnsZeroForNonPositiveCount() {
        DlqServiceImpl service = new DlqServiceImpl(rabbitTemplate, amqpAdmin);

        assertEquals(0, service.replay(RabbitConfig.HOT_SCORE_DLQ, 0));
        verify(rabbitTemplate, never()).receive(eq(RabbitConfig.HOT_SCORE_DLQ), anyInt());
    }
}