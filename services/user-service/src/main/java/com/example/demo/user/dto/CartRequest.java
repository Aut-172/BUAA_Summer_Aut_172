package com.example.demo.user.dto;

import lombok.Data;

/**
 * 购物车操作请求
 */
@Data
public class CartRequest {

    private Long merchantId;
    private Long productId;
    private Integer quantity;  // 数量
    private String specLabel;  // 规格标签
}
