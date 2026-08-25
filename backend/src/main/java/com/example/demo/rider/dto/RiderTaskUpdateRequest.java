package com.example.demo.rider.dto;

import lombok.Data;

/**
 * 骑手任务更新请求
 */
@Data
public class RiderTaskUpdateRequest {

    private String status;   // 待取餐|配送中|已完成
    private Long riderId;
    private String riderName;
    private String eta;
}
