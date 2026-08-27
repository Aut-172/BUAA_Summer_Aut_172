package com.example.demo.review.dto;

import lombok.Data;

import java.util.List;

/**
 * 提交评价请求
 */
@Data
public class ReviewRequest {

    private Long orderId;
    private List<ItemReview> items; // 逐商品评价

    @Data
    public static class ItemReview {
        private Long productId;
        private Integer rating;     // 评分(1-5)
        private String content;     // 评价内容(可选，不超过200字)
        private List<String> images; // 评价图片URL列表(可选)
    }
}
