package com.example.demo.user.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 收货地址请求/响应 DTO
 */
@Data
public class AddressDTO {

    private Long id;
    private String name;       // 收货人姓名
    private String phone;      // 手机号
    private String detail;     // 详细地址
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Boolean isDefault; // 是否默认地址
}
