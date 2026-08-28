package com.example.blog.vo;

import com.example.blog.entity.Article;
import com.example.blog.entity.User;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

/**
 * 文章返回结果（含作者昵称、头像）
 */
@Data
public class ArticleVO {

    private Long id;

    private String title;

    private String content;

    private String category;

    private String tags;

    private Integer viewCount;

    private Integer likeCount;

    private Integer status;

    /** 是否隐藏：0 公开 1 隐藏（仅作者可见此状态的文章） */
    private Integer hidden;

    /** 作者信息（关联 user 表） */
    private AuthorVO author;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 组装文章 + 作者信息
     *
     * @param article 文章
     * @param author  作者（可能为 null，如作者已被逻辑删除）
     */
    public static ArticleVO from(Article article, User author) {
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);
        vo.setAuthor(author == null ? null : AuthorVO.from(author));
        return vo;
    }
}
