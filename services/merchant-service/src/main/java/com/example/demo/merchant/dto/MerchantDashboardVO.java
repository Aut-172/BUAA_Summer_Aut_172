package com.example.demo.merchant.dto;

import com.example.demo.common.contract.merchant.MerchantDashboardStats;
import lombok.Data;

@Data
public class MerchantDashboardVO {

    private MerchantDashboardStats merchant;

    public MerchantDashboardVO(MerchantDashboardStats merchant) {
        this.merchant = merchant;
    }
}
