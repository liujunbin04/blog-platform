package com.example.blog.consumer;

import com.example.blog.config.HotScoreProperties;
import com.example.blog.dto.HotScoreMessage;
import com.example.blog.service.HotArticleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotScoreConsumerTest {

    @Mock
    private HotArticleService hotArticleService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private HotScoreConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new HotScoreConsumer(hotArticleService, new HotScoreProperties(), stringRedisTemplate);
    }

    @Test
    void firstDeliveryAddsWeightedScore() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(HotScoreConsumer.DEDUP_KEY_PREFIX + "LIKE:1"),
                anyString(), eq(HotScoreConsumer.DEDUP_TTL))).thenReturn(true);

        consumer.onScore(HotScoreMessage.of(HotScoreMessage.TYPE_LIKE, 7L, "LIKE:1"));

        verify(hotArticleService).addScore(7L, 5);
    }

    @Test
    void duplicateDeliveryIsIgnored() {
        // ZINCRBY 不幂等，重试必须靠 SETNX 挡住，否则一条点赞会加两次分
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        consumer.onScore(HotScoreMessage.of(HotScoreMessage.TYPE_LIKE, 7L, "LIKE:1"));

        verify(hotArticleService, never()).addScore(anyLong(), anyDouble());
    }

    @Test
    void unknownTypeIsIgnoredAfterDedupKeyIsTaken() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        consumer.onScore(HotScoreMessage.of("BOGUS", 7L, "BOGUS:1"));

        verify(hotArticleService, never()).addScore(anyLong(), anyDouble());
    }

    @Test
    void incompleteMessageIsIgnoredBeforeTouchingRedis() {
        consumer.onScore(HotScoreMessage.of(HotScoreMessage.TYPE_LIKE, null, "LIKE:1"));
        consumer.onScore(HotScoreMessage.of(HotScoreMessage.TYPE_LIKE, 7L, null));

        verify(stringRedisTemplate, never()).opsForValue();
        verify(hotArticleService, never()).addScore(anyLong(), anyDouble());
    }
}