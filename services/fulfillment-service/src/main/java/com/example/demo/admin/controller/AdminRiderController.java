package com.example.demo.admin.controller;

import com.example.demo.admin.dto.AuditRiderRequest;
import com.example.demo.admin.service.AdminRiderService;
import com.example.demo.auth.entity.Rider;
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
@RequestMapping("/api/admin/riders")
@RequiredArgsConstructor
public class AdminRiderController {

    private final AdminRiderService adminRiderService;

    @GetMapping
    public PageResult<Rider> listRiders(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int pageSize,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status) {
        return adminRiderService.listRiders(page, pageSize, keyword, status);
    }

    @PutMapping("/{id}/audit")
    public Result<Rider> auditRider(@PathVariable Long id, @RequestBody AuditRiderRequest request) {
        return Result.success(adminRiderService.auditRider(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Rider> freezeRider(@PathVariable Long id) {
        return Result.success(adminRiderService.freezeRider(id));
    }

    @PutMapping("/{id}/unfreeze")
    public Result<Rider> unfreezeRider(@PathVariable Long id) {
        return Result.success(adminRiderService.unfreezeRider(id));
    }
}
