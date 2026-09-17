package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.blog.entity.Article;
import com.example.blog.vo.ArticleVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 文章 Mapper
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 文章详情：自定义 SQL 关联 user 表取作者昵称、头像
     * <p>SQL 见 resources/mapper/ArticleMapper.xml；返回带嵌套 author 的 ArticleVO</p>
     */
    ArticleVO selectDetailWithAuthor(@Param("id") Long id);

    /**
     * 物理删除文章行（文章删除功能为硬删除）：
     * removeById 受 @TableLogic 影响只会 UPDATE deleted=1，这里用自定义 DELETE 绕开
     */
    @Delete("DELETE FROM article WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);

    /**
     * 把浏览量直接设为指定值（定时任务回写用）
     * <p>用绝对值覆盖而非累加：Redis 里存的是全量值，数据库只是定期落盘的副本。
     * 若用 SET view_count = view_count + N，一旦回写任务重复执行就会重复累加</p>
     */
    @Update("UPDATE article SET view_count = #{count} WHERE id = #{id}")
    int updateViewCount(@Param("id") Long id, @Param("count") long count);
}
