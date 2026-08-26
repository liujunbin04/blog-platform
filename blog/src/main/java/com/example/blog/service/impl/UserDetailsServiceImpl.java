package com.example.blog.service.impl;

import com.example.blog.common.LoginUser;
import com.example.blog.entity.User;
import com.example.blog.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 基于数据库的 UserDetailsService 实现
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // MP 自动拼接 deleted=0，逻辑删除的用户无法通过认证
        User user = userService.lambdaQuery().eq(User::getUsername, username).one();
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        // 返回自定义 LoginUser：携带 userId，业务代码通过 SecurityUtil 直接取当前用户
        return LoginUser.of(user);
    }
}
