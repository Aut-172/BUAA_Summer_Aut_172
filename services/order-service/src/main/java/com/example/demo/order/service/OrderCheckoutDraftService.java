package com.example.demo.order.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.merchant.ProductQuoteRequest;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import com.example.demo.order.client.MerchantProductClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCheckoutDraftService {

    private final MerchantProductClient merchantProductClient;

    public ProductQuoteResponse quoteProducts(ProductQuoteRequest request) {
        Result<ProductQuoteResponse> result = merchantProductClient.quote(request);
        if (result == null) {
            throw BusinessException.badRequest("商家商品服务无响应");
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        ProductQuoteResponse quote = result.getData();
        if (quote == null || !Boolean.TRUE.equals(quote.getAvailable())) {
            throw BusinessException.badRequest("商品不可下单");
        }
        return quote;
    }
}
