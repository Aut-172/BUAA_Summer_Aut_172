package com.example.demo.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Unified response body for Gateway Sentinel blocks.
 */
@Configuration
public class SentinelGatewayBlockConfig {

    private static final String BLOCK_RESPONSE = "{\"code\":429,\"message\":\"系统繁忙，请稍后重试\",\"data\":null}";

    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler(this::writeBlockedResponse);
    }

    private Mono<ServerResponse> writeBlockedResponse(org.springframework.web.server.ServerWebExchange exchange,
                                                      Throwable throwable) {
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(BLOCK_RESPONSE);
    }
}
