package com.example.blog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.config.RabbitConfig;
import com.example.blog.dto.CommentDTO;
import com.example.blog.dto.NotificationMessage;
import com.example.blog.entity.Article;
import com.example.blog.entity.Comment;
import com.example.blog.entity.User;
import com.example.blog.mapper.CommentMapper;
import com.example.blog.service.ArticleService;
import com.example.blog.service.CommentService;
import com.example.blog.service.UserService;
import com.example.blog.utils.SecurityUtil;
import com.example.blog.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final ArticleService articleService;
    private final UserService userService;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public CommentVO addComment(CommentDTO dto) {
        // 1. 校验文章存在（MP 自动过滤已删除文章，查不到即视为不存在）
        if (articleService.getById(dto.getArticleId()) == null) {
            throw new BusinessException(404, "文章不存在");
        }
        // 2. 回复评论：校验父评论存在且属于同一篇文章
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        // 回复目标评论（parentId != 0 时非 null；一级评论保持 null）
        Comment parent = null;
        if (parentId != 0) {
            parent = getById(parentId);
            if (parent == null) {
                throw new BusinessException(404, "父评论不存在");
            }
            if (!parent.getArticleId().equals(dto.getArticleId())) {
                throw new BusinessException(400, "父评论不属于该文章");
            }
        }
        // 3. 插入评论
        Long senderId = SecurityUtil.getCurrentUserId();
        Comment comment = new Comment();
        comment.setArticleId(dto.getArticleId());
        comment.setUserId(senderId);
        comment.setParentId(parentId);
        comment.setContent(dto.getContent());
        save(comment);
        // 4. 通知消息：回复时通知被回复人，一级评论通知文章作者（自己操作自己文章不发）。
        //    必须在事务提交后再投递：事务内直发的话消息可能先于提交到达消费者，
        //    消费者查库会查不到刚插入的评论
        Article article = articleService.getById(dto.getArticleId());
        Long recipientId = parent != null ? parent.getUserId() : article.getAuthorId();
        if (!recipientId.equals(senderId)) {
            NotificationMessage message = new NotificationMessage(
                    NotificationMessage.TYPE_COMMENT,
                    recipientId,
                    senderId,
                    dto.getArticleId(),
                    dto.getContent()
            );
            String routingKey = parentId != 0
                    ? RabbitConfig.ROUTING_COMMENT_ADD
                    : RabbitConfig.ROUTING_COMMENT_ADD; // 当前统一 comment.add；如需区分回复可扩展 reply.add
            sendAfterCommit(routingKey, message);
        }
        // 回读（create_time 由数据库默认值生成，需重新查询才能返回）
        return CommentVO.from(getById(comment.getId()), currentUser());
    }

    /**
     * 事务提交后投递 MQ 消息（事务回滚则不发送）
     */
    private void sendAfterCommit(String routingKey, NotificationMessage message) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(RabbitConfig.BLOG_EXCHANGE, routingKey, message);
                    log.debug("通知消息已投递: {}", message);
                } catch (Exception e) {
                    // 通知失败不影响主流程，记日志即可（生产可用 MQ 发送确认 + 重试）
                    log.error("通知消息投递失败: {}", message, e);
                }
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentVO> getCommentTree(Long articleId) {
        if (articleService.getById(articleId) == null) {
            throw new BusinessException(404, "文章不存在");
        }
        // 一次查出该文章全部评论（按时间正序），再批量查评论者，最后在内存中组装树
        List<Comment> comments = lambdaQuery()
                .eq(Comment::getArticleId, articleId)
                .orderByAsc(Comment::getCreateTime)
                .list();
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<CommentVO> vos = comments.stream()
                .map(c -> CommentVO.from(c, userMap.get(c.getUserId())))
                .toList();
        return buildTree(vos);
    }

    /**
     * 内存中组装树：parentId=0 的为一级评论；
     * 父评论已被逻辑删除的回复，提升为一级评论（避免整条子评论链消失）
     */
    private List<CommentVO> buildTree(List<CommentVO> vos) {
        Map<Long, CommentVO> byId = vos.stream()
                .collect(Collectors.toMap(CommentVO::getId, Function.identity()));
        Map<Long, List<CommentVO>> childrenMap = new HashMap<>();
        List<CommentVO> roots = new ArrayList<>();
        for (CommentVO vo : vos) {
            if (vo.getParentId() != 0 && byId.containsKey(vo.getParentId())) {
                childrenMap.computeIfAbsent(vo.getParentId(), k -> new ArrayList<>()).add(vo);
            } else {
                roots.add(vo);
            }
        }
        attachChildren(roots, childrenMap);
        return roots;
    }

    /**
     * 递归挂载子评论（支持多级回复；若想限制为两级，可在此截断）
     */
    private void attachChildren(List<CommentVO> parents, Map<Long, List<CommentVO>> childrenMap) {
        for (CommentVO parent : parents) {
            List<CommentVO> children = childrenMap.get(parent.getId());
            if (children != null && !children.isEmpty()) {
                parent.setChildren(children);
                attachChildren(children, childrenMap);
            }
        }
    }

    /**
     * 当前登录用户（从认证上下文直接取，见 SecurityUtil）
     */
    private User currentUser() {
        return userService.getById(SecurityUtil.getCurrentUserId());
    }
}
