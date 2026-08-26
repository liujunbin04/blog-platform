package com.example.blog.common;

import com.example.blog.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户信息（存入 SecurityContext 的 principal）
 * <p>与 Spring 自带的 User 的区别：额外携带 userId，
 * 业务代码通过 {@link com.example.blog.utils.SecurityUtil} 直接取当前用户，
 * 无需再按用户名反查数据库</p>
 */
@Getter
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final boolean enabled;

    private LoginUser(Long userId, String username, String password) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = true;
    }

    public static LoginUser of(User user) {
        return new LoginUser(user.getId(), user.getUsername(), user.getPassword());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 当前 user 表无角色字段，统一 ROLE_USER，后续可扩展为查询角色/权限
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
