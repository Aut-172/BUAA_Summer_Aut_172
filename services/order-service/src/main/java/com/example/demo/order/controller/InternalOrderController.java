package com.example.demo.order.controller;

import com.example.demo.common.Result;
import com.example.demo.common.contract.order.MarkPaidRequest;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @GetMapping("/{orderId}")
    public Result<OrderInternalResponse> getOrder(@PathVariable Long orderId) {
        return Result.success(orderService.getOrder(orderId));
    }

    @PostMapping("/{orderId}/mark-paid")
    public Result<OrderInternalResponse> markPaid(@PathVariable Long orderId,
                                                  @Valid @RequestBody MarkPaidRequest request) {
        return Result.success(orderService.markPaid(orderId, request));
    }
}
