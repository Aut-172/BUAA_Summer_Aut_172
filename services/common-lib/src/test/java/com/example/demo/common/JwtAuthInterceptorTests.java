package com.example.demo.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthInterceptorTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private JwtUtil jwtUtil;
    private JwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 60_000);
        interceptor = new JwtAuthInterceptor(jwtUtil);
    }

    @Test
    void optionsRequestBypassesAuthentication() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/user/profile");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getAttribute("userId")).isNull();
    }

    @Test
    void missingOrInvalidBearerTokenIsRejected() {
        MockHttpServletRequest missing = new MockHttpServletRequest("GET", "/api/user/profile");
        MockHttpServletRequest invalid = new MockHttpServletRequest("GET", "/api/user/profile");
        invalid.addHeader("Authorization", "Bearer not-a-token");

        assertThatThrownBy(() -> interceptor.preHandle(missing, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
        assertThatThrownBy(() -> interceptor.preHandle(invalid, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }

    @Test
    void validConsumerTokenSetsRequestIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer " + jwtUtil.generateToken(100L, "consumer", "student01"));

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getAttribute("userId")).isEqualTo(100L);
        assertThat(request.getAttribute("role")).isEqualTo("consumer");
    }

    @Test
    void roleProtectedPathsRejectWrongRolesAndSetRoleSpecificIds() {
        MockHttpServletRequest adminRequest = new MockHttpServletRequest("GET", "/api/admin/users");
        adminRequest.addHeader("Authorization", "Bearer " + jwtUtil.generateToken(1L, "consumer", "student01"));

        assertThatThrownBy(() -> interceptor.preHandle(adminRequest, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);

        MockHttpServletRequest merchantRequest = new MockHttpServletRequest("GET", "/api/merchant/orders");
        merchantRequest.addHeader("Authorization", "Bearer " + jwtUtil.generateToken(200L, "merchant", "merchant01"));

        assertThat(interceptor.preHandle(merchantRequest, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(merchantRequest.getAttribute("merchantId")).isEqualTo(200L);
    }
}
