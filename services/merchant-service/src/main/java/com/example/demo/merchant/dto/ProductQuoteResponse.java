package com.example.demo.merchant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductQuoteResponse {

    private String requestId;
    private Long merchantId;
    private Boolean available;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private List<Item> items = new ArrayList<>();
    private List<String> messages = new ArrayList<>();

    @Data
    public static class Item {
        private Long productId;
        private Long merchantId;
        private String name;
        private String image;
        private String specLabel;
        private BigDecimal unitPrice;
        private Integer quantity;
        private Integer stock;
        private BigDecimal subtotal;
        private Boolean active;
        private Boolean stockEnough;
        private String message;
    }
}
