package com.example.blog.service;

import com.example.blog.entity.Article;
import org.springframework.stereotype.Component;

/**
 * 文章公开性和作者可见性规则，集中所有“什么算公开文章”的判断。
 */
@Component
public class ArticleVisibilityPolicy {

    public boolean isDraft(Article article) {
        return article == null || article.getStatus() == null || article.getStatus() == 0;
    }

    public boolean isHidden(Article article) {
        return article == null || (article.getHidden() != null && article.getHidden() == 1);
    }

    public boolean isPublic(Article article) {
        return article != null
                && article.getStatus() != null
                && article.getStatus() == 1
                && (article.getHidden() == null || article.getHidden() == 0);
    }

    public boolean isVisibleTo(Article article, Long userId) {
        return isPublic(article)
                || (article != null && userId != null && userId.equals(article.getAuthorId()));
    }
}
