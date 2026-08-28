package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.blog.entity.LikeRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点赞记录 Mapper
 */
@Mapper
public interface LikeRecordMapper extends BaseMapper<LikeRecord> {

    /**
     * 物理删除某篇文章的全部点赞记录（文章硬删除时清理关联数据，
     * @Delete 自定义 SQL 不受 @TableLogic 影响）
     */
    @Delete("DELETE FROM like_record WHERE article_id = #{articleId}")
    int deleteByArticleId(@org.apache.ibatis.annotations.Param("articleId") Long articleId);
}
