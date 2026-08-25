package com.example.demo.order.controller;

import com.example.demo.common.Result;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.dto.OrderVO;
import com.example.demo.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端订单控制器
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    /**
     * 获取用户订单列表
     * GET /api/orders
     */
    @GetMapping("/orders")
    public Result<List<OrderVO>> getOrders(HttpServletRequest request) {
        return Result.success(orderService.getUserOrders(getUserId(request)));
    }

    /**
     * 获取订单详情
     * GET /api/orders/{id}
     */
    @GetMapping("/orders/{id}")
    public Result<OrderVO> getOrderDetail(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(orderService.getOrderDetail(getUserId(request), id));
    }

    /**
     * 结算下单
     * POST /api/checkout
     */
    @PostMapping("/checkout")
    public Result<OrderVO> checkout(HttpServletRequest request,
                                    @RequestBody CheckoutRequest body) {
        return Result.success(orderService.checkout(getUserId(request), body));
    }

    /**
     * 取消订单
     * POST /api/orders/{id}/cancel
     */
    @PostMapping("/orders/{id}/cancel")
    public Result<OrderVO> cancelOrder(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(orderService.cancelOrder(getUserId(request), id));
    }

    /**
     * 确认收货
     * POST /api/orders/{id}/complete
     */
    @PostMapping("/orders/{id}/complete")
    public Result<OrderVO> completeOrder(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(orderService.completeOrder(getUserId(request), id));
    }
}
