package com.example.blog.controller;

import com.example.blog.common.Result;
import com.example.blog.service.LikeRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞接口（全部需要登录，见 SecurityConfig）
 */
@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeRecordController {

    private final LikeRecordService likeRecordService;

    /**
     * 点赞：POST /api/likes/{articleId}
     */
    @PostMapping("/{articleId}")
    public Result<Void> like(@PathVariable Long articleId) {
        likeRecordService.like(articleId);
        return Result.success();
    }

    /**
     * 取消点赞：DELETE /api/likes/{articleId}
     */
    @DeleteMapping("/{articleId}")
    public Result<Void> unlike(@PathVariable Long articleId) {
        likeRecordService.unlike(articleId);
        return Result.success();
    }

    /**
     * 查询当前用户是否已点赞：GET /api/likes/{articleId}/status
     */
    @GetMapping("/{articleId}/status")
    public Result<Boolean> status(@PathVariable Long articleId) {
        return Result.success(likeRecordService.hasLiked(articleId));
    }
}
