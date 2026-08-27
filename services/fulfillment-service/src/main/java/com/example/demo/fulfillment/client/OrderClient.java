package com.example.demo.fulfillment.client;

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

    @GetMapping("/fulfillment/tasks/available")
    Result<List<OrderTaskSnapshot>> getAvailableTasksResult();

    @GetMapping("/fulfillment/tasks/assigned")
    Result<List<OrderTaskSnapshot>> getAssignedTasksResult(@RequestParam Long riderId);

    @GetMapping("/fulfillment/tasks/completed")
    Result<List<OrderTaskSnapshot>> getCompletedTasksResult(@RequestParam Long riderId);

    @PostMapping("/fulfillment/orders/{orderId}/assign-rider")
    Result<OrderTaskSnapshot> assignRiderResult(@PathVariable Long orderId, @RequestBody RiderTaskCommand command);

    @PostMapping("/fulfillment/orders/{orderId}/delivered")
    Result<OrderTaskSnapshot> markDeliveredResult(@PathVariable Long orderId, @RequestBody RiderTaskCommand command);

    @GetMapping("/fulfillment/orders/{orderId}")
    Result<OrderTaskSnapshot> getTaskResult(@PathVariable Long orderId, @RequestParam Long riderId);

    @GetMapping("/orders/{orderId}")
    Result<OrderTaskSnapshot> getDeliveryOrderResult(@PathVariable Long orderId);

    default List<OrderTaskSnapshot> getAvailableTasks() {
        return unwrap(getAvailableTasksResult(), "订单服务暂不可用");
    }

    default List<OrderTaskSnapshot> getAssignedTasks(Long riderId) {
        return unwrap(getAssignedTasksResult(riderId), "订单服务暂不可用");
    }

    default List<OrderTaskSnapshot> getCompletedTasks(Long riderId) {
        return unwrap(getCompletedTasksResult(riderId), "订单服务暂不可用");
    }

    default OrderTaskSnapshot assignRider(Long orderId, Long riderId) {
        return unwrap(assignRiderResult(orderId, new RiderTaskCommand(riderId)), "订单服务暂不可用");
    }

    default OrderTaskSnapshot markDelivered(Long orderId, Long riderId) {
        return unwrap(markDeliveredResult(orderId, new RiderTaskCommand(riderId)), "订单服务暂不可用");
    }

    default OrderTaskSnapshot getTask(Long orderId, Long riderId) {
        return unwrap(getTaskResult(orderId, riderId), "订单服务暂不可用");
    }

    default OrderTaskSnapshot getDeliveryOrder(Long orderId) {
        return unwrap(getDeliveryOrderResult(orderId), "订单服务暂不可用");
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

    record RiderTaskCommand(Long riderId) {
    }

    @Data
    class OrderTaskSnapshot {
        private Long id;
        private String orderNo;
        private Long userId;
        private Long merchantId;
        private Long riderId;
        private String status;
        private String addressDetail;
        private BigDecimal actualAmount;
        private BigDecimal deliveryFee;
        private LocalDateTime createTime;
        private LocalDateTime paidAt;
        private LocalDateTime completedAt;
        private List<ItemSnapshot> items;
    }

    @Data
    class ItemSnapshot {
        private String name;
        private Integer quantity;
    }
}
