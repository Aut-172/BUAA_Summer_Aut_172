package com.example.demo.merchant.client;

import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import com.example.demo.common.contract.merchant.MerchantDashboardStats;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ServiceNames.ORDER_SERVICE, path = "/internal/orders")
public interface OrderSummaryClient {

    @GetMapping("/merchant-dashboard")
    Result<MerchantDashboardStats> getMerchantDashboardResult(@RequestParam Long merchantId);
}
