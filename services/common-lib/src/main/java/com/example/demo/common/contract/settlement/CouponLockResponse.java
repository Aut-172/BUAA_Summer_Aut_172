package com.example.demo.common.contract.settlement;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CouponLockResponse {

    private String requestId;
    private Long userCouponId;
    private Long couponId;
    private Long orderId;
    private Boolean locked;
    private BigDecimal discount = BigDecimal.ZERO;
    private String status;
    private String message;
}
