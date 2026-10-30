package com.example.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置
 * <p>注意：Spring Security 场景下还需在 SecurityConfig 中调用 http.cors()，
 * 预检请求（OPTIONS）才能放行</p>
 * <p><b>为什么必须列全所有来源</b>：同源部署下浏览器本来不发 Origin，但一旦页面
 * 换了端口或从局域网地址访问，浏览器就会带上 Origin，此时 Origin 不在白名单里
 * 会被直接判 403「Invalid CORS request」——而且被拒的不只是预检请求，
 * 带 Origin 的普通请求同样会被拦。前端提示只会显示成「网络异常」，很难定位。</p>
 * <p>前端端口由 FRONTEND_PORT 决定（默认 18081，不是 80），局域网访问时来源是
 * {@code http://<局域网IP>:<端口>}，因此额外支持用 app.cors.allowed-origins 追加。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 默认放行的来源：本地开发（Vite）与容器化部署（前端端口由 FRONTEND_PORT 决定）
     */
    static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:18081",
            "http://127.0.0.1:18081"
    );

    /**
     * 额外放行的来源（局域网地址等），逗号分隔
     */
    @Value("${app.cors.allowed-origins:}")
    private String extraOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>(DEFAULT_ALLOWED_ORIGINS);
        for (String origin : extraOrigins.split(",")) {
            if (!origin.isBlank()) {
                origins.add(origin.trim());
            }
        }
        registry.addMapping("/**")
                // 本项目 token 走 Authorization 头，浏览器凭证机制（Cookie 等）
                // 无用武之地，不开 allowCredentials，收紧放行面
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 预检请求缓存 1 小时
                .maxAge(3600);
    }
}
