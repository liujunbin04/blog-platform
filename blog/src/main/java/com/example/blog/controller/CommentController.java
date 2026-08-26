package com.example.blog.controller;

import com.example.blog.common.Result;
import com.example.blog.dto.CommentDTO;
import com.example.blog.service.CommentService;
import com.example.blog.vo.CommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
     * 发表评论 / 回复评论（parentId 为空表示一级评论）
     */
    @PostMapping
    public Result<CommentVO> add(@Valid @RequestBody CommentDTO dto) {
        return Result.success(commentService.addComment(dto));
    }

    /**
     * 某篇文章的评论树：GET /api/comments?articleId=1
     */
    @GetMapping
    public Result<List<CommentVO>> tree(@RequestParam Long articleId) {
        return Result.success(commentService.getCommentTree(articleId));
    }
}
