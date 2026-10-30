package com.example.blog.service.impl;

import com.example.blog.mapper.ArticleMapper;
import com.example.blog.service.HotArticleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewCountServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private HotArticleService hotArticleService;

    @InjectMocks
    private ViewCountServiceImpl service;

    @Test
    void incrementUsesDatabaseBaselineAndIncrements() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("blog:view:count:9", "5")).thenReturn(true);
        when(valueOperations.increment("blog:view:count:9")).thenReturn(6L);

        long value = service.incrementAndGet(9L, 5);

        assertEquals(6L, value);
        // 浏览量直接加热度，不经过 MQ
        verify(hotArticleService).addScore(9L, 1);
    }
}
