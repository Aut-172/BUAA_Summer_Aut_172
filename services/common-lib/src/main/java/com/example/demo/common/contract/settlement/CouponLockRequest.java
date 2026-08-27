package com.example.demo.common.contract.settlement;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CouponLockRequest {

    private String requestId;

    @NotNull(message = "userId不能为空")
    private Long userId;

    @NotNull(message = "couponId不能为空")
    private Long couponId;

    @NotNull(message = "orderId不能为空")
    private Long orderId;

    @NotNull(message = "orderAmount不能为空")
    private BigDecimal orderAmount;
}
