package com.example.demo.merchant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.merchant.dto.MerchantListDTO;
import com.example.demo.merchant.dto.MerchantProfileDTO;
import com.example.demo.merchant.dto.ProductDTO;
import com.example.demo.merchant.entity.Category;
import com.example.demo.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public merchant catalog APIs owned by merchant-service.
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
}
