package com.example.demo.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单补偿记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_compensation")
public class OrderCompensationRecord extends BaseEntity {

    private String requestId;
    private Long orderId;
    private String action;
    private String targetService;
    private String payload;
    private String status;
    private String message;
}
