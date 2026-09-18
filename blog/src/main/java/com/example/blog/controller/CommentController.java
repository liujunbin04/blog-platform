package com.example.blog.controller;

import com.example.blog.common.Result;
import com.example.blog.dto.CommentDTO;
import com.example.blog.dto.CommentPageDTO;
import com.example.blog.service.CommentService;
import com.example.blog.vo.CommentVO;
import com.example.blog.vo.CommentPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评论接口（全部需要登录，见 SecurityConfig）
 */
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 发表评论 / 回复评论（replyToId 为空表示一级评论）
     */
    @PostMapping
    public Result<CommentVO> add(@Valid @RequestBody CommentDTO dto) {
        return Result.success(commentService.addComment(dto));
    }

    /**
     * 删除自己的评论（逻辑删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.deleteComment(id);
        return Result.success();
    }

    /**
     * 某篇文章的评论树：GET /api/comments?articleId=1
     */
    @GetMapping
    public Result<List<CommentVO>> tree(@RequestParam Long articleId) {
        return Result.success(commentService.getCommentTree(articleId));
    }

    /**
     * 一级评论分页：GET /api/comments/page?articleId=1&pageNum=1&pageSize=20
     */
    @GetMapping("/page")
    public Result<CommentPageVO> page(@Valid CommentPageDTO dto) {
        return Result.success(commentService.pageComments(dto));
    }
}
