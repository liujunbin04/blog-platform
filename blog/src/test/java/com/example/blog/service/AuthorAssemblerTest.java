package com.example.blog.service;

import com.example.blog.entity.User;
import com.example.blog.vo.AuthorVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthorAssemblerTest {

    private final UserService userService = mock(UserService.class);
    private final AuthorAssembler assembler = new AuthorAssembler(userService);

    @Test
    void emptyIdsReturnEmptyMapWithoutQuery() {
        assertTrue(assembler.loadUsers(List.of()).isEmpty());
        verifyNoInteractions(userService);
    }

    @Test
    void loadsAuthorsByUserId() {
        User user = new User();
        user.setId(3L);
        user.setNickname("tester");
        when(userService.listByIds(org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of(user));

        Map<Long, AuthorVO> authors = assembler.loadAuthors(List.of(3L));

        assertEquals("tester", authors.get(3L).getNickname());
    }
}
