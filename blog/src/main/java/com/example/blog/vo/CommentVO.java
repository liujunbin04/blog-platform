package com.example.blog.vo;

import com.example.blog.entity.Comment;
import com.example.blog.entity.User;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论返回结果（含评论者信息与子评论列表）
 */
@Data
public class CommentVO {

    private Long id;

    private Long articleId;

    /** 父评论 id，0 表示一级评论 */
    private Long parentId;

    private String content;

    /** 评论者信息（复用 AuthorVO：id + 昵称 + 头像） */
    private AuthorVO author;

    private LocalDateTime createTime;

    /** 子评论列表：一级评论的子评论挂在这里，回复本身为空列表 */
    private List<CommentVO> children;

    /**
     * 组装评论 + 评论者信息
     *
     * @param comment 评论
     * @param user    评论者（可能为 null，如评论者已被逻辑删除）
     */
    public static CommentVO from(Comment comment, User user) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);
        vo.setAuthor(user == null ? null : AuthorVO.from(user));
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
