package com.example.demo.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon")
public class Coupon extends BaseEntity {

    private String name;
    private BigDecimal discount;       // 优惠金额
    private BigDecimal threshold;      // 满减门槛
    private LocalDateTime startTime;   // 有效开始时间
    private LocalDateTime endTime;     // 有效结束时间
    private Integer totalCount;        // 发行总量
    private Integer claimedCount;      // 已领取数量
    private Integer limitPerUser;      // 每人限领数量
    private String status;             // unreleased/released/ended
}
