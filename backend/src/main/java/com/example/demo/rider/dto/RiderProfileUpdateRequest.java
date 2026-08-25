package com.example.demo.rider.dto;

import lombok.Data;

@Data
public class RiderProfileUpdateRequest {

    private String nickname;
    private String phone;
    private String serviceArea;
    private String avatar;
}
