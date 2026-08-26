package com.example.blog.vo;

import com.example.blog.entity.Notification;
import com.example.blog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知列表项（含发件人昵称）
 */
@Data
public class NotificationVO {

    private Long id;

    private Long senderId;

    private String senderNickname;

    private String type;

    private Long articleId;

    private String content;

    private Integer isRead;

    private LocalDateTime createTime;

    public static NotificationVO from(Notification n, User sender) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setSenderId(n.getSenderId());
        vo.setSenderNickname(sender == null ? "佚名" : sender.getNickname());
        vo.setType(n.getType());
        vo.setArticleId(n.getArticleId());
        vo.setContent(n.getContent());
        vo.setIsRead(n.getIsRead());
        vo.setCreateTime(n.getCreateTime());
        return vo;
    }
}
