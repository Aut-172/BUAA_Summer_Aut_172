package com.example.demo.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponVO {

    private Long id;
    private String title;
    private String description;
    private BigDecimal threshold;
    private BigDecimal discount;
    private LocalDateTime expireAt;
    private String status;
}
