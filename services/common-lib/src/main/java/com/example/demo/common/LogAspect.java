package com.example.demo.common;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified request/response logging for controller endpoints.
 */
@Slf4j
@Aspect
@Component
public class LogAspect {

    private static final List<String> SENSITIVE_KEYS = List.of("password", "secret", "token", "authorization");

    @Value("${app.logging.controller-enabled:true}")
    private boolean controllerLoggingEnabled;

    @Value("${app.logging.controller-payload-enabled:false}")
    private boolean controllerPayloadEnabled;

    @Value("${app.logging.controller-result-max-length:2000}")
    private int controllerResultMaxLength;

    @Pointcut("execution(public * com.example.demo..controller.*.*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!controllerLoggingEnabled) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String httpMethod = request != null ? request.getMethod() : "-";
        String requestUri = request != null ? request.getRequestURI() : "-";
        String queryString = request != null ? StrUtil.nullToEmpty(request.getQueryString()) : "";
        String querySuffix = StrUtil.isNotBlank(queryString) ? " ?" + queryString : "";

        if (controllerPayloadEnabled) {
            log.info("-> [{} {}] {}.{} | args={}{}",
                    httpMethod, requestUri, className, methodName, maskSensitiveArgs(joinPoint.getArgs()), querySuffix);
        } else {
            log.info("-> [{} {}] {}.{}{}", httpMethod, requestUri, className, methodName, querySuffix);
        }

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - startTime;

            if (controllerPayloadEnabled) {
                log.info("<- [{} {}] {}.{} | cost={}ms | result={}",
                        httpMethod, requestUri, className, methodName, elapsed, maskSensitiveResult(result));
            } else {
                log.info("<- [{} {}] {}.{} | cost={}ms",
                        httpMethod, requestUri, className, methodName, elapsed);
            }
            return result;
        } catch (Throwable throwable) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("<- [{} {}] {}.{} | cost={}ms | error={}",
                    httpMethod, requestUri, className, methodName, elapsed, throwable.getMessage());
            throw throwable;
        }
    }

    private String maskSensitiveArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return Arrays.stream(args)
                .map(arg -> {
                    if (arg == null) {
                        return "null";
                    }

                    try {
                        return maskSensitiveJson(JSONUtil.toJsonStr(arg));
                    } catch (Throwable e) {
                        return maskSensitiveJson(arg.toString());
                    }
                })
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String maskSensitiveResult(Object result) {
        if (result == null) {
            return "null";
        }

        try {
            return StrUtil.maxLength(maskSensitiveJson(JSONUtil.toJsonStr(result)), maxResultLength());
        } catch (Throwable e) {
            return StrUtil.maxLength(result.toString(), maxResultLength());
        }
    }

    private int maxResultLength() {
        return controllerResultMaxLength <= 0 ? 2000 : controllerResultMaxLength;
    }

    private String maskSensitiveJson(String json) {
        if (StrUtil.isBlank(json)) {
            return json;
        }

        String masked = json;
        for (String key : SENSITIVE_KEYS) {
            masked = masked.replaceAll(
                    "(?i)\"" + key + "\"\\s*:\\s*\"([^\"]+)\"",
                    "\"" + key + "\":\"****\""
            );
        }
        return masked;
    }
}
