package com.example.blog.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleSearchDTO;
import com.example.blog.entity.Article;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.service.AuthorAssembler;
import com.example.blog.vo.ArticleSearchVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSearchQueryServiceImplTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private AuthorAssembler authorAssembler;

    @InjectMocks
    private ArticleSearchQueryServiceImpl service;

    private static Page<Article> emptyPage() {
        return new Page<>(1, 10);
    }

    @Test
    void searchReturnsEmptyPageWhenNoHits() {
        when(articleMapper.searchByFullText(any(), anyString())).thenReturn(emptyPage());

        PageResult<ArticleSearchVO> result = service.search(dto("spring"));

        assertEquals(0L, result.getTotal());
        assertEquals(List.of(), result.getList());
    }

    @Test
    void multiCharKeywordUsesFullText() {
        when(articleMapper.searchByFullText(any(), anyString())).thenReturn(emptyPage());

        service.search(dto("数据库"));

        verify(articleMapper).searchByFullText(any(), anyString());
        verify(articleMapper, never()).searchByLike(any(), anyString());
    }

    @Test
    void singleCharKeywordFallsBackToLike() {
        // ngram_token_size=2，单字命不中全文索引，必须走 LIKE 兜底
        when(articleMapper.searchByLike(any(), anyString())).thenReturn(emptyPage());

        service.search(dto("猫"));

        verify(articleMapper).searchByLike(any(), anyString());
        verify(articleMapper, never()).searchByFullText(any(), anyString());
    }

    @Test
    void likeWildcardsAreEscaped() {
        when(articleMapper.searchByLike(any(), anyString())).thenReturn(emptyPage());

        service.search(dto("%"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(articleMapper).searchByLike(any(), captor.capture());
        // 不转义的话一个 % 会命中全表
        assertEquals("!%", captor.getValue());
    }

    @Test
    void escapeCharItselfIsEscapedFirst() {
        when(articleMapper.searchByFullText(any(), anyString())).thenReturn(emptyPage());

        service.search(dto("!%"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(articleMapper).searchByFullText(any(), captor.capture());
        // 转义符必须先于 % 和 _ 转义，否则会把「刚插入的转义符」再转义一遍：
        // 顺序正确时 "!%" -> "!!!%"，顺序颠倒会得到 "!!!!%"
        assertEquals("!!!%", captor.getValue());
    }

    @Test
    void hitIsHighlightedWithEmTag() {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("Spring 入门");
        article.setContent("这是一篇关于 Spring 的文章，内容足够长所以会被截断。");
        article.setAuthorId(9L);

        Page<Article> page = new Page<>(1, 10);
        page.setRecords(List.of(article));
        when(articleMapper.searchByFullText(any(), anyString())).thenReturn(page);
        when(authorAssembler.loadUsers(any())).thenReturn(java.util.Map.of());

        PageResult<ArticleSearchVO> result = service.search(dto("Spring"));

        String title = result.getList().get(0).getTitle();
        assertEquals("<em>Spring</em> 入门", title);
        assertTrue(result.getList().get(0).getContentSnippet().contains("<em>Spring</em>"));
    }

    private ArticleSearchDTO dto(String keyword) {
        ArticleSearchDTO dto = new ArticleSearchDTO();
        dto.setKeyword(keyword);
        dto.setPage(1);
        dto.setSize(10);
        return dto;
    }
}