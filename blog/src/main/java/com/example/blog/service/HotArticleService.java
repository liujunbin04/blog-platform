package com.example.blog.service;

import com.example.blog.vo.ArticleVO;

import java.util.List;

/**
 * 热门文章服务（Redis ZSet）
 */
public interface HotArticleService {

    /**
     * 重建热门文章 ZSet（定时任务每 5 分钟调用一次）：
     * 从数据库取浏览量 Top 100 的已发布文章，score = 浏览量
     */
    void refreshHotArticles();

    /**
     * 取热门文章 id 列表（按热度倒序）。
     * ZSet 为空（定时任务尚未执行或正在重建）时回源数据库兜底
     */
    List<Long> getHotArticleIds(int topN);

    /**
     * 取热门文章（含作者信息），按热度倒序
     */
    List<ArticleVO> getHotArticles(int topN);
}
