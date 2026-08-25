package com.example.demo.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商家实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant")
public class Merchant extends BaseEntity {

    private String username;
    @JsonIgnore
    private String password;
    private String name;
    private String phone;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String businessHours;
    private String category;
    private String description;
    private String avatar;
    private String tags;
    private String status;       // pending/active/rest/frozen
    private BigDecimal rating;
    private Integer monthlySales;
    private BigDecimal minDeliveryFee;
    private BigDecimal deliveryFee;
    private Integer deliveryRadius;
}
