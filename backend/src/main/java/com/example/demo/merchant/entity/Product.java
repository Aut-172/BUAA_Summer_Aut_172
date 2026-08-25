package com.example.demo.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    /**
     * 所属商家ID
     */
    private Long merchantId;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品图片
     */
    private String image;

    /**
     * 商品原价（数据库中无此列）
     */
    @TableField(exist = false)
    private BigDecimal originalPrice;

    /**
     * 商品现价
     */
    private BigDecimal price;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 月销量
     */
    private Integer monthlySales;

    /**
     * 库存
     */
    private Integer stock;

    /**
     * 商品类型：normal-普通，delivery-外卖
     */
    private String type;

    /**
     * 状态：active-上架，inactive-下架
     */
    private String status;

    /**
     * 商品图册（JSON数组）
     */
    private String gallery;

    /**
     * 排序号（数据库中无此列）
     */
    @TableField(exist = false)
    private Integer sortOrder;
}
