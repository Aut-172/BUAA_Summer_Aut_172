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
                    .replaceAll("/api/admin/orders/\\d+", "/api/admin/orders/{id}")
                    .replaceAll("/api/admin/users/\\d+", "/api/admin/users/{id}")
                    .replaceAll("/api/admin/merchants/\\d+", "/api/admin/merchants/{id}")
                    .replaceAll("/api/admin/riders/\\d+", "/api/admin/riders/{id}");
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
