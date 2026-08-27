package com.example.demo.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 购物车实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cart")
public class Cart extends BaseEntity {

    private Long userId;
    private Long merchantId;
    private Long productId;
    private String name;       // 商品名称(快照)
    private BigDecimal price;  // 商品价格(快照)
    private String image;      // 商品图片
    private Integer quantity;  // 数量
    private String specLabel;  // 规格标签
}
