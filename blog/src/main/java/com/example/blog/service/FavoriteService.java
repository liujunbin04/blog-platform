package com.example.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.blog.common.PageResult;
import com.example.blog.dto.FavoritePageDTO;
import com.example.blog.entity.Favorite;
import com.example.blog.vo.FavoriteVO;

/**
 * 收藏服务
 */
public interface FavoriteService extends IService<Favorite> {

    /**
     * 收藏文章（重复收藏返回 400）
     */
    void addFavorite(Long articleId);

    /**
     * 取消收藏（未收藏返回 400）
     */
    void removeFavorite(Long articleId);

    /**
     * 分页查询当前用户的收藏列表（按收藏时间倒序，返回文章摘要）
     */
    PageResult<FavoriteVO> pageFavorites(FavoritePageDTO dto);

    /**
     * 当前用户是否已收藏某文章
     */
    boolean hasFavorited(Long articleId);
}
