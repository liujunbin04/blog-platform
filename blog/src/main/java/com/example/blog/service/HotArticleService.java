package com.example.blog.service;

import com.example.blog.vo.ArticleVO;

import java.util.List;

/**
 * 热门文章服务（Redis ZSet）
 */
public interface HotArticleService {

    /**
     * 重建热门文章 ZSet（定时任务每 30 分钟调用一次）：
     * 按热度公式计算窗口内的分数并原子替换排行榜
     * <p>日常分数由 MQ 事件增量维护（{@link #addScore}），本方法是校正——
     * 增量会因为消息丢失、消费失败、Redis 重启而漂移，不能只靠增量。</p>
     */
    void refreshHotArticles();

    /**
     * 增量加分：事件驱动路径
     *
     * @param articleId 文章 id
     * @param delta     分差（权重见 HotScoreProperties）
     */
    void addScore(Long articleId, double delta);

    /**
     * 取热门文章 id 列表（按热度倒序）。
     * ZSet 为空（定时任务尚未执行或正在重建）时回源数据库兜底
     */
    List<Long> getHotArticleIds(int topN);

    /**
     * 当前有效热门文章总数，用于分页。
     */
    long countHotArticles();

    /**
     * 取热门文章（含作者信息），按热度倒序
     */
    List<ArticleVO> getHotArticles(int topN);
}