package com.example.demo.auth.controller;

import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.dto.RegisterRequest;
import com.example.demo.auth.service.AuthService;
import com.example.demo.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 处理用户/商家/骑手/管理员的注册和登录
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 商家注册
     * POST /api/auth/merchant/register
     */
    @PostMapping("/merchant/register")
    public Result<Void> registerMerchant(@Valid @RequestBody RegisterRequest request) {
        authService.registerMerchant(request);
        return Result.success();
    }

    /**
     * 商家登录
     * POST /api/auth/merchant/login
     */
    @PostMapping("/merchant/login")
    public Result<LoginResponse> loginMerchant(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.loginMerchant(request));
    }

    /**
     * 骑手注册
     * POST /api/auth/rider/register
     */
    @PostMapping("/rider/register")
    public Result<Void> registerRider(@Valid @RequestBody RegisterRequest request) {
        authService.registerRider(request);
        return Result.success();
    }

    /**
     * 骑手登录
     * POST /api/auth/rider/login
     */
    @PostMapping("/rider/login")
    public Result<LoginResponse> loginRider(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.loginRider(request));
    }

    /**
     * 管理员登录
     * POST /api/auth/admin/login
     */
    @PostMapping("/admin/login")
    public Result<LoginResponse> loginAdmin(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.loginAdmin(request));
    }
}
