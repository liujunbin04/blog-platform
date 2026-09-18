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
        // 1. 校验文章存在且公开：草稿 / 已隐藏的文章不可互动，防止绕过可见性
        Article article = articleService.requirePublicArticle(dto.getArticleId());
        // 2. 解析回复目标：replyToId 为空表示一级评论；
        //    非空时校验目标评论存在且属于同一篇文章，父评论（挂树用）由目标推导：
        //    目标是一级评论则父评论=目标本身，否则父评论=目标的父评论（统一挂到一级评论下）
        Comment target = null;
        if (dto.getReplyToId() != null) {
            target = getById(dto.getReplyToId());
            if (target == null) {
                throw new BusinessException(404, "被回复的评论不存在");
            }
            if (!target.getArticleId().equals(dto.getArticleId())) {
                throw new BusinessException(400, "被回复的评论不属于该文章");
            }
        }
        Long parentId = target == null ? 0L
                : (target.getParentId() == 0 ? target.getId() : target.getParentId());
        // 3. 插入评论
        Long senderId = SecurityUtil.getCurrentUserId();
        Comment comment = new Comment();
        comment.setArticleId(dto.getArticleId());
        comment.setUserId(senderId);
        comment.setParentId(parentId);
        comment.setReplyToId(target == null ? 0L : target.getId());
        comment.setReplyToUserId(target == null ? null : target.getUserId());
        comment.setContent(dto.getContent());
        save(comment);
        // 4. 通知消息：回复时通知被回复评论的作者（而非挂树的父评论作者，否则
        //    回复楼中楼某条评论时通知会发错人），一级评论通知文章作者（自己操作自己文章不发）。
        //    必须在事务提交后再投递：事务内直发的话消息可能先于提交到达消费者，
        //    消费者查库会查不到刚插入的评论
        Long recipientId = target != null ? target.getUserId() : article.getAuthorId();
        if (!recipientId.equals(senderId)) {
            NotificationMessage message = new NotificationMessage(
                    NotificationMessage.TYPE_COMMENT,
                    recipientId,
                    senderId,
                    dto.getArticleId(),
                    dto.getContent(),
                    "COMMENT:" + comment.getId()
            );
            sendAfterCommit(RabbitConfig.ROUTING_COMMENT_ADD, message);
        }
        // 回读（create_time 由数据库默认值生成，需重新查询才能返回）
        CommentVO vo = CommentVO.from(getById(comment.getId()), currentUser());
        // 单条返回也带上被回复人昵称，与评论树接口的展示保持一致
        if (target != null) {
            User replyToUser = userService.getById(target.getUserId());
            vo.setReplyToNickname(replyToUser == null ? null : replyToUser.getNickname());
        }
        return vo;
    }

    @Override
    @Transactional
    public void deleteComment(Long id) {
        Comment comment = getById(id);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        if (!comment.getUserId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(403, "只能删除自己的评论");
        }
        // 逻辑删除：保住评论树结构上下文（子评论回复目标仍可解析出「佚名」），
        // 父评论被删后其子评论由 buildTree 提升为一级评论，避免整条链消失
        removeById(id);
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
        Article article = articleService.getById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        // 公开文章对所有人可读；草稿 / 已隐藏的文章仅作者本人可看评论
        // （详情页允许作者查看自己的受限文章，评论树需保持一致），其余按不存在处理
        boolean restricted = article.getStatus() == null
                || article.getStatus() != ArticleServiceImpl.STATUS_PUBLISHED
                || (article.getHidden() != null && article.getHidden() == 1);
        if (restricted) {
            Long userId = currentUserIdOrNull();
            if (userId == null || !userId.equals(article.getAuthorId())) {
                throw new BusinessException(404, "文章不存在");
            }
        }
        // 一次查出该文章全部评论（按时间正序），再批量查评论者，最后在内存中组装树
        List<Comment> comments = lambdaQuery()
                .eq(Comment::getArticleId, articleId)
                .orderByAsc(Comment::getCreateTime)
                .list();
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        // 评论者 + 被回复人一次批量查（防 N+1）；被回复人 id 直接存在评论行上，
        // 被回复的评论被删除后昵称仍可解析
        Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        comments.stream()
                .map(Comment::getReplyToUserId)
                .filter(id -> id != null && id != 0)
                .forEach(userIds::add);
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<CommentVO> vos = comments.stream()
                .map(c -> CommentVO.from(c, userMap.get(c.getUserId())))
                .toList();
        fillReplyToNicknames(vos, userMap);
        return buildTree(vos);
    }

    /**
     * 填充「被回复人昵称」：按 reply_to_user_id 从批量查好的 userMap 取；
     * 被回复人已注销（逻辑删除）时昵称为 null，前端兜底显示「佚名」
     */
    private void fillReplyToNicknames(List<CommentVO> vos, Map<Long, User> userMap) {
        for (CommentVO vo : vos) {
            if (vo.getReplyToUserId() == null || vo.getReplyToUserId() == 0) {
                continue;
            }
            User author = userMap.get(vo.getReplyToUserId());
            vo.setReplyToNickname(author == null ? null : author.getNickname());
        }
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

    /**
     * 取当前登录用户 id，匿名访客返回 null（评论树是公开接口，需处理匿名场景）
     */
    private Long currentUserIdOrNull() {
        try {
            return SecurityUtil.getCurrentUserId();
        } catch (BusinessException e) {
            return null;
        }
    }
}
