package com.example.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章实体（对应 article 表）
 */
@Data
@TableName("article")
public class Article {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 作者 id（user.id） */
    private Long authorId;

    /** 分类 */
    private String category;

    /** 标签，多个以逗号分隔 */
    private String tags;

    /** 浏览量 */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 状态：0 草稿 1 已发布 */
    private Integer status;

    /** 逻辑删除：0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;

    /** 创建时间（由数据库默认值 CURRENT_TIMESTAMP 维护） */
    private LocalDateTime createTime;

    /** 更新时间（由数据库 ON UPDATE CURRENT_TIMESTAMP 维护） */
    private LocalDateTime updateTime;
}
