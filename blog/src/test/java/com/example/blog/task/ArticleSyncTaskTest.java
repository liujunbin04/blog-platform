package com.example.blog.task;

import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleService;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSyncTaskTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private ArticleSearchRepository articleSearchRepository;

    @InjectMocks
    private ArticleSyncTask task;

    @Test
    void backfillDeletesStalePrivateDocumentBeforeUpsert() {
        ArticleDocument stale = new ArticleDocument();
        stale.setId(8L);
        when(articleSearchRepository.findAll()).thenReturn(List.of(stale));
        when(articleService.getById(8L)).thenReturn(null);
        when(articleService.lambdaQuery()).thenThrow(new RuntimeException("stop after reconciliation"));

        task.backfillSearchIndex();

        verify(articleSearchRepository).deleteById(8L);
    }
}
