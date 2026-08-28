package com.example.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论实体（对应 comment 表）
 */
@Data
@TableName("comment")
public class Comment {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文章 id（article.id） */
    private Long articleId;

    /** 评论者 id（user.id） */
    private Long userId;

    /** 父评论 id，0 表示一级评论（多级回复统一挂到所属一级评论下） */
    private Long parentId;

    /** 被回复的评论 id，0 表示一级评论；用于展示「回复@被回复人」与确定通知对象 */
    private Long replyToId;

    /** 被回复人的用户 id，null 表示一级评论（见 design.md 评论表定义）。
     *  直接存用户 id：被回复的评论即使被删，回复目标信息仍可解析 */
    private Long replyToUserId;

    /** 评论内容 */
    private String content;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;

    /** 创建时间（数据库默认值维护） */
    private LocalDateTime createTime;

    /** 更新时间（数据库 ON UPDATE 维护） */
    private LocalDateTime updateTime;
}
