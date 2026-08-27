package com.example.demo.order.client;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(contextId = "orderMerchantCatalogClient", name = ServiceNames.MERCHANT_SERVICE, path = "/internal")
public interface MerchantCatalogClient {

    @GetMapping("/merchants/{merchantId}")
    Result<MerchantSnapshot> getMerchantResult(@PathVariable Long merchantId);

    default MerchantSnapshot getMerchant(Long merchantId) {
        Result<MerchantSnapshot> result = getMerchantResult(merchantId);
        if (result == null) {
            throw new BusinessException(503, "商家服务暂不可用");
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    @Data
    public static class MerchantSnapshot {
        private Long id;
        private String name;
        private String address;
        private String avatar;
        private String status;
        private BigDecimal minDeliveryFee;
        private BigDecimal deliveryFee;
    }
}
