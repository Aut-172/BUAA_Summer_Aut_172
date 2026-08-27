package com.example.demo.merchant.controller;

import com.example.demo.common.Result;
import com.example.demo.common.contract.merchant.ProductQuoteRequest;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import com.example.demo.merchant.service.MerchantProductInternalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final MerchantProductInternalService merchantProductInternalService;

    @PostMapping("/quote")
    public Result<ProductQuoteResponse> quote(@Valid @RequestBody ProductQuoteRequest request) {
        return Result.success(merchantProductInternalService.quote(request));
    }
}
