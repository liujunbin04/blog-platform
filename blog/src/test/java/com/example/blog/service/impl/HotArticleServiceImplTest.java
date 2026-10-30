package com.example.blog.service.impl;

import com.example.blog.config.HotScoreProperties;
import com.example.blog.dto.HotScoreRow;
import com.example.blog.entity.Article;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.service.AuthorAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotArticleServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private AuthorAssembler authorAssembler;

    private HotArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HotArticleServiceImpl(stringRedisTemplate, articleMapper, authorAssembler,
                new HotScoreProperties());
    }

    @Test
    void refreshBuildsTemporaryKeyThenRenamesIt() {
        HotScoreRow row = new HotScoreRow();
        row.setArticleId(7L);
        row.setScore(42D);
        when(articleMapper.selectHotScores(anyInt(), anyInt(), anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(List.of(row));
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service.refreshHotArticles();

        verify(zSetOperations).add(HotArticleServiceImpl.HOT_KEY_TEMP, "7", 42D);
        verify(stringRedisTemplate).rename(
                HotArticleServiceImpl.HOT_KEY_TEMP,
                HotArticleServiceImpl.HOT_KEY
        );
    }

    @Test
    void refreshDeletesHotKeyWhenNoArticlesInWindow() {
        when(articleMapper.selectHotScores(anyInt(), anyInt(), anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(List.of());

        service.refreshHotArticles();

        verify(stringRedisTemplate).delete(HotArticleServiceImpl.HOT_KEY);
        verify(stringRedisTemplate, never()).rename(anyString(), anyString());
    }

    @Test
    void addScoreUsesIncrementNotAdd() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service.addScore(7L, 5);

        // 必须用 incrementScore：add 会覆盖成「只有这一次的分」，把累计分数抹掉
        verify(zSetOperations).incrementScore(HotArticleServiceImpl.HOT_KEY, "7", 5D);
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void addScoreIgnoresNullOrZeroDelta() {
        service.addScore(null, 5);
        service.addScore(7L, 0);

        verify(stringRedisTemplate, never()).opsForZSet();
    }

    @Test
    void readsMoreCandidatesThanRequested() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(HotArticleServiceImpl.HOT_KEY, 0, 2L))
                .thenReturn(new LinkedHashSet<>(List.of("9", "8", "7")));

        List<Long> ids = service.getHotArticleIds(1);

        assertEquals(List.of(9L, 8L, 7L), ids);
    }

    @Test
    void countHotArticlesUsesZSetCardinality() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard(HotArticleServiceImpl.HOT_KEY)).thenReturn(12L);

        assertEquals(12L, service.countHotArticles());
    }

    @Test
    void getHotArticlesSkipsArticlesOutsideWindow() {
        // 增量加分会把窗口外的老文章写进 ZSet，取列表时必须按当前状态再筛一遍，
        // 否则半年前的文章被点一次赞就会挂上榜单
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        Set<String> ids = new LinkedHashSet<>(List.of("1", "2"));
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenReturn(ids);

        Article fresh = article(1L, LocalDateTime.now().minusDays(1));
        Article stale = article(2L, LocalDateTime.now().minusDays(90));
        when(articleMapper.selectByIds(List.of(1L, 2L))).thenReturn(List.of(fresh, stale));
        when(authorAssembler.loadUsers(any())).thenReturn(Map.of());

        List<Long> result = service.getHotArticles(2).stream().map(a -> a.getId()).toList();

        assertEquals(List.of(1L), result);
        assertTrue(result.contains(1L));
    }

    @Test
    void getHotArticlesSkipsHiddenArticles() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenReturn(new LinkedHashSet<>(List.of("1")));

        Article hidden = article(1L, LocalDateTime.now().minusDays(1));
        hidden.setHidden(1);
        when(articleMapper.selectByIds(List.of(1L))).thenReturn(List.of(hidden));
        when(authorAssembler.loadUsers(any())).thenReturn(Map.of());

        assertEquals(List.of(), service.getHotArticles(1));
    }

    private Article article(Long id, LocalDateTime createTime) {
        Article article = new Article();
        article.setId(id);
        article.setAuthorId(9L);
        article.setStatus(1);
        article.setHidden(0);
        article.setViewCount(1);
        article.setLikeCount(0);
        article.setCreateTime(createTime);
        return article;
    }
}