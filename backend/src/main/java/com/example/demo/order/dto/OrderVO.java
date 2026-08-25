package com.example.demo.order.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String orderNo;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long merchantId;
    private String merchant;
    private String merchantAvatar;
    private String status;
    private BigDecimal total;
    private BigDecimal deliveryFee;
    private BigDecimal discount;
    private String eta;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long riderId;
    private String riderName;
    private String riderPhone;
    private String address;
    private List<OrderItemVO> items;
    private List<String> reviewedProductIds;
    private List<TimelineItem> timeline;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long productId;
        private String name;
        private BigDecimal price;
        private Integer quantity;
        private String image;
        private String specLabel;
        private Boolean reviewed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineItem {
        private String label;
        private String time;
    }
}
