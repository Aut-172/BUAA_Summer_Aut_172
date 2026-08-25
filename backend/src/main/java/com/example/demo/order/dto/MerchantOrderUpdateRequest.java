package com.example.demo.order.dto;

import lombok.Data;

/**
 * 商家更新订单状态请求
 */
@Data
public class MerchantOrderUpdateRequest {

    private String status;  // 待取餐|配送中|已完成
    private String eta;     // 预计送达文案
}
