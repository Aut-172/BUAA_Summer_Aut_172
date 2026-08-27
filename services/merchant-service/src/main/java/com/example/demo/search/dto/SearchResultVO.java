package com.example.demo.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 搜索结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultVO {

    /**
     * 商家信息
     */
    private Long id;
    private String name;
    private String category;
    private BigDecimal rating;
    private String sales;
    private String distance;
    private String fee;
    private Boolean open;
    private String description;
    private String address;
    private String phone;
    private String avatar;
    private List<String> tags;
    private List<ProductItem> products;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductItem {
        private Long id;
        private String category;
        private String name;
        private String desc;
        private BigDecimal price;
        private Integer sales;
        private Integer stock;
        private String image;
        private List<String> gallery;
    }
}
