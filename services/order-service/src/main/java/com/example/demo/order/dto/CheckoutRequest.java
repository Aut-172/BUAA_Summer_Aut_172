package com.example.demo.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CheckoutRequest {

    private Long merchantId;
    private List<CheckoutItem> items;
    private BigDecimal total;
    private BigDecimal deliveryFee;
    private BigDecimal discount;
    private Long addressId;
    private String address;
    private Long couponId;

    @Data
    public static class CheckoutItem {
        private Long productId;
        private String name;
        private BigDecimal price;
        private Integer quantity;
        private String image;
        private String specLabel;
    }
}
