package com.example.blog.vo;

import com.example.blog.entity.Article;
import com.example.blog.entity.Favorite;
import com.example.blog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏列表项：收藏记录 + 文章摘要（不含正文 content）+ 作者信息
 */
@Data
public class FavoriteVO {

    /** 收藏记录 id */
    private Long favoriteId;

    /** 文章 id */
    private Long articleId;

    private String title;

    private String category;

    private String tags;

    private Integer viewCount;

    private Integer likeCount;

    /** 作者信息 */
    private AuthorVO author;

    /** 文章创建时间 */
    private LocalDateTime articleCreateTime;

    /** 收藏时间（favorite.create_time） */
    private LocalDateTime favoriteTime;

    public static FavoriteVO from(Favorite favorite, Article article, User author) {
        FavoriteVO vo = new FavoriteVO();
        vo.setFavoriteId(favorite.getId());
        vo.setArticleId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setCategory(article.getCategory());
        vo.setTags(article.getTags());
        vo.setViewCount(article.getViewCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setAuthor(author == null ? null : AuthorVO.from(author));
        vo.setArticleCreateTime(article.getCreateTime());
        vo.setFavoriteTime(favorite.getCreateTime());
        return vo;
    }
}
