package com.example.demo.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionKeepsStableCodeAndMessage() {
        Result<Void> result = handler.handleBusinessException(BusinessException.forbidden("无权访问管理员接口"));

        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getMessage()).isEqualTo("无权访问管理员接口");
        assertThat(result.getData()).isNull();
    }

    @Test
    void illegalArgumentReturnsBadRequestResult() {
        Result<Void> result = handler.handleIllegalArgumentException(new IllegalArgumentException("参数错误"));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("参数错误");
    }

    @Test
    void unknownExceptionReturnsServerErrorResult() {
        Result<Void> result = handler.handleException(new RuntimeException("database down"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("系统繁忙，请稍后重试");
    }
}
