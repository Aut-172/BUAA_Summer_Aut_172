package com.example.demo.common.controller;

import com.example.demo.common.Result;
import com.example.demo.common.dto.CaptchaVO;
import com.example.demo.common.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码接口
 * 生成图形验证码，用于登录等场景
 */
@Tag(name = "验证码")
@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    @GetMapping
    @Operation(summary = "获取验证码")
    public Result<CaptchaVO> getCaptcha() {
        CaptchaVO captcha = captchaService.generate();
        return Result.success(captcha);
    }
}
