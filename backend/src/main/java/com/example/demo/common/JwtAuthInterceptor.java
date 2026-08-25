package com.example.demo.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT authentication interceptor with basic role-based path protection.
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public JwtAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw BusinessException.unauthorized("请先登录");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw BusinessException.unauthorized("登录已过期，请重新登录");
        }

        Long userId = jwtUtil.getUserId(token);
        String role = jwtUtil.getRole(token);
        enforceRoleAccess(request.getRequestURI(), role);

        request.setAttribute("userId", userId);
        request.setAttribute("role", role);

        if ("merchant".equals(role)) {
            request.setAttribute("merchantId", userId);
        } else if ("rider".equals(role)) {
            request.setAttribute("riderId", userId);
        } else if ("admin".equals(role)) {
            request.setAttribute("adminId", userId);
        }

        return true;
    }

    private void enforceRoleAccess(String requestUri, String role) {
        if (requestUri.startsWith("/api/admin/") && !"admin".equals(role)) {
            throw BusinessException.forbidden("无权访问管理员接口");
        }
        if (requestUri.startsWith("/api/merchant/") && !"merchant".equals(role)) {
            throw BusinessException.forbidden("无权访问商家接口");
        }
        if (requestUri.startsWith("/api/rider/") && !"rider".equals(role)) {
            throw BusinessException.forbidden("无权访问骑手接口");
        }
        if (requestUri.startsWith("/api/user/")
                || requestUri.equals("/api/checkout")
                || requestUri.equals("/api/orders")
                || requestUri.startsWith("/api/orders/")
                || requestUri.equals("/api/coupons")
                || requestUri.startsWith("/api/coupons/")
                || requestUri.startsWith("/api/delivery/")
                || requestUri.startsWith("/api/payments/")) {
            if (!"consumer".equals(role)) {
                throw BusinessException.forbidden("无权访问用户接口");
            }
        }
    }
}
