package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.blog.dto.HotScoreRow;
import com.example.blog.entity.Article;
import com.example.blog.vo.ArticleVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章 Mapper
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 全文搜索：标题/正文走 ngram FULLTEXT，标签走 LIKE，按相关度倒序
     * <p>SQL 见 resources/mapper/ArticleMapper.xml。可见性（已发布 + 未隐藏）直接写在
     * WHERE 中，结果无需再回表过滤</p>
     *
     * @param keyword 已转义 LIKE 通配符的关键词
     */
    IPage<Article> searchByFullText(IPage<Article> page, @Param("keyword") String keyword);

    /**
     * 短关键词兜底：ngram_token_size=2 时单字无法命中全文索引，退化为 LIKE 扫描
     *
     * @param keyword 已转义 LIKE 通配符（! 作转义符）的关键词
     */
    IPage<Article> searchByLike(IPage<Article> page, @Param("keyword") String keyword);

    /**
     * 热度重建：按权重公式计算窗口内每篇文章的热度分，取分数最高的 limit 篇
     * <p>SQL 见 resources/mapper/ArticleMapper.xml。评论数与收藏数用子查询实时统计</p>
     */
    List<HotScoreRow> selectHotScores(@Param("viewWeight") int viewWeight,
                                      @Param("likeWeight") int likeWeight,
                                      @Param("commentWeight") int commentWeight,
                                      @Param("favoriteWeight") int favoriteWeight,
                                      @Param("since") LocalDateTime since,
                                      @Param("limit") int limit);

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
