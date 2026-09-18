package com.example.blog.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * 文章搜索文档（对应 Elasticsearch 索引 article）
 * <p>与 MySQL 的 Article 实体是两份数据：文章发布/更新时同步写入 ES，
 * 搜索走 ES，详情页走 MySQL。文档 id 与 article 表主键一致，便于同步与删除</p>
 */
@Data
@Document(indexName = "article")
public class ArticleDocument {

    /** 文档 id（= article 表主键） */
    @Id
    private Long id;

    /** 标题：Text 分词，全文检索。ik_max_word 最细粒度切分建索引，
     *  ik_smart 搜索时粗粒度分词（需在 ES 中安装 analysis-ik 插件） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    /** 正文：同标题，ik 中文分词 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    /** 作者 id */
    @Field(type = FieldType.Long)
    private Long authorId;

    /** 分类：Keyword 精确匹配（按分类过滤用） */
    @Field(type = FieldType.Keyword)
    private String category;

    /** 标签：Keyword 精确匹配 */
    @Field(type = FieldType.Keyword)
    private String tags;

    /** 标签分词字段：支持按标签名称全文搜索。 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private List<String> tagNames;

    /** 作者昵称：冗余存储，用于全文搜索。 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String authorNickname;

    /** 浏览量 */
    @Field(type = FieldType.Integer)
    private Integer viewCount;

    /** 点赞数 */
    @Field(type = FieldType.Integer)
    private Integer likeCount;

    /** 创建时间。注意：不能用 LocalDateTime——SD ES 5.5 对它的默认转换器
     *  写入时会截断成 yyyy-MM-dd（已知问题），Instant 的默认转换读写对称 */
    @Field(type = FieldType.Date)
    private Instant createTime;

    /** 更新时间（同上，用 Instant） */
    @Field(type = FieldType.Date)
    private Instant updateTime;

    /**
     * 由 MySQL 的 Article 实体转换（发布/更新文章时调用，同步到 ES）。
     * LocalDateTime -> Instant 需要指定时区（本项目统一系统默认时区，即 Asia/Shanghai）
     */
    public static ArticleDocument from(Article article) {
        return from(article, null);
    }

    public static ArticleDocument from(Article article, String authorNickname) {
        ArticleDocument doc = new ArticleDocument();
        doc.setId(article.getId());
        doc.setTitle(article.getTitle());
        doc.setContent(article.getContent());
        doc.setAuthorId(article.getAuthorId());
        doc.setCategory(article.getCategory());
        doc.setTags(article.getTags());
        if (article.getTags() == null || article.getTags().isBlank()) {
            doc.setTagNames(List.of());
        } else {
        doc.setTagNames(Arrays.stream(article.getTags().split(","))
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .toList());
        }
        doc.setAuthorNickname(authorNickname);
        doc.setViewCount(article.getViewCount());
        doc.setLikeCount(article.getLikeCount());
        doc.setCreateTime(article.getCreateTime().atZone(ZoneId.systemDefault()).toInstant());
        doc.setUpdateTime(article.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant());
        return doc;
    }
}
