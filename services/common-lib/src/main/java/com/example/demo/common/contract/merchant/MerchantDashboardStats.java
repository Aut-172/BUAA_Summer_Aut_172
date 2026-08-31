package com.example.demo.common.contract.merchant;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantDashboardStats {

    private Integer todayOrders;
    private BigDecimal todayRevenue;
    private Integer pendingOrders;
}
