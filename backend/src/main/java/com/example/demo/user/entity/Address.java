package com.example.demo.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("address")
public class Address extends BaseEntity {

    private Long userId;
    private String name;       // 收货人姓名
    private String phone;      // 手机号
    private String detail;     // 详细地址
    private java.math.BigDecimal longitude;
    private java.math.BigDecimal latitude;
    private Boolean isDefault; // 是否默认地址
}
