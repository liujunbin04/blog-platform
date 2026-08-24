package com.example.blog.controller;

import com.example.blog.common.PageResult;
import com.example.blog.common.Result;
import com.example.blog.dto.FavoritePageDTO;
import com.example.blog.service.FavoriteService;
import com.example.blog.vo.FavoriteVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口（全部需要登录，见 SecurityConfig）
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 收藏：POST /api/favorites/{articleId}
     */
    @PostMapping("/{articleId}")
    public Result<Void> add(@PathVariable Long articleId) {
        favoriteService.addFavorite(articleId);
        return Result.success();
    }

    /**
     * 取消收藏：DELETE /api/favorites/{articleId}
     */
    @DeleteMapping("/{articleId}")
    public Result<Void> remove(@PathVariable Long articleId) {
        favoriteService.removeFavorite(articleId);
        return Result.success();
    }

    /**
     * 查询当前用户是否已收藏：GET /api/favorites/{articleId}/status
     */
    @GetMapping("/{articleId}/status")
    public Result<Boolean> status(@PathVariable Long articleId) {
        return Result.success(favoriteService.hasFavorited(articleId));
    }

    /**
     * 当前用户的收藏列表（按收藏时间倒序）：GET /api/favorites?pageNum=1&pageSize=10
     */
    @GetMapping
    public Result<PageResult<FavoriteVO>> page(@Valid FavoritePageDTO dto) {
        return Result.success(favoriteService.pageFavorites(dto));
    }
}
