package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.service.ArticleService;
import com.example.blog.vo.ArticleSearchVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSearchVisibilityServiceImplTest {

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private ArticleSearchVisibilityServiceImpl service;

    @Test
    void removesHiddenDocumentsFromVisibleResult() {
        ArticleSearchVO hidden = new ArticleSearchVO();
        hidden.setId(1L);
        ArticleSearchVO visible = new ArticleSearchVO();
        visible.setId(2L);

        Article hiddenArticle = new Article();
        hiddenArticle.setId(1L);
        hiddenArticle.setStatus(1);
        hiddenArticle.setHidden(1);
        Article visibleArticle = new Article();
        visibleArticle.setId(2L);
        visibleArticle.setStatus(1);
        visibleArticle.setHidden(0);
        when(articleService.listByIds(anyCollection()))
                .thenReturn(List.of(hiddenArticle, visibleArticle));

        List<ArticleSearchVO> result = service.filterVisible(List.of(hidden, visible));

        assertEquals(List.of(2L), result.stream().map(ArticleSearchVO::getId).toList());
    }
}
