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

    /** 幂等窗口：MQ 至少一次投递可能在短时间内重发同一消息 */
    private static final int DEDUP_WINDOW_SECONDS = 10;

    private final UserService userService;

    @Override
    @Transactional
    public void createFromMessage(NotificationMessage message) {
        // 幂等判重：10 秒窗口内同发件人+文章+类型的通知视为重复投递，跳过。
        // 窗口式判重在教程场景够用；严格去重需要消息表唯一键携带业务 id（如点赞记录 id）
        long recent = count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRecipientId, message.getRecipientId())
                .eq(Notification::getSenderId, message.getSenderId())
                .eq(Notification::getType, message.getType())
                .eq(Notification::getArticleId, message.getArticleId())
                .ge(Notification::getCreateTime,
                        LocalDateTime.now().minusSeconds(DEDUP_WINDOW_SECONDS)));
        if (recent > 0) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientId(message.getRecipientId());
        notification.setSenderId(message.getSenderId());
        notification.setType(message.getType());
        notification.setArticleId(message.getArticleId());
        // content 取自评论原文，CommentDTO 允许 1000 字而本列为 VARCHAR(500)：
        // 超长会插库失败并经 basicNack(requeue) 变成毒消息死循环，截断兜底
        String content = message.getContent();
        notification.setContent(content == null ? null
                : content.substring(0, Math.min(content.length(), 500)));
        notification.setIsRead(0);
        // create_time 由数据库默认值生成
        save(notification);
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
}
