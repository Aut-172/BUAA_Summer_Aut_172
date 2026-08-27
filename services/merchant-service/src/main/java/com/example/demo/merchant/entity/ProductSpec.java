package com.example.demo.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品规格变体实体（如：大份 +3元，小份 +0元）
 * 对应数据库 product_spec 表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_spec")
public class ProductSpec extends BaseEntity {

    /**
     * 所属商品ID
     */
    private Long productId;

    /**
     * 规格标签(如: 大份)
     */
    private String label;

    /**
     * 加价金额
     */
    private BigDecimal price;

    /**
     * 规格库存
     */
    private Integer stock;
}
