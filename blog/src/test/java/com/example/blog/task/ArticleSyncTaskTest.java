package com.example.blog.task;

import com.example.blog.entity.Article;
import com.example.blog.service.ArticleService;
import com.example.blog.service.ArticleSearchSyncService;
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
    private ArticleSearchSyncService articleSearchSyncService;

    @InjectMocks
    private ArticleSyncTask task;

    @Test
    void backfillDeletesStalePrivateDocumentBeforeUpsert() {
        when(articleService.lambdaQuery()).thenThrow(new RuntimeException("stop after reconciliation"));

        task.backfillSearchIndex();

        verify(articleSearchSyncService).reconcileVisibility();
    }
}
