package com.example.demo.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仪表盘 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO {

    private ConsumerData consumer;
    private MerchantData merchant;
    private RiderData rider;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsumerData {
        private Integer orderCount;
        private Integer favoriteMerchants;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantData {
        private Integer todayOrders;
        private Double todayRevenue;
        private Integer pendingOrders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiderData {
        private Integer todayDeliveries;
        private Double todayEarnings;
        private String status;
    }
}
