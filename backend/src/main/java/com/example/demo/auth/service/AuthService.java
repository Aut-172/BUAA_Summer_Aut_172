package com.example.demo.auth.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.dto.RegisterRequest;
import com.example.demo.auth.entity.Admin;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.AdminMapper;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.JwtUtil;
import com.example.demo.common.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;
    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    @Value("${app.auth.captcha-enabled:true}")
    private boolean captchaEnabled;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     */
    public void register(RegisterRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        // 校验用户名是否已存在
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())) > 0) {
            throw BusinessException.badRequest("用户名已存在");
        }

        // 校验手机号是否已注册
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone())) > 0) {
            throw BusinessException.badRequest("手机号已注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setNickname(StrUtil.isNotBlank(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setRole("consumer");
        user.setStatus("active");

        userMapper.insert(user);
    }

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .or()
                .eq(User::getPhone, request.getUsername()));

        if (user == null) {
            throw BusinessException.badRequest("用户名或密码错误");
        }

        if ("frozen".equals(user.getStatus())) {
            throw BusinessException.badRequest("账号已被冻结，无法登录");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw BusinessException.badRequest("用户名或密码错误");
        }

        // 生成 JWT
        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getUsername());

        // 构建响应
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .build();

        return LoginResponse.builder()
                .accessToken(token)
                .user(userInfo)
                .build();
    }

    /**
     * 商家注册
     */
    public void registerMerchant(RegisterRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        // 校验用户名是否已存在
        if (merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, request.getUsername())) > 0) {
            throw BusinessException.badRequest("用户名已存在");
        }

        // 校验手机号是否已注册
        if (merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getPhone, request.getPhone())) > 0) {
            throw BusinessException.badRequest("手机号已注册");
        }

        Merchant merchant = new Merchant();
        merchant.setUsername(request.getUsername());
        merchant.setPassword(passwordEncoder.encode(request.getPassword()));
        merchant.setPhone(request.getPhone());
        merchant.setName(request.getNickname() != null ? request.getNickname() : request.getUsername());
        merchant.setStatus("pending");

        merchantMapper.insert(merchant);
    }

    /**
     * 商家登录
     */
    public LoginResponse loginMerchant(LoginRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        Merchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, request.getUsername()));

        if (merchant == null) {
            throw BusinessException.badRequest("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), merchant.getPassword())) {
            throw BusinessException.badRequest("用户名或密码错误");
        }

        if ("pending".equals(merchant.getStatus())) {
            throw BusinessException.badRequest("账号待管理员审核通过后才能登录");
        }
        if ("frozen".equals(merchant.getStatus())) {
            throw BusinessException.badRequest("账号已被冻结，无法登录");
        }

        String token = jwtUtil.generateToken(merchant.getId(), "merchant", merchant.getUsername());

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(merchant.getId())
                .username(merchant.getUsername())
                .role("merchant")
                .merchantId(merchant.getId())
                .nickname(merchant.getName())
                .phone(merchant.getPhone())
                .build();

        return LoginResponse.builder()
                .accessToken(token)
                .user(userInfo)
                .build();
    }

    /**
     * 骑手注册
     */
    public void registerRider(RegisterRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        if (riderMapper.selectCount(new LambdaQueryWrapper<Rider>()
                .eq(Rider::getPhone, request.getPhone())) > 0) {
            throw BusinessException.badRequest("手机号已注册");
        }

        Rider rider = new Rider();
        rider.setName(request.getNickname() != null ? request.getNickname() : request.getUsername());
        rider.setPassword(passwordEncoder.encode(request.getPassword()));
        rider.setPhone(request.getPhone());
        rider.setStatus("pending");

        riderMapper.insert(rider);
    }

    /**
     * 骑手登录
     */
    public LoginResponse loginRider(LoginRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        Rider rider = riderMapper.selectOne(new LambdaQueryWrapper<Rider>()
                .eq(Rider::getPhone, request.getUsername())
                .or()
                .eq(Rider::getName, request.getUsername()));

        if (rider == null) {
            throw BusinessException.badRequest("手机号或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), rider.getPassword())) {
            throw BusinessException.badRequest("手机号或密码错误");
        }

        if ("pending".equals(rider.getStatus())) {
            throw BusinessException.badRequest("账号待管理员审核通过后才能登录");
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
                .build();

        return LoginResponse.builder()
                .accessToken(token)
                .user(userInfo)
                .build();
    }

    /**
     * 管理员登录
     */
    public LoginResponse loginAdmin(LoginRequest request) {
        verifyCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, request.getUsername()));

        if (admin == null) {
            throw BusinessException.badRequest("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw BusinessException.badRequest("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(admin.getId(), "admin", admin.getUsername());

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .role("admin")
                .nickname("管理员")
                .build();

        return LoginResponse.builder()
                .accessToken(token)
                .user(userInfo)
                .build();
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
