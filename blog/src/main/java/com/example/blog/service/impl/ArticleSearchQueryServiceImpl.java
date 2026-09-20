package com.example.blog.service.impl;

import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleSearchDTO;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.entity.User;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleSearchQueryService;
import com.example.blog.service.ArticleSearchVisibilityService;
import com.example.blog.service.AuthorAssembler;
import com.example.blog.vo.ArticleSearchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArticleSearchQueryServiceImpl implements ArticleSearchQueryService {

    private final ArticleSearchRepository repository;
    private final ArticleSearchVisibilityService visibilityService;
    private final AuthorAssembler authorAssembler;

    @Override
    public PageResult<ArticleSearchVO> search(ArticleSearchDTO dto) {
        SearchHits<ArticleDocument> hits = repository.searchByKeyword(
                dto.getKeyword(), dto.getPage(), dto.getSize()
        );
        List<ArticleSearchVO> list = hits.getSearchHits().stream()
                .map(ArticleSearchVO::from)
                .toList();
        list = visibilityService.filterVisible(list);
        Map<Long, User> authorMap = authorAssembler.loadUsers(
                list.stream().map(ArticleSearchVO::getAuthorId).toList()
        );
        ArticleSearchVO.fillAuthors(list, authorMap);
        return PageResult.of(
                list,
                hits.getTotalHits(),
                dto.getPage().longValue(),
                dto.getSize().longValue()
        );
    }
}
