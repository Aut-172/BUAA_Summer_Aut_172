package com.example.demo.merchant.controller;

import com.example.demo.auth.entity.Merchant;
import com.example.demo.common.Result;
import com.example.demo.merchant.client.OrderSummaryClient;
import com.example.demo.merchant.dto.MerchantDashboardVO;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.service.MerchantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantSelfController {

    private final MerchantService merchantService;
    private final OrderSummaryClient orderSummaryClient;

    @GetMapping("/dashboard")
    public Result<MerchantDashboardVO> getDashboard(HttpServletRequest request) {
        Long merchantId = getMerchantId(request);
        return Result.success(new MerchantDashboardVO(orderSummaryClient.getMerchantDashboard(merchantId)));
    }

    @GetMapping("/profile")
    public Result<Merchant> getProfile(HttpServletRequest request) {
        return Result.success(merchantService.getMerchantBasicInfo(getMerchantId(request)));
    }

    @PutMapping("/profile")
    public Result<Merchant> updateProfile(HttpServletRequest request, @RequestBody Merchant body) {
        return Result.success(merchantService.updateMerchantProfile(getMerchantId(request), body));
    }

    @GetMapping("/products")
    public Result<List<Product>> getProducts(HttpServletRequest request) {
        return Result.success(merchantService.getMerchantProducts(getMerchantId(request)));
    }

    @GetMapping("/products/{productId}")
    public Result<Product> getProduct(HttpServletRequest request, @PathVariable Long productId) {
        return Result.success(merchantService.getMerchantProduct(getMerchantId(request), productId));
    }

    @PostMapping("/products")
    public Result<Product> addProduct(HttpServletRequest request, @RequestBody Product body) {
        return Result.success(merchantService.addProduct(getMerchantId(request), body));
    }

    @PutMapping("/products")
    public Result<Product> updateProduct(HttpServletRequest request, @RequestBody Product body) {
        return Result.success(merchantService.updateProduct(getMerchantId(request), body));
    }

    @DeleteMapping("/products/{productId}")
    public Result<Void> deleteProduct(HttpServletRequest request, @PathVariable Long productId) {
        merchantService.deleteProduct(getMerchantId(request), productId);
        return Result.success();
    }

    private Long getMerchantId(HttpServletRequest request) {
        return (Long) request.getAttribute("merchantId");
    }
}
