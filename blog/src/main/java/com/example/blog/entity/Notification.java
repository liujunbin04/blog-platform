package com.example.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知（comment.notify.queue / like.notify.queue 消费落库）
 * <p>纯追加型数据：无逻辑删除、无更新时间，已读用 is_read 标记而非删行</p>
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收件人 id */
    private Long recipientId;

    /** 发件人 id */
    private Long senderId;

    /** 类型：COMMENT / LIKE（与 NotificationMessage 常量一致） */
    private String type;

    private Long articleId;

    /** 业务事件唯一键，唯一索引保证消息幂等。 */
    private String eventKey;

    private String content;

    /** 是否已读：0 未读 1 已读 */
    private Integer isRead;

    private LocalDateTime createTime;
}
