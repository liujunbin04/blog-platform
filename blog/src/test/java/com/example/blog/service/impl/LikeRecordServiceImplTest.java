package com.example.blog.service.impl;

import com.example.blog.service.ArticleCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeRecordServiceImplTest {

    @Mock
    private ArticleCacheService articleCacheService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private LikeRecordServiceImpl service;

    @Test
    void evictsArticleCacheAfterInteraction() {
        service.evictArticleCache(5L);

        verify(articleCacheService).evict(5L);
    }
}
