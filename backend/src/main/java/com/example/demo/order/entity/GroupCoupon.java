package com.example.demo.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 团购券实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("group_coupon")
public class GroupCoupon extends BaseEntity {

    private Long orderId;
    private Long orderItemId;
    private String code;        // 6位核销码
    private String status;      // pending_use/used/expired
    private LocalDateTime expireAt;
    private LocalDateTime usedAt;
}
