package com.example.demo.common.feign;

import com.example.demo.common.contract.InternalHeaders;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Response;
import feign.Retryer;
import feign.Util;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Baseline Feign governance shared by all business services.
 */
@Slf4j
@Configuration
public class FeignGovernanceConfig {

    private static final int MAX_BODY_LOG_LENGTH = 2048;

    @Bean
    public ErrorDecoder remoteServiceErrorDecoder() {
        return (methodKey, response) -> {
            String requestUrl = response.request() == null ? "-" : response.request().url();
            String responseBody = readBody(response);
            int status = response.status();
            int code = toBusinessCode(status);
            String message = toUserMessage(status);

            log.warn("Feign call failed: methodKey={}, status={}, url={}, body={}",
                    methodKey, status, requestUrl, responseBody);

            return new RemoteServiceException(code, message, status, methodKey, requestUrl, responseBody);
        };
    }

    @Bean
    public RequestInterceptor internalHeadersRequestInterceptor(
            @Value("${spring.application.name:unknown-service}") String applicationName) {
        return template -> {
            if (!hasHeader(template.headers(), InternalHeaders.CALLER_SERVICE)) {
                template.header(InternalHeaders.CALLER_SERVICE, applicationName);
            }

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes == null ? null : attributes.getRequest();
            if (request == null) {
                addHeaderIfMissing(template.headers(), template::header,
                        InternalHeaders.REQUEST_ID, UUID.randomUUID().toString());
                return;
            }

            String requestId = request.getHeader(InternalHeaders.REQUEST_ID);
            addHeaderIfMissing(template.headers(), template::header, InternalHeaders.REQUEST_ID,
                    requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId);
            copyHeaderIfPresent(request, template.headers(), template::header, InternalHeaders.IDEMPOTENCY_KEY);
            copyHeaderIfPresent(request, template.headers(), template::header, "Authorization");
        };
    }

    @Bean
    public Logger.Level feignLoggerLevel(@Value("${app.feign.logger-level:BASIC}") String loggerLevel) {
        try {
            return Logger.Level.valueOf(loggerLevel.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            log.warn("Invalid app.feign.logger-level={}, fallback to BASIC", loggerLevel);
            return Logger.Level.BASIC;
        }
    }

    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    private static void copyHeaderIfPresent(HttpServletRequest request,
                                            Map<String, Collection<String>> existingHeaders,
                                            HeaderWriter writer,
                                            String headerName) {
        String value = request.getHeader(headerName);
        if (value != null && !value.isBlank() && !hasHeader(existingHeaders, headerName)) {
            writer.header(headerName, value);
        }
    }

    private static void addHeaderIfMissing(Map<String, Collection<String>> existingHeaders,
                                           HeaderWriter writer,
                                           String headerName,
                                           String value) {
        if (value != null && !value.isBlank() && !hasHeader(existingHeaders, headerName)) {
            writer.header(headerName, value);
        }
    }

    private static boolean hasHeader(Map<String, Collection<String>> headers, String headerName) {
        return headers.keySet().stream().anyMatch(name -> name.equalsIgnoreCase(headerName));
    }

    private static String readBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try {
            String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
            return body.length() <= MAX_BODY_LOG_LENGTH ? body : body.substring(0, MAX_BODY_LOG_LENGTH);
        } catch (IOException ex) {
            return "<unreadable response body>";
        }
    }

    private static int toBusinessCode(int httpStatus) {
        if (httpStatus == 429) {
            return 429;
        }
        if (httpStatus >= 500 || httpStatus <= 0) {
            return 503;
        }
        return httpStatus;
    }

    private static String toUserMessage(int httpStatus) {
        if (httpStatus == 429) {
            return "依赖服务请求过多，请稍后重试";
        }
        if (httpStatus == 404) {
            return "依赖服务资源不存在";
        }
        if (httpStatus >= 400 && httpStatus < 500) {
            return "依赖服务拒绝了本次请求";
        }
        return "依赖服务暂不可用，请稍后重试";
    }

    @FunctionalInterface
    private interface HeaderWriter {
        void header(String name, String value);
    }
}
