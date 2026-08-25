package com.example.demo.user.controller;

import com.example.demo.common.Result;
import com.example.demo.user.dto.*;
import com.example.demo.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端控制器
 * 提供个人中心、收货地址、购物车管理 API
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户的 userId
     */
    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    // ==================== 个人资料 ====================

    /**
     * 获取用户资料
     * GET /api/user/profile
     */
    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile(HttpServletRequest request) {
        return Result.success(userService.getProfile(getUserId(request)));
    }

    /**
     * 更新用户资料
     * PUT /api/user/profile
     */
    @PutMapping("/profile")
    public Result<UserProfileVO> updateProfile(HttpServletRequest request,
                                                @RequestBody UserProfileUpdateRequest body) {
        return Result.success(userService.updateProfile(getUserId(request), body));
    }

    // ==================== 收货地址 ====================

    /**
     * 获取地址列表
     * GET /api/user/addresses
     */
    @GetMapping("/addresses")
    public Result<List<AddressDTO>> getAddressList(HttpServletRequest request) {
        return Result.success(userService.getAddressList(getUserId(request)));
    }

    /**
     * 获取单个地址
     * GET /api/user/addresses/{id}
     */
    @GetMapping("/addresses/{id}")
    public Result<AddressDTO> getAddress(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(userService.getAddress(getUserId(request), id));
    }

    /**
     * 新增地址
     * POST /api/user/addresses
     */
    @PostMapping("/addresses")
    public Result<AddressDTO> addAddress(HttpServletRequest request,
                                          @RequestBody AddressDTO body) {
        return Result.success(userService.addAddress(getUserId(request), body));
    }

    /**
     * 更新地址
     * PUT /api/user/addresses/{id}
     */
    @PutMapping("/addresses/{id}")
    public Result<AddressDTO> updateAddress(HttpServletRequest request,
                                             @PathVariable Long id,
                                             @RequestBody AddressDTO body) {
        return Result.success(userService.updateAddress(getUserId(request), id, body));
    }

    /**
     * 删除地址
     * DELETE /api/user/addresses/{id}
     */
    @DeleteMapping("/addresses/{id}")
    public Result<Void> deleteAddress(HttpServletRequest request, @PathVariable Long id) {
        userService.deleteAddress(getUserId(request), id);
        return Result.success();
    }

    // ==================== 购物车 ====================

    /**
     * 获取购物车列表
     * GET /api/user/cart
     */
    @GetMapping("/cart")
    public Result<List<CartVO>> getCartList(HttpServletRequest request) {
        return Result.success(userService.getCartList(getUserId(request)));
    }

    /**
     * 添加商品到购物车
     * POST /api/user/cart
     */
    @PostMapping("/cart")
    public Result<CartVO> addCart(HttpServletRequest request,
                                   @RequestBody CartRequest body) {
        return Result.success(userService.addCart(getUserId(request), body));
    }

    /**
     * 更新购物车商品数量
     * PUT /api/user/cart/{id}?quantity=2
     */
    @PutMapping("/cart/{id}")
    public Result<CartVO> updateCartQuantity(HttpServletRequest request,
                                              @PathVariable Long id,
                                              @RequestParam Integer quantity) {
        CartVO vo = userService.updateCartQuantity(getUserId(request), id, quantity);
        if (vo == null) {
            return Result.success();
        }
        return Result.success(vo);
    }

    /**
     * 删除购物车项
     * DELETE /api/user/cart/{id}
     */
    @DeleteMapping("/cart/{id}")
    public Result<Void> deleteCart(HttpServletRequest request, @PathVariable Long id) {
        userService.deleteCart(getUserId(request), id);
        return Result.success();
    }

    /**
     * 清空购物车
     * DELETE /api/user/cart
     */
    @DeleteMapping("/cart")
    public Result<Void> clearCart(HttpServletRequest request) {
        userService.clearCart(getUserId(request));
        return Result.success();
    }
}
