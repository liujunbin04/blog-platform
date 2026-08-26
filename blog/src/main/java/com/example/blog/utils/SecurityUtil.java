package com.example.blog.utils;

import com.example.blog.common.BusinessException;
import com.example.blog.common.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具：从 SecurityContext 获取当前登录用户
 * <p>前提：JwtAuthenticationFilter 已把 LoginUser 放入认证上下文，
 * 因此 controller / service 中可直接调用，无需传参</p>
 */
public class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户，未登录抛 401
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return loginUser;
    }

    /**
     * 获取当前登录用户 id
     */
    public static Long getCurrentUserId() {
        return getLoginUser().getUserId();
    }
}
