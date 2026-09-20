package com.example.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.blog.dto.CommentDTO;
import com.example.blog.dto.CommentPageDTO;
import com.example.blog.entity.Comment;
import com.example.blog.vo.CommentVO;
import com.example.blog.vo.CommentPageVO;

import java.util.List;

/**
 * 评论服务
 */
public interface CommentService extends IService<Comment> {

    /**
     * 发表评论 / 回复评论（replyToId 为空表示一级评论）
     */
    CommentVO addComment(CommentDTO dto);

    /**
     * 删除自己的评论（逻辑删除）
     */
    void deleteComment(Long id);

    /**
     * 分页查询一级评论，子回复随当前页返回。
     */
    CommentPageVO pageComments(CommentPageDTO dto);
}
