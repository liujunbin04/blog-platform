package com.example.blog.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CorsConfigTest {

    @Test
    void allowsOnlyLocalFrontendOrigins() {
        assertArrayEquals(
                new String[]{"http://localhost", "http://localhost:5173"},
                CorsConfig.ALLOWED_ORIGINS
        );
    }
}
