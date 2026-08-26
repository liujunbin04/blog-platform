package com.example.blog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置
 * <p>注意：Spring Security 场景下还需在 SecurityConfig 中调用 http.cors()，
 * 预检请求（OPTIONS）才能放行</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许所有来源：本项目 token 走 Authorization 头，浏览器凭证机制
                // （Cookie 等）无用武之地，不开 allowCredentials，收紧放行面
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                // 预检请求缓存 1 小时
                .maxAge(3600);
    }
}
