package com.example.demo.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 验证码响应 VO
 */
@Data
@AllArgsConstructor
@Schema(description = "验证码响应")
public class CaptchaVO {

    @Schema(description = "验证码唯一标识（用于后续校验）")
    private String key;

    @Schema(description = "验证码图片 Base64（data:image/png;base64,...）")
    private String image;
}
