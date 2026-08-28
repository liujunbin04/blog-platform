package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.blog.entity.Comment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 评论 Mapper
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 物理删除某篇文章的全部评论（文章硬删除时清理关联数据，
     * @Delete 自定义 SQL 不受 @TableLogic 影响）
     */
    @Delete("DELETE FROM comment WHERE article_id = #{articleId}")
    int deleteByArticleId(@Param("articleId") Long articleId);
}
