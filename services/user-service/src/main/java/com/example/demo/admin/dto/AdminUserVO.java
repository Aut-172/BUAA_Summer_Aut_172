package com.example.demo.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserVO {

    private Long id;
    private String username;
    private String phone;
    private String nickname;
    private String avatar;
    private String role;
    private String status;
    private LocalDateTime createTime;
}
