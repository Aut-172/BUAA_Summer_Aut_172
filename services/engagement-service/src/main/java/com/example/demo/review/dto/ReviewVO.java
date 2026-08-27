package com.example.demo.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO {

    private Long id;
    private Long orderId;
    private Long userId;
    private String userName;       // 用户昵称
    private String userAvatar;     // 用户头像
    private Long merchantId;
    private Long productId;
    private String productName;    // 商品名称
    private String productImage;   // 商品图片
    private Integer rating;        // 评分(1-5)
    private String content;        // 评价内容
    private List<String> images;   // 评价图片URL列表
    private LocalDateTime createTime;
}
