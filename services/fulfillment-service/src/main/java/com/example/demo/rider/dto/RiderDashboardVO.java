package com.example.demo.rider.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiderDashboardVO {

    private RiderMetrics rider;

    @Data
    @Builder
    public static class RiderMetrics {
        private int todayDeliveries;
        private double todayEarnings;
        private String status;
    }
}
