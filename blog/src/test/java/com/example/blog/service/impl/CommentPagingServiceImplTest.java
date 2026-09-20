package com.example.blog.service.impl;

import com.example.blog.dto.CommentPageDTO;
import com.example.blog.entity.Article;
import com.example.blog.entity.Comment;
import com.example.blog.mapper.CommentMapper;
import com.example.blog.service.ArticleService;
import com.example.blog.service.ArticleVisibilityPolicy;
import com.example.blog.service.AuthorAssembler;
import com.example.blog.service.UserService;
import com.example.blog.vo.CommentPageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentPagingServiceImplTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private UserService userService;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private ArticleVisibilityPolicy visibilityPolicy;

    @InjectMocks
    private CommentServiceImpl service;

    @Test
    void pageCommentsReturnsPagedResult() {
        Article article = new Article();
        article.setId(7L);
        article.setStatus(1);
        article.setHidden(0);
        when(articleService.getById(7L)).thenReturn(article);
        ReflectionTestUtils.setField(service, "baseMapper", commentMapper);
        ReflectionTestUtils.setField(service, "visibilityPolicy", new ArticleVisibilityPolicy());
        ReflectionTestUtils.setField(service, "authorAssembler", new AuthorAssembler(userService));

        CommentPageDTO dto = new CommentPageDTO();
        dto.setArticleId(7L);
        dto.setPageNum(1);
        dto.setPageSize(20);

        CommentServiceImpl spyService = spy(service);
        Page<Comment> emptyPage = new Page<>(1, 20);
        emptyPage.setTotal(0);
        doReturn(emptyPage).when(spyService).findRootCommentPage(dto);

        CommentPageVO result = spyService.pageComments(dto);

        assertEquals(0L, result.getTotal());
    }
}
