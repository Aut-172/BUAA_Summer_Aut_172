package com.example.demo.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户优惠券实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_coupon")
public class UserCoupon extends BaseEntity {

    private Long userId;
    private Long couponId;
    private String status;       // unused/locked/used/expired
    private LocalDateTime claimedAt;
    private LocalDateTime usedAt;
    private Long orderId;
}
