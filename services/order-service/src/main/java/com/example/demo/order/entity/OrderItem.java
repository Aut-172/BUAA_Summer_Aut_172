package com.example.demo.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends BaseEntity {

    private Long orderId;
    private Long productId;
    private String name;        // 商品名称(快照)
    private BigDecimal price;   // 商品价格(快照)
    private Integer quantity;
    private String image;
    private String specLabel;
    private BigDecimal subtotal;
    private Boolean reviewed;   // 是否已评价
}
