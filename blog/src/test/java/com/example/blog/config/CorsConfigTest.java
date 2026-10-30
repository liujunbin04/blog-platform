package com.example.blog.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CORS 白名单的回归测试
 * <p>曾经只放行 {@code http://localhost}（80 端口），而前端实际跑在 18081，
 * 于是浏览器带上的 Origin 不在白名单里，注册等写请求直接被判 403
 * 「Invalid CORS request」，前端只显示「网络异常」。</p>
 */
class CorsConfigTest {

    @Test
    void allowsFrontendOnConfiguredPort() {
        // 前端由 FRONTEND_PORT 决定（默认 18081），不是 80
        assertTrue(CorsConfig.DEFAULT_ALLOWED_ORIGINS.contains("http://localhost:18081"));
        assertTrue(CorsConfig.DEFAULT_ALLOWED_ORIGINS.contains("http://127.0.0.1:18081"));
    }

    @Test
    void allowsViteDevServer() {
        assertTrue(CorsConfig.DEFAULT_ALLOWED_ORIGINS.contains("http://localhost:5173"));
    }

    @Test
    void doesNotAllowArbitraryOrigins() {
        assertFalse(CorsConfig.DEFAULT_ALLOWED_ORIGINS.contains("http://evil.example.com"));
        // 局域网 IP 不硬编码在内置白名单里，需通过 app.cors.allowed-origins 追加
        assertFalse(CorsConfig.DEFAULT_ALLOWED_ORIGINS.contains("http://192.168.1.9:18081"));
    }
}