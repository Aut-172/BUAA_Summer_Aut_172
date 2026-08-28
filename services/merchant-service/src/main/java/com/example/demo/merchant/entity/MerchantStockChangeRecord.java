package com.example.demo.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商家库存变更记录，按 requestId 做幂等保护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant_stock_change")
public class MerchantStockChangeRecord extends BaseEntity {

    private String requestId;
    private Long merchantId;
    private Long orderId;
    private String action;
    private String payload;
    private String status;
    private String message;
}
