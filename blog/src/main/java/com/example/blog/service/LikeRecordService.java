package com.example.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.blog.entity.LikeRecord;

/**
 * 点赞记录服务
 */
public interface LikeRecordService extends IService<LikeRecord> {

    /**
     * 点赞（重复点赞返回 400）
     */
    void like(Long articleId);

    /**
     * 取消点赞（未点赞返回 400）
     */
    void unlike(Long articleId);

    /**
     * 当前用户是否已点赞某文章
     */
    boolean hasLiked(Long articleId);
}
