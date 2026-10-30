package com.example.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端配置白名单
 * <p>项目没有角色体系（user 表无 role 列，JWT 只带 ROLE_USER），
 * DLQ 重放是运维动作，用一个只读白名单挡住即可，
 * 不在这次改动里顺带引入一套只用到一处的权限模型。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    /** 允许访问管理端点的用户名（按登录名匹配） */
    private List<String> usernames = new ArrayList<>();
}