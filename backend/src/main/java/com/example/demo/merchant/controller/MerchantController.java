package com.example.demo.merchant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.merchant.dto.MerchantListDTO;
import com.example.demo.merchant.dto.MerchantProfileDTO;
import com.example.demo.merchant.dto.ProductDTO;
import com.example.demo.merchant.entity.Category;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.entity.ProductSpec;
import com.example.demo.merchant.entity.SpecGroup;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Merchant-facing and public merchant APIs.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @GetMapping("/merchants")
    public Result<PageResult<MerchantListDTO>> getMerchantList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<MerchantListDTO> pageResult = merchantService.getMerchantListWithProducts(keyword, category, page, size);
        return Result.success(PageResult.of(pageResult));
    }

    @GetMapping("/merchants/{id}")
    public Result<MerchantProfileDTO> getMerchantDetail(@PathVariable Long id) {
        return Result.success(merchantService.getMerchantDetail(id));
    }

    @GetMapping("/products/{id}")
    public Result<ProductDTO> getProductDetail(@PathVariable Long id) {
        return Result.success(merchantService.getProductDetail(id));
    }

    @GetMapping("/categories")
    public Result<List<Category>> getAllCategories() {
        return Result.success(merchantService.getAllCategories());
    }

    @GetMapping("/merchant/profile")
    public Result<Merchant> getMyProfile(HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        return Result.success(merchantService.getMerchantBasicInfo(merchantId));
    }

    @PutMapping("/merchant/profile")
    public Result<Merchant> updateProfile(HttpServletRequest request, @RequestBody Merchant merchant) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        merchant.setUsername(null);
        merchant.setPassword(null);
        return Result.success(merchantService.updateMerchantProfile(merchantId, merchant));
    }

    @GetMapping("/merchant/products")
    public Result<List<Product>> getMyProducts(HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        return Result.success(merchantService.getMerchantProducts(merchantId));
    }

    @PostMapping("/merchant/products")
    public Result<Product> addProduct(HttpServletRequest request, @RequestBody Product product) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        return Result.success(merchantService.addProduct(merchantId, product));
    }

    @PutMapping("/merchant/products")
    public Result<Product> updateProduct(HttpServletRequest request, @RequestBody Product product) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        return Result.success(merchantService.updateProduct(merchantId, product));
    }

    @DeleteMapping("/merchant/products/{productId}")
    public Result<Void> deleteProduct(HttpServletRequest request, @PathVariable Long productId) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        merchantService.deleteProduct(merchantId, productId);
        return Result.success();
    }

    @PostMapping("/merchant/spec-groups")
    public Result<Void> addSpecGroup(HttpServletRequest request, @RequestBody SpecGroup specGroup) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        merchantService.addSpecGroup(merchantId, specGroup);
        return Result.success();
    }

    @DeleteMapping("/merchant/spec-groups/{groupId}")
    public Result<Void> deleteSpecGroup(HttpServletRequest request, @PathVariable Long groupId) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        merchantService.deleteSpecGroup(merchantId, groupId);
        return Result.success();
    }

    @PostMapping("/merchant/product-specs")
    public Result<Void> addProductSpec(HttpServletRequest request, @RequestBody ProductSpec productSpec) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        merchantService.addProductSpec(merchantId, productSpec);
        return Result.success();
    }

    @DeleteMapping("/merchant/product-specs/{specId}")
    public Result<Void> deleteProductSpec(HttpServletRequest request, @PathVariable Long specId) {
        Long merchantId = (Long) request.getAttribute("merchantId");
        if (merchantId == null) {
            return Result.unauthorized("请先登录商家账号");
        }
        merchantService.deleteProductSpec(merchantId, specId);
        return Result.success();
    }
}
