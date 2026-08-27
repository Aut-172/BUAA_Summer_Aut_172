package com.example.demo.merchant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商家列表 DTO（含推荐商品）
 */
@Data
public class MerchantListDTO {

    private Long id;
    private String name;
    private String phone;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String businessHours;
    private String category;
    private String description;
    private String avatar;
    private String tags;
    private String status;
    private Double rating;
    private Integer monthlySales;
    private BigDecimal minDeliveryFee;
    private BigDecimal deliveryFee;
    private Integer deliveryRadius;

    /**
     * 推荐商品列表（最多3个）
     */
    private List<ProductItem> products;

    @Data
    public static class ProductItem {
        private Long id;
        private String name;
        private String image;
        private BigDecimal price;
        private Integer monthlySales;
    }
}
