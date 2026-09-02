package com.example.demo.common.contract.merchant;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MerchantDashboardStats {

    private Integer todayOrders;
    private BigDecimal todayRevenue;
    private Integer pendingOrders;
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private List<MerchantRevenuePoint> dailyRevenueTrend;
}
