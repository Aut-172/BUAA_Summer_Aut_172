package com.example.demo.rider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 骑手任务视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderTaskVO {

    private List<TaskItem> available;   // 待抢单
    private List<TaskItem> assigned;    // 进行中
    private List<TaskItem> completed;   // 已完成
    private RiderStats stats;           // 统计数据

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        private Long id;
        private String orderNo;
        private Long userId;
        private Long merchantId;
        private String merchant;
        private String merchantAvatar;
        private String items;           // 商品摘要
        private String pickup;          // 取货地址（商家地址）
        private String destination;     // 送达地址
        private String status;          // 待取餐/配送中/已完成
        private String eta;
        private Double total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiderStats {
        private Double totalEarnings;
        private Integer completedOrders;
        private String totalDistance;
    }
}
