package com.example.demo.merchant.controller;

import com.example.demo.common.Result;
import com.example.demo.common.contract.merchant.ProductQuoteRequest;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import com.example.demo.common.contract.merchant.StockChangeRequest;
import com.example.demo.common.contract.merchant.StockChangeResponse;
import com.example.demo.merchant.service.MerchantProductInternalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping("/reserve")
    public Result<StockChangeResponse> reserve(@Valid @RequestBody StockChangeRequest request) {
        return Result.success(merchantProductInternalService.reserve(request));
    }

    @PostMapping("/release")
    public Result<StockChangeResponse> release(@Valid @RequestBody StockChangeRequest request) {
        return Result.success(merchantProductInternalService.release(request));
    }

    @GetMapping("/changes/{requestId}")
    public Result<StockChangeResponse> getChangeStatus(@PathVariable String requestId) {
        return Result.success(merchantProductInternalService.getChangeStatus(requestId));
    }
}
