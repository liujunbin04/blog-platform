package com.example.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热度加分事件（RabbitMQ blog.exchange 投递）
 * <p>只有「加分」事件，没有减分事件：取消点赞/收藏、删除评论造成的分数偏高，
 * 由每 30 分钟一次的 {@code refreshHotArticles} 全量重建校正。这是有意的取舍——
 * 减分事件会让消息量翻倍，而它的影响最多持续一个重建周期。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotScoreMessage implements EventMessage {

    /** 加分类型，决定加多少分（见 HotScoreProperties） */
    private String type;

    /** 被加分的文章 id */
    private Long articleId;

    /** 业务事件唯一键，例如 LIKE:{likeRecordId} / COMMENT:{commentId} / HOT_FAV:{favoriteId} */
    private String eventKey;

    public static final String TYPE_LIKE = "LIKE";

    public static final String TYPE_COMMENT = "COMMENT";

    public static final String TYPE_FAVORITE = "FAVORITE";

    /**
     * 按类型构造事件
     */
    public static HotScoreMessage of(String type, Long articleId, String eventKey) {
        return new HotScoreMessage(type, articleId, eventKey);
    }
}