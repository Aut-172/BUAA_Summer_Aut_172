package com.example.demo.merchant.client;

import com.example.demo.common.BusinessException;
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

    default MerchantDashboardStats getMerchantDashboard(Long merchantId) {
        Result<MerchantDashboardStats> result = getMerchantDashboardResult(merchantId);
        if (result == null) {
            throw new BusinessException(503, "订单服务暂不可用");
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
