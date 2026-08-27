package com.example.demo.order.controller;

import com.example.demo.common.Result;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/fulfillment")
@RequiredArgsConstructor
public class InternalFulfillmentOrderController {

    private final OrderService orderService;

    @GetMapping("/tasks/available")
    public Result<List<OrderInternalResponse>> getAvailableFulfillmentTasks() {
        return Result.success(orderService.getAvailableFulfillmentTasks());
    }

    @GetMapping("/tasks/assigned")
    public Result<List<OrderInternalResponse>> getAssignedFulfillmentTasks(@RequestParam Long riderId) {
        return Result.success(orderService.getAssignedFulfillmentTasks(riderId));
    }

    @GetMapping("/tasks/completed")
    public Result<List<OrderInternalResponse>> getCompletedFulfillmentTasks(@RequestParam Long riderId) {
        return Result.success(orderService.getCompletedFulfillmentTasks(riderId));
    }

    @PostMapping("/orders/{orderId}/assign-rider")
    public Result<OrderInternalResponse> assignRider(@PathVariable Long orderId,
                                                     @RequestBody RiderTaskCommand command) {
        return Result.success(orderService.assignRider(orderId, command.riderId()));
    }

    @PostMapping("/orders/{orderId}/delivered")
    public Result<OrderInternalResponse> markDelivered(@PathVariable Long orderId,
                                                       @RequestBody RiderTaskCommand command) {
        return Result.success(orderService.markDelivered(orderId, command.riderId()));
    }

    @GetMapping("/orders/{orderId}")
    public Result<OrderInternalResponse> getFulfillmentTask(@PathVariable Long orderId, @RequestParam Long riderId) {
        return Result.success(orderService.getFulfillmentTask(orderId, riderId));
    }

    public record RiderTaskCommand(Long riderId) {
    }
}
