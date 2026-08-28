package com.example.demo.order.client;

import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import com.example.demo.common.contract.merchant.ProductQuoteRequest;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import com.example.demo.common.contract.merchant.StockChangeRequest;
import com.example.demo.common.contract.merchant.StockChangeResponse;
import com.example.demo.common.BusinessException;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(contextId = "orderMerchantProductClient", name = ServiceNames.MERCHANT_SERVICE, path = "/internal/products")
public interface MerchantProductClient {

    @PostMapping("/quote")
    Result<ProductQuoteResponse> quote(@Valid @RequestBody ProductQuoteRequest request);

    @PostMapping("/reserve")
    Result<StockChangeResponse> reserveResult(@Valid @RequestBody StockChangeRequest request);

    @PostMapping("/release")
    Result<StockChangeResponse> releaseResult(@Valid @RequestBody StockChangeRequest request);

    @GetMapping("/changes/{requestId}")
    Result<StockChangeResponse> getChangeStatusResult(@PathVariable("requestId") String requestId);

    default StockChangeResponse reserve(StockChangeRequest request) {
        return unwrap(reserveResult(request), "商家库存服务暂不可用");
    }

    default StockChangeResponse release(StockChangeRequest request) {
        return unwrap(releaseResult(request), "商家库存服务暂不可用");
    }

    default StockChangeResponse getChangeStatus(String requestId) {
        return unwrap(getChangeStatusResult(requestId), "商家库存服务暂不可用");
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
}
