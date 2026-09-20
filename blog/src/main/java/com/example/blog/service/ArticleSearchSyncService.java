package com.example.blog.service;

import com.example.blog.entity.Article;

import java.util.List;

/**
 * Elasticsearch 文章索引的唯一同步入口。
 */
public interface ArticleSearchSyncService {

    void sync(Article article);

    void remove(Long articleId);

    void syncAll(List<Article> articles);

    void reconcileVisibility();
}
