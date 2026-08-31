package com.example.demo.order.controller;

import com.example.demo.common.Result;
import com.example.demo.common.contract.merchant.MerchantDashboardStats;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @GetMapping("/{orderId}")
    public Result<OrderInternalResponse> getOrder(@PathVariable Long orderId) {
        return Result.success(orderService.getOrder(orderId));
    }

    @GetMapping("/merchant-dashboard")
    public Result<MerchantDashboardStats> getMerchantDashboard(@RequestParam Long merchantId) {
        return Result.success(orderService.getMerchantDashboard(merchantId));
    }

    @PostMapping("/{orderId}/mark-paid")
    public Result<OrderInternalResponse> markPaid(@PathVariable Long orderId,
                                                  @Valid @RequestBody MarkPaidRequest request) {
        return Result.success(orderService.markPaid(orderId, request));
    }

    @GetMapping("/{orderId}/participants")
    public Result<OrderInternalResponse> getParticipantOrder(@PathVariable Long orderId,
                                                            @RequestParam Long participantId,
                                                            @RequestParam String participantType) {
        return Result.success(orderService.getParticipantOrder(orderId, participantId, participantType));
    }

    @PostMapping("/{orderId}/reviewed-items")
    public Result<Void> markReviewedItems(@PathVariable Long orderId,
                                          @RequestBody ReviewedItemsCommand command) {
        orderService.markReviewedItems(orderId, command == null ? List.of() : command.productIds());
        return Result.success();
    }

    public record ReviewedItemsCommand(List<Long> productIds) {
    }
}
