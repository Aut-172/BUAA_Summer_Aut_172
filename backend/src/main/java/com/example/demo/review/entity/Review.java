package com.example.demo.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评价实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review")
public class Review extends BaseEntity {

    private Long orderId;
    private Long userId;
    private Long merchantId;
    private Long productId;
    private Integer rating;      // 评分(1-5)
    private String content;      // 评价内容
    private String images;       // 评价图片JSON数组
}
