package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.service.ArticleService;
import com.example.blog.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotArticleServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ArticleService articleService;

    @Mock
    private UserService userService;

    @InjectMocks
    private HotArticleServiceImpl service;

    @Test
    void refreshBuildsTemporaryKeyThenRenamesIt() {
        Article article = new Article();
        article.setId(7L);
        article.setViewCount(42);
        HotArticleServiceImpl spyService = spy(service);
        doReturn(List.of(article)).when(spyService).loadTopArticles();
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        spyService.refreshHotArticles();

        verify(zSetOperations).add(
                HotArticleServiceImpl.HOT_KEY_TEMP,
                "7",
                42D
        );
        verify(stringRedisTemplate).rename(
                HotArticleServiceImpl.HOT_KEY_TEMP,
                HotArticleServiceImpl.HOT_KEY
        );
    }
}
