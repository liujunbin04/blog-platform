package com.example.blog.vo;

import lombok.Data;

import java.util.List;

/**
 * 通知分页返回（比 PageResult 多一个未读数，顶栏徽标一次请求拿齐）
 */
@Data
public class NotificationPageVO {

    private Long total;

    private Long pageNum;

    private Long pageSize;

    private List<NotificationVO> list;

    /** 当前用户未读数 */
    private Long unreadCount;
}
