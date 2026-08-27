package com.example.demo.engagement.client;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@FeignClient(name = ServiceNames.ORDER_SERVICE, path = "/internal")
public interface OrderClient {

    @GetMapping("/orders/{orderId}/participants")
    Result<OrderSnapshot> getParticipantOrderResult(@PathVariable Long orderId,
                                                    @RequestParam Long participantId,
                                                    @RequestParam String participantType);

    @GetMapping("/orders/{orderId}")
    Result<OrderSnapshot> getOrderResult(@PathVariable Long orderId);

    @PostMapping("/orders/{orderId}/reviewed-items")
    Result<Void> markReviewedItemsResult(@PathVariable Long orderId, @RequestBody ReviewedItemsCommand command);

    default OrderSnapshot getParticipantOrder(Long orderId, Long participantId, String participantType) {
        return unwrap(getParticipantOrderResult(orderId, participantId, participantType), "订单服务暂不可用");
    }

    default OrderSnapshot getOrder(Long orderId) {
        return unwrap(getOrderResult(orderId), "订单服务暂不可用");
    }

    default void markReviewedItems(Long orderId, List<Long> productIds) {
        unwrap(markReviewedItemsResult(orderId, new ReviewedItemsCommand(productIds)), "订单服务暂不可用");
    }

    private <T> T unwrap(Result<T> result, String unavailableMessage) {
        if (result == null) {
            throw new BusinessException(503, unavailableMessage);
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    record ReviewedItemsCommand(List<Long> productIds) {
    }

    @Data
    class OrderSnapshot {
        private Long id;
        private String orderNo;
        private Long userId;
        private Long merchantId;
        private Long riderId;
        private String merchant;
        private String merchantAvatar;
        private String status;
        private BigDecimal total;
        private BigDecimal deliveryFee;
        private BigDecimal discount;
        private String eta;
        private LocalDateTime createdAt;
        private LocalDateTime paidAt;
        private String address;
        private List<OrderItemSnapshot> items;
        private List<String> reviewedProductIds;
        private List<TimelineItem> timeline;
    }

    @Data
    class OrderItemSnapshot {
        private Long productId;
        private String name;
        private BigDecimal price;
        private Integer quantity;
        private String image;
        private String specLabel;
        private Boolean reviewed;
    }

    @Data
    class TimelineItem {
        private String label;
        private String time;
    }
}
