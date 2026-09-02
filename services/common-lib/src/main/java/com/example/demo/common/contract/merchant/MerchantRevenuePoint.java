package com.example.demo.common.contract.merchant;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantRevenuePoint {

    private String date;
    private BigDecimal revenue;
}
