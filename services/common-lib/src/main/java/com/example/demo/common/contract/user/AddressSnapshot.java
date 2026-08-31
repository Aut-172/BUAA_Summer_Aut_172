package com.example.demo.common.contract.user;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddressSnapshot {

    private Long id;
    private String name;
    private String phone;
    private String detail;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Boolean isDefault;
}
