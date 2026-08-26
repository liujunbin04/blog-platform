package com.example.blog.repository;

import com.example.blog.entity.ArticleDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;

import java.util.List;

/**
 * ArticleSearchRepositoryCustom 的实现（类名 = 接口名 + Impl，Spring Data 自动挂载，
 * 不要加 @Component 避免重复注册）
 */
@RequiredArgsConstructor
public class ArticleSearchRepositoryCustomImpl implements ArticleSearchRepositoryCustom {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public SearchHits<ArticleDocument> searchByKeyword(String keyword, int page, int size) {
        // 高亮全局参数：<em> 标签包裹命中词
        HighlightParameters parameters = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .build();
        // 标题：numberOfFragments=0 返回整段标题；正文：取 1 个 100 字片段
        HighlightFieldParameters titleParams = HighlightFieldParameters.builder()
                .withNumberOfFragments(0)
                .build();
        HighlightFieldParameters contentParams = HighlightFieldParameters.builder()
                .withFragmentSize(100)
                .withNumberOfFragments(1)
                .build();
        Highlight highlight = new Highlight(parameters, List.of(
                new HighlightField("title", titleParams),
                new HighlightField("content", contentParams)
        ));

        // multi_match：标题、正文任一命中（OR），按相关度打分排序
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(mm -> mm
                        .query(keyword)
                        .fields("title", "content")))
                .withHighlightQuery(new HighlightQuery(highlight, ArticleDocument.class))
                .withPageable(PageRequest.of(page - 1, size))
                .build();

        return elasticsearchOperations.search(query, ArticleDocument.class);
    }
}
