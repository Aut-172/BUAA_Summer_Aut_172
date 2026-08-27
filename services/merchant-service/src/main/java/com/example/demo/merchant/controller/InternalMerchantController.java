package com.example.demo.merchant.controller;

import com.example.demo.auth.entity.Merchant;
import com.example.demo.common.Result;
import com.example.demo.merchant.dto.ProductDTO;
import com.example.demo.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalMerchantController {

    private final MerchantService merchantService;

    @GetMapping("/merchants/{merchantId}")
    public Result<Merchant> getMerchant(@PathVariable Long merchantId) {
        return Result.success(merchantService.getMerchantBasicInfo(merchantId));
    }

    @GetMapping("/products/{productId}")
    public Result<ProductDTO> getProduct(@PathVariable Long productId) {
        return Result.success(merchantService.getProductDetail(productId));
    }
}
