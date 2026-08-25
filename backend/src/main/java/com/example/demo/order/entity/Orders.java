package com.example.demo.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 * 表名使用 orders 避免与 SQL 关键字 order 冲突
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("orders")
public class Orders extends BaseEntity {

    private String orderNo;
    private Long userId;
    private Long merchantId;
    private Long riderId;
    private String type;            // delivery(外卖)/group(团购)
    private BigDecimal totalAmount;
    private BigDecimal actualAmount;
    private BigDecimal deliveryFee;
    private BigDecimal discount;
    private String status;          // pending_payment/pending_accept/delivering/completed/cancelled/pending_use
    private Long addressId;
    private String addressDetail;
    private String buyerRemark;
    private Long couponId;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
}
