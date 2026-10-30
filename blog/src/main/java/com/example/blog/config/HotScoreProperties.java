package com.example.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 热度分权重与统计窗口
 * <p>做成配置项而不是常量：这四个数字没有任何数据支撑，是拍脑袋定的，
 * 以后调参不该需要改代码重打包。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.hot")
public class HotScoreProperties {

    /** 浏览量权重（浏览量走高频道，不过 MQ，见 ViewCountServiceImpl） */
    private int viewWeight = 1;

    /** 点赞权重 */
    private int likeWeight = 5;

    /** 评论权重 */
    private int commentWeight = 10;

    /** 收藏权重 */
    private int favoriteWeight = 8;

    /** 统计窗口（天）：只统计这段时间内发布的文章，防止老文章长期霸榜 */
    private int windowDays = 30;

    /**
     * 按加分类型取权重
     *
     * @return 对应的权重；未知类型返回 0（加分事件被静默忽略）
     */
    public int weightOf(String type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case "LIKE" -> likeWeight;
            case "COMMENT" -> commentWeight;
            case "FAVORITE" -> favoriteWeight;
            default -> 0;
        };
    }
}