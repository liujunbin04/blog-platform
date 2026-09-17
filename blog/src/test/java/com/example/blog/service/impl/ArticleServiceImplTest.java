package com.example.blog.service.impl;

import com.example.blog.common.BusinessException;
import com.example.blog.entity.Article;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.mapper.CommentMapper;
import com.example.blog.mapper.FavoriteMapper;
import com.example.blog.mapper.LikeRecordMapper;
import com.example.blog.mapper.NotificationMapper;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleCacheService;
import com.example.blog.service.UserService;
import com.example.blog.service.ViewCountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * requirePublicArticle 可见性闸门：评论 / 点赞 / 收藏入口共用的存在性与公开性校验
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceImplTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private UserService userService;

    @Mock
    private ArticleSearchRepository articleSearchRepository;

    @Mock
    private ArticleCacheService articleCacheService;

    @Mock
    private ViewCountService viewCountService;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private LikeRecordMapper likeRecordMapper;

    @Mock
    private FavoriteMapper favoriteMapper;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private ArticleServiceImpl articleService;

    @BeforeEach
    void setUp() {
        // ServiceImpl 的 baseMapper 由 MyBatis-Plus 注入，单测中手动装配
        ReflectionTestUtils.setField(articleService, "baseMapper", articleMapper);
    }

    @Test
    void requirePublicArticleReturnsPublishedVisibleArticle() {
        Article article = new Article();
        article.setId(1L);
        article.setStatus(ArticleServiceImpl.STATUS_PUBLISHED);
        article.setHidden(0);
        when(articleMapper.selectById(1L)).thenReturn(article);

        assertSame(article, articleService.requirePublicArticle(1L));
    }

    @Test
    void requirePublicArticleThrows404WhenMissing() {
        when(articleMapper.selectById(1L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> articleService.requirePublicArticle(1L));
        assertEquals(404, e.getCode());
    }

    @Test
    void requirePublicArticleThrows404ForDraft() {
        Article article = new Article();
        article.setId(1L);
        article.setStatus(ArticleServiceImpl.STATUS_DRAFT);
        article.setHidden(0);
        when(articleMapper.selectById(1L)).thenReturn(article);

        BusinessException e = assertThrows(BusinessException.class,
                () -> articleService.requirePublicArticle(1L));
        assertEquals(404, e.getCode());
    }

    @Test
    void requirePublicArticleThrows404ForHidden() {
        Article article = new Article();
        article.setId(1L);
        article.setStatus(ArticleServiceImpl.STATUS_PUBLISHED);
        article.setHidden(1);
        when(articleMapper.selectById(1L)).thenReturn(article);

        BusinessException e = assertThrows(BusinessException.class,
                () -> articleService.requirePublicArticle(1L));
        assertEquals(404, e.getCode());
    }
}
