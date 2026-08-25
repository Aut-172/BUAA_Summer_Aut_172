package com.example.demo.merchant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品详情 DTO
 */
@Data
public class ProductDTO {

    private Long id;
    private Long merchantId;
    private Long categoryId;
    private String name;
    private String image;
    private BigDecimal originalPrice;
    private BigDecimal price;
    private String description;
    private Integer monthlySales;
    private Integer stock;
    private Integer status;

    /**
     * 规格分组列表
     */
    private List<SpecGroupItem> specGroups;

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
