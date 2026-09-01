package com.example.demo.merchant.service;

import com.example.demo.common.contract.ServiceNames;
import com.example.demo.common.contract.merchant.MerchantDashboardStats;
import com.example.demo.merchant.dto.MerchantDashboardVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MerchantDashboardFallbackProvider {

    private final String fallbackMessage;

    public MerchantDashboardFallbackProvider(
            @Value("${app.fault-tolerance.merchant-dashboard.fallback-message:订单服务暂不可用，已返回临时看板数据，请稍后刷新。}")
            String fallbackMessage) {
        this.fallbackMessage = fallbackMessage;
    }

    public MerchantDashboardVO orderSummaryUnavailable(String reason) {
        MerchantDashboardStats stats = new MerchantDashboardStats();
        stats.setTodayOrders(0);
        stats.setTodayRevenue(BigDecimal.ZERO);
        stats.setPendingOrders(0);
        return MerchantDashboardVO.degraded(stats, ServiceNames.ORDER_SERVICE, fallbackMessage, normalizeReason(reason));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "remote dependency unavailable";
        }
        String trimmed = reason.trim();
        if (trimmed.length() <= 160) {
            return trimmed;
        }
        return trimmed.substring(0, 160);
    }
}
