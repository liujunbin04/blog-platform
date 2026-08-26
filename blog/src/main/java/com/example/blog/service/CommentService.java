package com.example.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.blog.dto.CommentDTO;
import com.example.blog.entity.Comment;
import com.example.blog.vo.CommentVO;

import java.util.List;

/**
 * 评论服务
 */
public interface CommentService extends IService<Comment> {

    /**
     * 发表评论 / 回复评论（parentId 为空表示一级评论）
     */
    CommentVO addComment(CommentDTO dto);

    /**
     * 查询某篇文章的评论树：
     * 返回一级评论列表，每个一级评论的 children 里挂子评论（支持多级嵌套）
     */
    List<CommentVO> getCommentTree(Long articleId);
}
