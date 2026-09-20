package com.example.blog.service.impl;

import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleSearchDTO;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleSearchVisibilityService;
import com.example.blog.service.AuthorAssembler;
import com.example.blog.vo.ArticleSearchVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSearchQueryServiceImplTest {

    @Mock
    private ArticleSearchRepository repository;

    @Mock
    private ArticleSearchVisibilityService visibilityService;

    @Mock
    private AuthorAssembler authorAssembler;

    @InjectMocks
    private ArticleSearchQueryServiceImpl service;

    @Test
    void searchReturnsEmptyPageWhenNoHits() {
        SearchHits<com.example.blog.entity.ArticleDocument> hits =
                org.mockito.Mockito.mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(hits.getTotalHits()).thenReturn(0L);
        when(repository.searchByKeyword("spring", 1, 10)).thenReturn(hits);
        when(visibilityService.filterVisible(List.of())).thenReturn(List.of());

        ArticleSearchDTO dto = new ArticleSearchDTO();
        dto.setKeyword("spring");
        dto.setPage(1);
        dto.setSize(10);

        PageResult<ArticleSearchVO> result = service.search(dto);

        assertEquals(0L, result.getTotal());
        assertEquals(List.of(), result.getList());
    }
}
