package com.example.blog.utils;

import com.example.blog.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String VALID_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void constructorRejectsMissingSecret() {
        JwtProperties properties = properties(null);

        assertThrows(IllegalStateException.class, () -> new JwtUtil(properties));
    }

    @Test
    void constructorRejectsSecretShorterThan64Bytes() {
        JwtProperties properties = properties("too-short");

        assertThrows(IllegalStateException.class, () -> new JwtUtil(properties));
    }

    @Test
    void generatesAndParsesTokenWithValidSecret() {
        JwtUtil jwtUtil = new JwtUtil(properties(VALID_SECRET));

        String token = jwtUtil.generateToken(42L, "tester");
        Claims claims = jwtUtil.parseToken(token);

        assertEquals("42", claims.getSubject());
        assertEquals("tester", claims.get("username", String.class));
    }

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpiration(60_000L);
        return properties;
    }
}
