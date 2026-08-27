package com.example.demo.merchant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商家详情 DTO（含商品列表）
 */
@Data
public class MerchantProfileDTO {

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
    private List<String> tags;
    private String status;
    private Double rating;
    private Integer monthlySales;
    private BigDecimal minDeliveryFee;
    private BigDecimal deliveryFee;
    private Integer deliveryRadius;

    /**
     * 商品分类列表（每个分类下含商品列表）
     */
    private List<CategoryWithProducts> categoryList;

    @Data
    public static class CategoryWithProducts {
        private Long id;
        private String name;
        private List<ProductItem> products;
    }

    @Data
    public static class ProductItem {
        private Long id;
        private Long categoryId;
        private String name;
        private String image;
        private BigDecimal originalPrice;
        private BigDecimal price;
        private String description;
        private Integer monthlySales;
        private Integer stock;
        private String status;
        private List<SpecGroupItem> specGroups;
    }

    @Data
    public static class SpecGroupItem {
        private Long id;
        private String name;
        private Integer multiple;
        private List<SpecItem> specs;
    }

    @Data
    public static class SpecItem {
        private Long id;
        private String name;
        private BigDecimal extraPrice;
    }
}
