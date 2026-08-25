package com.example.demo.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO
 * 对应前端期望的响应格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String username;
        private String role;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long merchantId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long riderId;
        private String nickname;
        private String phone;
        private String avatar;
        private String status;
    }
}
