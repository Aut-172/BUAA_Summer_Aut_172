package com.example.demo.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 购物车视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartVO {

    private Long id;
    private Long merchantId;
    private String merchantName;
    private Long productId;
    private String name;
    private BigDecimal price;
    private String image;
    private Integer quantity;
    private String specLabel;
    private BigDecimal subtotal; // 小计 = price * quantity
}
