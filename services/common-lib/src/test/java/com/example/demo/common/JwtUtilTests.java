package com.example.demo.common;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void generatedTokenExposesIdentityRoleAndUsername() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

        String token = jwtUtil.generateToken(42L, "consumer", "student01");
        Claims claims = jwtUtil.parseToken(token);

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUserId(token)).isEqualTo(42L);
        assertThat(jwtUtil.getRole(token)).isEqualTo("consumer");
        assertThat(claims.get("username", String.class)).isEqualTo("student01");
        assertThat(claims.getSubject()).isEqualTo("42");
    }

    @Test
    void validateTokenRejectsTamperedAndExpiredTokens() throws Exception {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);
        String token = jwtUtil.generateToken(7L, "admin", "admin");

        assertThat(jwtUtil.validateToken(token + "x")).isFalse();

        JwtUtil expiringJwtUtil = new JwtUtil(SECRET, 1);
        String expiringToken = expiringJwtUtil.generateToken(8L, "rider", "rider01");
        Thread.sleep(5);

        assertThat(expiringJwtUtil.validateToken(expiringToken)).isFalse();
    }
}
