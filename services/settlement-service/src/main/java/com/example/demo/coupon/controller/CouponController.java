package com.example.demo.coupon.controller;

import com.example.demo.common.Result;
import com.example.demo.coupon.dto.CouponVO;
import com.example.demo.coupon.service.SettlementCouponService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CouponController {

    private final SettlementCouponService settlementCouponService;

    @GetMapping("/coupons")
    public Result<List<CouponVO>> getCoupons(HttpServletRequest request) {
        return Result.success(settlementCouponService.getUserCoupons(getUserId(request)));
    }

    @GetMapping("/coupons/available")
    public Result<List<CouponVO>> getAvailableCoupons() {
        return Result.success(settlementCouponService.getAvailableCoupons());
    }

    @PostMapping("/coupons/{id}/claim")
    public Result<CouponVO> claimCoupon(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(settlementCouponService.claimCoupon(getUserId(request), id));
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
