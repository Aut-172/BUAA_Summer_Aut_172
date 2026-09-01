package com.example.demo.merchant.dto;

import com.example.demo.common.contract.merchant.MerchantDashboardStats;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantDashboardVO {

    private MerchantDashboardStats merchant;
    private boolean degraded;
    private String degradedDependency;
    private String degradationMessage;
    private String fallbackReason;
    private LocalDateTime fallbackAt;

    public MerchantDashboardVO(MerchantDashboardStats merchant) {
        this.merchant = merchant;
    }

    public static MerchantDashboardVO normal(MerchantDashboardStats merchant) {
        return new MerchantDashboardVO(merchant);
    }

    public static MerchantDashboardVO degraded(MerchantDashboardStats merchant,
                                               String degradedDependency,
                                               String degradationMessage,
                                               String fallbackReason) {
        MerchantDashboardVO vo = new MerchantDashboardVO(merchant);
        vo.setDegraded(true);
        vo.setDegradedDependency(degradedDependency);
        vo.setDegradationMessage(degradationMessage);
        vo.setFallbackReason(fallbackReason);
        vo.setFallbackAt(LocalDateTime.now());
        return vo;
    }
}
