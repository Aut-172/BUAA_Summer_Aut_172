package com.example.demo.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;

/**
 * Shared Sentinel block response mapping.
 */
public final class SentinelBlockResponse {

    private SentinelBlockResponse() {
    }

    public static int code(BlockException exception) {
        if (exception instanceof DegradeException) {
            return 503;
        }
        return 429;
    }

    public static int httpStatus(BlockException exception) {
        return code(exception);
    }

    public static String message(BlockException exception) {
        if (exception instanceof DegradeException) {
            return "依赖服务暂不可用，请稍后重试";
        }
        if (exception instanceof FlowException) {
            return "请求过于频繁，请稍后重试";
        }
        return "系统繁忙，请稍后重试";
    }
}
