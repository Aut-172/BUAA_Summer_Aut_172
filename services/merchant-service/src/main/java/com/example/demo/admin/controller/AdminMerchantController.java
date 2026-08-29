package com.example.demo.admin.controller;

import com.example.demo.admin.dto.AuditMerchantRequest;
import com.example.demo.admin.service.AdminMerchantService;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/merchants")
@RequiredArgsConstructor
public class AdminMerchantController {

    private final AdminMerchantService adminMerchantService;

    @GetMapping
    public PageResult<Merchant> listMerchants(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        return adminMerchantService.listMerchants(page, pageSize, keyword, status);
    }

    @PutMapping("/{id}/audit")
    public Result<Merchant> auditMerchant(@PathVariable Long id, @RequestBody AuditMerchantRequest request) {
        return Result.success(adminMerchantService.auditMerchant(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Merchant> freezeMerchant(@PathVariable Long id) {
        return Result.success(adminMerchantService.freezeMerchant(id));
    }

    @PutMapping("/{id}/unfreeze")
    public Result<Merchant> unfreezeMerchant(@PathVariable Long id) {
        return Result.success(adminMerchantService.unfreezeMerchant(id));
    }
}
