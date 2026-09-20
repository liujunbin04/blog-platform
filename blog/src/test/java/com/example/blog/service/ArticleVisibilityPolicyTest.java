package com.example.blog.service;

import com.example.blog.entity.Article;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleVisibilityPolicyTest {

    private final ArticleVisibilityPolicy policy = new ArticleVisibilityPolicy();

    @Test
    void publishedVisibleArticleIsPublic() {
        Article article = article(1, 0);

        assertTrue(policy.isPublic(article));
    }

    @Test
    void draftOrHiddenArticleIsNotPublic() {
        assertFalse(policy.isPublic(article(0, 0)));
        assertFalse(policy.isPublic(article(1, 1)));
        assertFalse(policy.isPublic(article(null, 0)));
    }

    @Test
    void privateArticleIsVisibleToAuthorOnly() {
        Article article = article(1, 1);
        article.setAuthorId(9L);

        assertTrue(policy.isVisibleTo(article, 9L));
        assertFalse(policy.isVisibleTo(article, 10L));
        assertFalse(policy.isVisibleTo(article, null));
    }

    private Article article(Integer status, Integer hidden) {
        Article article = new Article();
        article.setStatus(status);
        article.setHidden(hidden);
        return article;
    }
}
