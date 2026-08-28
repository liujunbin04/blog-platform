package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.blog.entity.Favorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏记录 Mapper
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 物理删除某篇文章的全部收藏（文章硬删除时清理关联数据，
     * @Delete 自定义 SQL 不受 @TableLogic 影响）
     */
    @Delete("DELETE FROM favorite WHERE article_id = #{articleId}")
    int deleteByArticleId(@org.apache.ibatis.annotations.Param("articleId") Long articleId);
}
