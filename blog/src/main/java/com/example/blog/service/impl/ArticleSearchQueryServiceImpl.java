package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleSearchDTO;
import com.example.blog.entity.Article;
import com.example.blog.entity.User;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.service.ArticleSearchQueryService;
import com.example.blog.service.AuthorAssembler;
import com.example.blog.vo.ArticleSearchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 文章全文搜索：MySQL FULLTEXT（ngram）+ 短关键词 LIKE 兜底
 */
@Service
@RequiredArgsConstructor
public class ArticleSearchQueryServiceImpl implements ArticleSearchQueryService {

    /** LIKE 通配符转义符，必须与 ArticleMapper.xml 里的 ESCAPE '!' 保持一致 */
    private static final String LIKE_ESCAPE = "!";

    /** ngram_token_size 默认为 2，短于它无法命中全文索引 */
    private static final int MIN_FULLTEXT_LENGTH = 2;

    private final ArticleMapper articleMapper;

    private final AuthorAssembler authorAssembler;

    @Override
    public PageResult<ArticleSearchVO> search(ArticleSearchDTO dto) {
        String keyword = dto.getKeyword().trim();
        Page<Article> page = new Page<>(dto.getPage(), dto.getSize());
        String escaped = escapeLike(keyword);
        IPage<Article> result = keyword.length() < MIN_FULLTEXT_LENGTH
                ? articleMapper.searchByLike(page, escaped)
                : articleMapper.searchByFullText(page, escaped);

        List<ArticleSearchVO> list = result.getRecords().stream()
                .map(article -> ArticleSearchVO.from(article, keyword))
                .toList();
        Map<Long, User> authorMap = authorAssembler.loadUsers(
                list.stream().map(ArticleSearchVO::getAuthorId).toList()
        );
        ArticleSearchVO.fillAuthors(list, authorMap);
        return PageResult.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 转义 LIKE 通配符，避免关键词里的 % 和 _ 被当成模式匹配
     * <p>不转义的话搜一个 % 会命中全表，搜 _ 会命中任意单字位置。
     * 转义符本身也要先转义，顺序不能反。</p>
     */
    private String escapeLike(String keyword) {
        return keyword
                .replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}