package com.example.demo.order.client;

import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import com.example.demo.common.contract.merchant.ProductQuoteRequest;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(contextId = "orderMerchantProductClient", name = ServiceNames.MERCHANT_SERVICE, path = "/internal/products")
public interface MerchantProductClient {

    @PostMapping("/quote")
    Result<ProductQuoteResponse> quote(@Valid @RequestBody ProductQuoteRequest request);
}
