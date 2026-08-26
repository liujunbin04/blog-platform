package com.example.blog.repository;

import com.example.blog.entity.ArticleDocument;
import org.springframework.data.elasticsearch.core.SearchHits;

/**
 * 文章搜索仓库的自定义扩展（派生方法名查询不支持高亮，需自定义实现，
 * 实现类见 ArticleSearchRepositoryCustomImpl，命名约定自动挂载）
 */
public interface ArticleSearchRepositoryCustom {

    /**
     * 全文检索：标题、正文 multi_match，返回带高亮信息的命中结果
     *
     * @param keyword 关键词
     * @param page    页码（从 1 开始）
     * @param size    每页大小
     */
    SearchHits<ArticleDocument> searchByKeyword(String keyword, int page, int size);
}
