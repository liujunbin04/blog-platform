package com.example.blog.repository;

import com.example.blog.entity.ArticleDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 文章搜索仓库（Spring Data Elasticsearch）
 * <p>自动获得 save / findById / deleteById / findAll 等 CRUD；
 * 全文检索带高亮见自定义扩展 {@link ArticleSearchRepositoryCustom}</p>
 */
public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleDocument, Long>,
        ArticleSearchRepositoryCustom {
}
