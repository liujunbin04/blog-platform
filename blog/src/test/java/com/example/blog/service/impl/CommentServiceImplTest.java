package com.example.blog.service.impl;

import com.example.blog.common.BusinessException;
import com.example.blog.dto.CommentDTO;
import com.example.blog.service.ArticleService;
import com.example.blog.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 评论入口的可见性闸门：草稿 / 已隐藏文章不可评论，且拒绝时不产生任何副作用
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private UserService userService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CommentServiceImpl commentService;

    @Test
    void addCommentRejectsNonPublicArticleWithoutSideEffects() {
        when(articleService.requirePublicArticle(9L))
                .thenThrow(new BusinessException(404, "文章不存在"));
        CommentDTO dto = new CommentDTO();
        dto.setArticleId(9L);
        dto.setContent("hi");

        BusinessException e = assertThrows(BusinessException.class,
                () -> commentService.addComment(dto));
        assertEquals(404, e.getCode());
        verifyNoInteractions(rabbitTemplate);
    }
}
