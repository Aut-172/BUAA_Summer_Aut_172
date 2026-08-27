package com.example.demo.auth.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.dto.RegisterRequest;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.JwtUtil;
import com.example.demo.common.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RiderMapper riderMapper;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    @Value("${app.auth.captcha-enabled:true}")
    private boolean captchaEnabled;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public void registerRider(RegisterRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        if (riderMapper.selectCount(new LambdaQueryWrapper<Rider>()
                .eq(Rider::getPhone, request.getPhone())) > 0) {
            throw BusinessException.badRequest("手机号已注册");
        }

        Rider rider = new Rider();
        rider.setName(StrUtil.isNotBlank(request.getNickname()) ? request.getNickname() : request.getUsername());
        rider.setPassword(passwordEncoder.encode(request.getPassword()));
        rider.setPhone(request.getPhone());
        rider.setStatus("pending");
        riderMapper.insert(rider);
    }

    public LoginResponse loginRider(LoginRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        Rider rider = riderMapper.selectOne(new LambdaQueryWrapper<Rider>()
                .eq(Rider::getPhone, request.getUsername())
                .or()
                .eq(Rider::getName, request.getUsername()));
        if (rider == null || !passwordEncoder.matches(request.getPassword(), rider.getPassword())) {
            throw BusinessException.badRequest("手机号或密码错误");
        }
        if ("frozen".equals(rider.getStatus())) {
            throw BusinessException.badRequest("账号已被冻结，无法登录");
        }

        String token = jwtUtil.generateToken(rider.getId(), "rider", rider.getName());
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(rider.getId())
                .username(rider.getName())
                .role("rider")
                .riderId(rider.getId())
                .nickname(rider.getName())
                .phone(rider.getPhone())
                .status(rider.getStatus())
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
