package com.example.demo.order.dto;

import lombok.Data;

@Data
public class MerchantOrderUpdateRequest {

    private String status;
    private String eta;
}
