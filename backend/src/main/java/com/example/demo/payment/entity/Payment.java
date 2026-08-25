package com.example.demo.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment")
public class Payment extends BaseEntity {

    private Long orderId;
    private BigDecimal amount;
    private String payMethod;      // ALIPAY/WECHAT
    private String transactionId;  // 支付流水号
    private String status;         // PENDING/SUCCESS/FAIL
    private LocalDateTime payTime;
}
