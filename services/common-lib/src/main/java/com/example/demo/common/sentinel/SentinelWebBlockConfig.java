package com.example.demo.common.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.adapter.web.common.UrlCleaner;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Sentinel WebMVC block handling shared by servlet business services.
 */
@Configuration
public class SentinelWebBlockConfig {

    @Bean
    public UrlCleaner sentinelUrlCleaner() {
        return originUrl -> {
            if (originUrl == null || originUrl.isBlank()) {
                return originUrl;
            }
            return originUrl
                    .replaceAll("/api/orders/\\d+", "/api/orders/{id}")
                    .replaceAll("/api/products/\\d+", "/api/products/{id}")
                    .replaceAll("/api/merchants/\\d+", "/api/merchants/{id}")
                    .replaceAll("/api/coupons/\\d+", "/api/coupons/{id}")
                    .replaceAll("/api/payments/\\d+", "/api/payments/{id}")
                    .replaceAll("/api/delivery/\\d+", "/api/delivery/{id}")
                    .replaceAll("/api/user/addresses/\\d+", "/api/user/addresses/{id}")
                    .replaceAll("/api/user/cart/\\d+", "/api/user/cart/{id}")
                    .replaceAll("/api/user/favorites/\\d+", "/api/user/favorites/{merchantId}")
                    .replaceAll("/api/merchant/products/\\d+", "/api/merchant/products/{productId}")
                    .replaceAll("/api/merchant/orders/\\d+", "/api/merchant/orders/{id}")
                    .replaceAll("/api/rider/tasks/\\d+", "/api/rider/tasks/{id}")
                    .replaceAll("/api/messages/orders/\\d+", "/api/messages/orders/{orderId}")
                    .replaceAll("/api/admin/orders/\\d+", "/api/admin/orders/{id}")
                    .replaceAll("/api/admin/users/\\d+", "/api/admin/users/{id}")
                    .replaceAll("/api/admin/merchants/\\d+", "/api/admin/merchants/{id}")
                    .replaceAll("/api/admin/riders/\\d+", "/api/admin/riders/{id}")
                    .replaceAll("/internal/users/\\d+/addresses/\\d+", "/internal/users/{userId}/addresses/{addressId}")
                    .replaceAll("/internal/users/\\d+/cart", "/internal/users/{userId}/cart")
                    .replaceAll("/internal/users/\\d+", "/internal/users/{userId}")
                    .replaceAll("/internal/merchants/\\d+", "/internal/merchants/{merchantId}")
                    .replaceAll("/internal/products/changes/[^/]+", "/internal/products/changes/{requestId}")
                    .replaceAll("/internal/products/\\d+", "/internal/products/{productId}")
                    .replaceAll("/internal/orders/\\d+/mark-paid", "/internal/orders/{orderId}/mark-paid")
                    .replaceAll("/internal/orders/\\d+/participants", "/internal/orders/{orderId}/participants")
                    .replaceAll("/internal/orders/\\d+/reviewed-items", "/internal/orders/{orderId}/reviewed-items")
                    .replaceAll("/internal/orders/\\d+", "/internal/orders/{orderId}")
                    .replaceAll("/internal/fulfillment/orders/\\d+/assign-rider", "/internal/fulfillment/orders/{orderId}/assign-rider")
                    .replaceAll("/internal/fulfillment/orders/\\d+/delivered", "/internal/fulfillment/orders/{orderId}/delivered")
                    .replaceAll("/internal/fulfillment/orders/\\d+", "/internal/fulfillment/orders/{orderId}")
                    .replaceAll("/internal/coupon-locks/\\d+/release", "/internal/coupon-locks/{orderId}/release")
                    .replaceAll("/internal/coupon-locks/\\d+/confirm", "/internal/coupon-locks/{orderId}/confirm")
                    .replaceAll("/internal/riders/\\d+", "/internal/riders/{riderId}");
        };
    }

    @Bean
    public BlockExceptionHandler sentinelBlockExceptionHandler() {
        return this::writeBlockedResponse;
    }

    private void writeBlockedResponse(HttpServletRequest request,
                                      HttpServletResponse response,
                                      String resourceName,
                                      BlockException exception) throws IOException {
        response.setStatus(SentinelBlockResponse.httpStatus(exception));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + SentinelBlockResponse.code(exception)
                + ",\"message\":\"" + SentinelBlockResponse.message(exception)
                + "\",\"data\":null}");
    }
}
