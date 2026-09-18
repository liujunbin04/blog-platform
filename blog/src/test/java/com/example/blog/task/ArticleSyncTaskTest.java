package com.example.blog.task;

import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.entity.User;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleService;
import com.example.blog.service.UserService;
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

    @Mock
    private UserService userService;

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

    @Test
    void syncRecentArticlesIncludesAuthorNickname() {
        Article article = new Article();
        article.setId(9L);
        article.setAuthorId(3L);
        article.setTitle("title");
        article.setContent("content");
        article.setViewCount(0);
        article.setLikeCount(0);
        article.setCreateTime(java.time.LocalDateTime.now());
        article.setUpdateTime(java.time.LocalDateTime.now());
        User author = new User();
        author.setId(3L);
        author.setNickname("tester");
        when(userService.listByIds(org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of(author));

        List<ArticleDocument> documents = task.toDocuments(List.of(article));

        org.junit.jupiter.api.Assertions.assertEquals(
                "tester",
                documents.get(0).getAuthorNickname()
        );
    }
}
