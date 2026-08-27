package com.example.demo.auth.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.dto.RegisterRequest;
import com.example.demo.auth.entity.Admin;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.AdminMapper;
import com.example.demo.auth.mapper.UserMapper;
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

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    @Value("${app.auth.captcha-enabled:true}")
    private boolean captchaEnabled;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public void register(RegisterRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())) > 0) {
            throw BusinessException.badRequest("用户名已存在");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone())) > 0) {
            throw BusinessException.badRequest("手机号已注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setNickname(StrUtil.isNotBlank(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setRole("consumer");
        user.setStatus("active");
        userMapper.insert(user);
    }

    public LoginResponse login(LoginRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .or()
                .eq(User::getPhone, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw BusinessException.badRequest("用户名或密码错误");
        }
        if ("frozen".equals(user.getStatus())) {
            throw BusinessException.badRequest("账号已被冻结，无法登录");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getUsername());
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .build();
        return LoginResponse.builder().accessToken(token).user(userInfo).build();
    }

    public LoginResponse loginAdmin(LoginRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, request.getUsername()));
        if (admin == null || !passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw BusinessException.badRequest("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(admin.getId(), "admin", admin.getUsername());
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .role("admin")
                .nickname("管理员")
                .status("active")
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
