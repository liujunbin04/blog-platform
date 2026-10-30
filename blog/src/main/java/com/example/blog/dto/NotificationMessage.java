package com.example.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知消息体（RabbitMQ blog.exchange 投递）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements EventMessage {

    /** 消息类型：COMMENT / LIKE */
    private String type;

    /** 接收者用户 id（文章作者或被回复人） */
    private Long recipientId;

    /** 触发通知的用户 id（评论者/点赞者） */
    private Long senderId;

    /** 文章 id（用于跳转） */
    private Long articleId;

    /** 通知内容摘要 */
    private String content;

    /** 业务事件唯一键，例如 LIKE:{likeRecordId} / COMMENT:{commentId} */
    private String eventKey;

    public static final String TYPE_COMMENT = "COMMENT";

    public static final String TYPE_LIKE = "LIKE";
}
