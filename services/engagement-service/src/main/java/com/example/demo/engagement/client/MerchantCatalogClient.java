package com.example.demo.engagement.client;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = ServiceNames.MERCHANT_SERVICE, path = "/internal")
public interface MerchantCatalogClient {

    @GetMapping("/merchants/{merchantId}")
    Result<MerchantSnapshot> getMerchantResult(@PathVariable Long merchantId);

    @GetMapping("/products/{productId}")
    Result<ProductSnapshot> getProductResult(@PathVariable Long productId);

    default MerchantSnapshot getMerchant(Long merchantId) {
        return unwrap(getMerchantResult(merchantId), "商家服务暂不可用");
    }

    default ProductSnapshot getProduct(Long productId) {
        return unwrap(getProductResult(productId), "商家服务暂不可用");
    }

    private <T> T unwrap(Result<T> result, String unavailableMessage) {
        if (result == null) {
            throw new BusinessException(503, unavailableMessage);
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    @Data
    class MerchantSnapshot {
        private Long id;
        private String name;
        private String avatar;
    }

    @Data
    class ProductSnapshot {
        private Long id;
        private String name;
        private String image;
    }
}
