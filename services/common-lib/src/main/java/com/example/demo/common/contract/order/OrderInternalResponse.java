package com.example.demo.common.contract.order;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrderInternalResponse {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long merchantId;
    private Long riderId;
    private String type;
    private BigDecimal totalAmount;
    private BigDecimal actualAmount;
    private BigDecimal deliveryFee;
    private BigDecimal discount;
    private String status;
    private Long addressId;
    private String addressDetail;
    private Long couponId;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long productId;
        private String name;
        private BigDecimal price;
        private Integer quantity;
        private String image;
        private String specLabel;
        private BigDecimal subtotal;
        private Boolean reviewed;
    }
}
