package com.example.demo.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.admin.service.AdminService;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.entity.User;
import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.order.entity.Orders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台控制器
 */
@Tag(name = "管理后台")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ==================== 用户管理 ====================

    @Operation(summary = "获取用户列表")
    @GetMapping("/users")
    public PageResult<User> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        IPage<User> result = adminService.listUsers(page, pageSize, keyword, status);
        return PageResult.of(result);
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/users/{id}")
    public Result<User> getUserDetail(@PathVariable Long id) {
        return Result.success(adminService.getUserDetail(id));
    }

    @Operation(summary = "删除用户（冻结）")
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return Result.success();
    }

    @Operation(summary = "解冻用户")
    @PutMapping("/users/{id}/unfreeze")
    public Result<Void> unfreezeUser(@PathVariable Long id) {
        adminService.unfreezeUser(id);
        return Result.success();
    }

    // ==================== 商家管理 ====================

    @Operation(summary = "获取商家列表")
    @GetMapping("/merchants")
    public PageResult<Merchant> listMerchants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        IPage<Merchant> result = adminService.listMerchants(page, pageSize, keyword, status);
        return PageResult.of(result);
    }

    @Operation(summary = "获取商家详情")
    @GetMapping("/merchants/{id}")
    public Result<Merchant> getMerchantDetail(@PathVariable Long id) {
        return Result.success(adminService.getMerchantDetail(id));
    }

    @Operation(summary = "审核商家")
    @PutMapping("/merchants/{id}/audit")
    public Result<Void> auditMerchant(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String opinion = body.get("opinion");
        adminService.auditMerchant(id, status, opinion);
        return Result.success();
    }

    @Operation(summary = "删除商家（冻结）")
    @DeleteMapping("/merchants/{id}")
    public Result<Void> deleteMerchant(@PathVariable Long id) {
        adminService.deleteMerchant(id);
        return Result.success();
    }

    @Operation(summary = "解冻商家")
    @PutMapping("/merchants/{id}/unfreeze")
    public Result<Void> unfreezeMerchant(@PathVariable Long id) {
        adminService.unfreezeMerchant(id);
        return Result.success();
    }

    // ==================== 骑手管理 ====================

    @Operation(summary = "获取骑手列表")
    @GetMapping("/riders")
    public PageResult<Rider> listRiders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        IPage<Rider> result = adminService.listRiders(page, pageSize, keyword, status);
        return PageResult.of(result);
    }

    @Operation(summary = "获取骑手详情")
    @GetMapping("/riders/{id}")
    public Result<Rider> getRiderDetail(@PathVariable Long id) {
        return Result.success(adminService.getRiderDetail(id));
    }

    @Operation(summary = "审核骑手")
    @PutMapping("/riders/{id}/audit")
    public Result<Void> auditRider(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String opinion = body.get("opinion");
        adminService.auditRider(id, status, opinion);
        return Result.success();
    }

    @Operation(summary = "删除骑手（冻结）")
    @DeleteMapping("/riders/{id}")
    public Result<Void> deleteRider(@PathVariable Long id) {
        adminService.deleteRider(id);
        return Result.success();
    }

    @Operation(summary = "解冻骑手")
    @PutMapping("/riders/{id}/unfreeze")
    public Result<Void> unfreezeRider(@PathVariable Long id) {
        adminService.unfreezeRider(id);
        return Result.success();
    }

    // ==================== 订单管理 ====================

    @Operation(summary = "获取订单列表")
    @GetMapping("/orders")
    public PageResult<Orders> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        IPage<Orders> result = adminService.listOrders(page, pageSize, keyword, status, type);
        return PageResult.of(result);
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/orders/{id}")
    public Result<Orders> getOrderDetail(@PathVariable Long id) {
        return Result.success(adminService.getOrderDetail(id));
    }
}
