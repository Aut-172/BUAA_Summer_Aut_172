package com.example.demo.common;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.demo.common.feign.RemoteServiceException;
import com.example.demo.common.sentinel.SentinelBlockResponse;
import feign.FeignException;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handling for business, validation and request parsing errors.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BlockException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Void> handleSentinelBlockException(BlockException e) {
        int code = SentinelBlockResponse.code(e);
        log.warn("Sentinel 规则触发: code={}, rule={}, message={}", code, e.getRule(), e.getMessage());
        return Result.error(code, SentinelBlockResponse.message(e));
    }

    @ExceptionHandler(RemoteServiceException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleRemoteServiceException(RemoteServiceException e) {
        log.warn("远程服务异常: code={}, httpStatus={}, methodKey={}, url={}, message={}",
                e.getCode(), e.getHttpStatus(), e.getMethodKey(), e.getRequestUrl(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RetryableException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleFeignRetryableException(RetryableException e) {
        log.warn("远程服务连接或超时异常: method={}, message={}", e.method(), e.getMessage());
        return Result.error(503, "依赖服务连接超时或暂不可用，请稍后重试");
    }

    @ExceptionHandler(FeignException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleFeignException(FeignException e) {
        int code = e.status() == 429 ? 429 : e.status() >= 500 || e.status() <= 0 ? 503 : e.status();
        log.warn("远程服务调用异常: code={}, status={}, message={}", code, e.status(), e.getMessage());
        return Result.error(code, code == 429 ? "依赖服务请求过多，请稍后重试" : "依赖服务暂不可用，请稍后重试");
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验异常: {}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(400, "请求体格式错误");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.serverError("系统繁忙，请稍后重试");
    }
}
