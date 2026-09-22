package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.entity.User;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleVisibilityPolicy;
import com.example.blog.service.AuthorAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSearchSyncServiceImplTest {

    @Mock
    private ArticleSearchRepository repository;

    @Mock
    private AuthorAssembler authorAssembler;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private ArticleVisibilityPolicy visibilityPolicy;

    @InjectMocks
    private ArticleSearchSyncServiceImpl service;

    @Test
    void syncAllIncludesAuthorNickname() {
        Article article = article();
        User author = new User();
        author.setId(3L);
        author.setNickname("tester");
        when(authorAssembler.loadUsers(List.of(3L))).thenReturn(Map.of(3L, author));

        service.syncAll(List.of(article));

        verify(repository).saveAll(argThat(documents ->
                documents.iterator().hasNext()
                        && "tester".equals(documents.iterator().next().getAuthorNickname())
        ));
    }

    private Article article() {
        Article article = new Article();
        article.setId(9L);
        article.setAuthorId(3L);
        article.setTitle("title");
        article.setContent("content");
        article.setViewCount(0);
        article.setLikeCount(0);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        return article;
    }
}
