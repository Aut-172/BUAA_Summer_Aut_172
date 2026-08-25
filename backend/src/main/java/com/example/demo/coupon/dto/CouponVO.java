package com.example.demo.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券视图对象（前端期望格式）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponVO {

    private Long id;
    private String title;          // 优惠券名称
    private String description;    // 描述（如：满30减10）
    private BigDecimal threshold;  // 满减门槛
    private BigDecimal discount;   // 优惠金额
    private LocalDateTime expireAt; // 过期时间
    private String status;         // unused/used/expired
}
