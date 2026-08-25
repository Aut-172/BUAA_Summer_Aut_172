package com.example.demo.coupon.controller;

import com.example.demo.common.Result;
import com.example.demo.coupon.dto.CouponVO;
import com.example.demo.coupon.service.CouponService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券控制器
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    /**
     * 获取用户优惠券列表
     * GET /api/coupons
     */
    @GetMapping("/coupons")
    public Result<List<CouponVO>> getCoupons(HttpServletRequest request) {
        return Result.success(couponService.getUserCoupons(getUserId(request)));
    }

    /**
     * 获取可领取的优惠券列表
     * GET /api/coupons/available
     */
    @GetMapping("/coupons/available")
    public Result<List<CouponVO>> getAvailableCoupons() {
        return Result.success(couponService.getAvailableCoupons());
    }

    /**
     * 领取优惠券
     * POST /api/coupons/{id}/claim
     */
    @PostMapping("/coupons/{id}/claim")
    public Result<CouponVO> claimCoupon(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(couponService.claimCoupon(getUserId(request), id));
    }
}
