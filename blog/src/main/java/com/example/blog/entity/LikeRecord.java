package com.example.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点赞记录实体（对应 like_record 表）
 * <p>注意：本实体刻意<b>不加 @TableLogic</b>。取消点赞需要物理删除记录，
 * 否则 deleted=1 的行仍占用唯一索引 (user_id, article_id)，
 * 用户再次点赞会撞索引。详见 LikeRecordServiceImpl 注释</p>
 */
@Data
@TableName("like_record")
public class LikeRecord {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点赞用户 id（user.id） */
    private Long userId;

    /** 文章 id（article.id） */
    private Long articleId;

    /** 创建时间（数据库默认值维护） */
    private LocalDateTime createTime;

    /** 更新时间（数据库 ON UPDATE 维护） */
    private LocalDateTime updateTime;
}
