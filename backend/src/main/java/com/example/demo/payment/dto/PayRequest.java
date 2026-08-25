package com.example.demo.payment.dto;

import lombok.Data;

/**
 * 支付请求
 */
@Data
public class PayRequest {

    private String payMethod; // ALIPAY/WECHAT
}
