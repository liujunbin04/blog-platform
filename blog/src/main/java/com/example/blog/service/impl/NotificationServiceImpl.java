package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.blog.dto.NotificationMessage;
import com.example.blog.dto.NotificationPageDTO;
import com.example.blog.entity.Notification;
import com.example.blog.entity.User;
import com.example.blog.mapper.NotificationMapper;
import com.example.blog.service.NotificationService;
import com.example.blog.service.UserService;
import com.example.blog.utils.SecurityUtil;
import com.example.blog.vo.NotificationPageVO;
import com.example.blog.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内通知服务实现
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    private final UserService userService;

    @Override
    @Transactional
    public void createFromMessage(NotificationMessage message) {
        Notification notification = new Notification();
        notification.setRecipientId(message.getRecipientId());
        notification.setSenderId(message.getSenderId());
        notification.setType(message.getType());
        notification.setEventKey(message.getEventKey());
        notification.setArticleId(message.getArticleId());
        // content 取自评论原文，CommentDTO 允许 1000 字而本列为 VARCHAR(500)：
        // 超长会插库失败并经 basicNack(requeue) 变成毒消息死循环，截断兜底
        String content = message.getContent();
        notification.setContent(content == null ? null
                : content.substring(0, Math.min(content.length(), 500)));
        notification.setIsRead(0);
        // create_time 由数据库默认值生成
        try {
            save(notification);
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // 唯一索引保证同一业务事件只写入一次
        }
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageVO pageMy(NotificationPageDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        Page<Notification> page = page(new Page<>(dto.getPageNum(), dto.getPageSize()),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getRecipientId, userId)
                        .orderByDesc(Notification::getCreateTime));
        List<Notification> records = page.getRecords();

        // 未读数走条件 count（命中 idx_recipient 前缀）
        Long unreadCount = lambdaQuery()
                .eq(Notification::getRecipientId, userId)
                .eq(Notification::getIsRead, 0)
                .count();

        NotificationPageVO vo = new NotificationPageVO();
        if (records.isEmpty()) {
            vo.setList(List.of());
        } else {
            // 批量回查发件人昵称（防 N+1）；发件人可能已被逻辑删除，昵称兜底在 NotificationVO.from
            Set<Long> senderIds = records.stream()
                    .map(Notification::getSenderId)
                    .collect(Collectors.toSet());
            Map<Long, User> senderMap = userService.listByIds(senderIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            vo.setList(records.stream()
                    .map(n -> NotificationVO.from(n, senderMap.get(n.getSenderId())))
                    .toList());
        }
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setUnreadCount(unreadCount);
        return vo;
    }

    @Override
    @Transactional
    public void markAllRead() {
        Long userId = SecurityUtil.getCurrentUserId();
        lambdaUpdate()
                .set(Notification::getIsRead, 1)
                .eq(Notification::getRecipientId, userId)
                .eq(Notification::getIsRead, 0)
                .update();
    }

    @Override
    @Transactional
    public void clearAll() {
        // 物理删除（notification 表无逻辑删除），只删收件人是自己的通知
        Long userId = SecurityUtil.getCurrentUserId();
        remove(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRecipientId, userId));
    }
}
