package com.example.demo.auth.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.dto.RegisterRequest;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.JwtUtil;
import com.example.demo.common.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MerchantMapper merchantMapper;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    @Value("${app.auth.captcha-enabled:true}")
    private boolean captchaEnabled;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public void registerMerchant(RegisterRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        if (merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, request.getUsername())) > 0) {
            throw BusinessException.badRequest("用户名已存在");
        }
        if (merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getPhone, request.getPhone())) > 0) {
            throw BusinessException.badRequest("手机号已注册");
        }

        Merchant merchant = new Merchant();
        merchant.setUsername(request.getUsername());
        merchant.setPassword(passwordEncoder.encode(request.getPassword()));
        merchant.setPhone(request.getPhone());
        merchant.setName(StrUtil.isNotBlank(request.getNickname()) ? request.getNickname() : request.getUsername());
        merchant.setStatus("pending");
        merchant.setRating(BigDecimal.ZERO);
        merchant.setMonthlySales(0);
        merchant.setMinDeliveryFee(BigDecimal.ZERO);
        merchant.setDeliveryFee(new BigDecimal("5.00"));
        merchant.setDeliveryRadius(5);
        merchantMapper.insert(merchant);
    }

    public LoginResponse loginMerchant(LoginRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        Merchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, request.getUsername())
                .or()
                .eq(Merchant::getPhone, request.getUsername()));
        if (merchant == null || !passwordEncoder.matches(request.getPassword(), merchant.getPassword())) {
            throw BusinessException.badRequest("用户名或密码错误");
        }
        if ("frozen".equals(merchant.getStatus())) {
            throw BusinessException.badRequest("商家账号已被冻结，无法登录");
        }

        String token = jwtUtil.generateToken(merchant.getId(), "merchant", merchant.getUsername());
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(merchant.getId())
                .username(merchant.getUsername())
                .role("merchant")
                .merchantId(merchant.getId())
                .nickname(merchant.getName())
                .phone(merchant.getPhone())
                .avatar(merchant.getAvatar())
                .status(merchant.getStatus())
                .build();
        return LoginResponse.builder().accessToken(token).user(userInfo).build();
    }

    private void verifyCaptcha(String key, String code) {
        if (!captchaEnabled) {
            return;
        }
        if (StrUtil.isBlank(key) || StrUtil.isBlank(code) || !captchaService.verify(key, code)) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
    }
}
