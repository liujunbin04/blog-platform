package com.example.blog.controller;

import com.example.blog.common.Result;
import com.example.blog.dto.NotificationPageDTO;
import com.example.blog.service.NotificationService;
import com.example.blog.vo.NotificationPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口（全部需要登录，见 SecurityConfig）
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 当前用户的通知分页（含未读数）：GET /api/notifications?pageNum=1&pageSize=10
     */
    @GetMapping
    public Result<NotificationPageVO> page(@Valid NotificationPageDTO dto) {
        return Result.success(notificationService.pageMy(dto));
    }

    /**
     * 全部标记已读：PUT /api/notifications/read-all
     */
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        notificationService.markAllRead();
        return Result.success();
    }

    /**
     * 清空全部通知：DELETE /api/notifications（物理删除，不可恢复）
     */
    @DeleteMapping
    public Result<Void> clear() {
        notificationService.clearAll();
        return Result.success();
    }
}
