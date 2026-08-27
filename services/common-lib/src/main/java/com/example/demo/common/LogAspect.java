package com.example.demo.common;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
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

    @Pointcut("execution(public * com.example.demo..controller.*.*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String httpMethod = request != null ? request.getMethod() : "-";
        String requestUri = request != null ? request.getRequestURI() : "-";
        String queryString = request != null ? StrUtil.nullToEmpty(request.getQueryString()) : "";
        String args = maskSensitiveArgs(joinPoint.getArgs());

        log.info("-> [{} {}] {}.{} | args={}{}",
                httpMethod, requestUri, className, methodName, args,
                StrUtil.isNotBlank(queryString) ? " ?" + queryString : "");

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - startTime;
            String resultStr = maskSensitiveResult(result);

            log.info("<- [{} {}] {}.{} | cost={}ms | result={}",
                    httpMethod, requestUri, className, methodName, elapsed, resultStr);
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
            return StrUtil.maxLength(maskSensitiveJson(JSONUtil.toJsonStr(result)), 2000);
        } catch (Throwable e) {
            return StrUtil.maxLength(result.toString(), 2000);
        }
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
