package com.example.demo.coupon.controller;

import com.example.demo.common.Result;
import com.example.demo.common.contract.settlement.CouponLockRequest;
import com.example.demo.common.contract.settlement.CouponLockResponse;
import com.example.demo.coupon.service.SettlementCouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/coupon-locks")
@RequiredArgsConstructor
public class InternalCouponController {

    private final SettlementCouponService settlementCouponService;

    @PostMapping
    public Result<CouponLockResponse> lock(@Valid @RequestBody CouponLockRequest request) {
        return Result.success(settlementCouponService.lockCoupon(request));
    }

    @PostMapping("/{orderId}/release")
    public Result<CouponLockResponse> release(@PathVariable Long orderId) {
        return Result.success(settlementCouponService.releaseCoupon(orderId));
    }

    @PostMapping("/{orderId}/confirm")
    public Result<CouponLockResponse> confirm(@PathVariable Long orderId) {
        return Result.success(settlementCouponService.confirmCoupon(orderId));
    }
}
