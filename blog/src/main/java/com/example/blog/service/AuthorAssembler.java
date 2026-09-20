package com.example.blog.service;

import com.example.blog.entity.User;
import com.example.blog.vo.AuthorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作者信息批量查询和 VO 转换，统一处理空集合与缺失用户。
 */
@Component
@RequiredArgsConstructor
public class AuthorAssembler {

    private final UserService userService;

    public Map<Long, User> loadUsers(Collection<Long> userIds) {
        Set<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    public AuthorVO toAuthor(User user) {
        return user == null ? null : AuthorVO.from(user);
    }

    public Map<Long, AuthorVO> loadAuthors(Collection<Long> userIds) {
        return loadUsers(userIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toAuthor(entry.getValue())));
    }
}
