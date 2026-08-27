package com.example.demo.order.controller;

import com.example.demo.common.Result;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.dto.OrderVO;
import com.example.demo.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/orders")
    public Result<List<OrderVO>> getOrders(HttpServletRequest request) {
        return Result.success(orderService.getUserOrders(getUserId(request)));
    }

    @GetMapping("/orders/{id}")
    public Result<OrderVO> getOrderDetail(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(orderService.getOrderDetail(getUserId(request), id));
    }

    @PostMapping("/checkout")
    public Result<OrderVO> checkout(HttpServletRequest request, @RequestBody CheckoutRequest body) {
        return Result.success(orderService.checkout(getUserId(request), body));
    }

    @PostMapping("/orders/{id}/cancel")
    public Result<OrderVO> cancelOrder(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(orderService.cancelOrder(getUserId(request), id));
    }

    @PostMapping("/orders/{id}/complete")
    public Result<OrderVO> completeOrder(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(orderService.completeOrder(getUserId(request), id));
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
