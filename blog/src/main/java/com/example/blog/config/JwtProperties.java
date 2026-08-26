package com.example.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性（对应 application.yml 中的 jwt.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥（64 字节十六进制 → HS512） */
    private String secret;

    /** 过期时间（毫秒） */
    private Long expiration;
}
