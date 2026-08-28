package com.example.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.blog.dto.NotificationMessage;
import com.example.blog.dto.NotificationPageDTO;
import com.example.blog.entity.Notification;
import com.example.blog.vo.NotificationPageVO;

/**
 * 站内通知服务
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 由 MQ 消息创建通知（幂等：10 秒窗口内同 sender+article+type 的重复投递直接跳过）
     */
    void createFromMessage(NotificationMessage message);

    /**
     * 分页查询当前用户的通知（含未读数），按时间倒序
     */
    NotificationPageVO pageMy(NotificationPageDTO dto);

    /**
     * 当前用户全部标记已读
     */
    void markAllRead();

    /**
     * 清空当前用户的全部通知（物理删除，不可恢复）
     */
    void clearAll();
}
