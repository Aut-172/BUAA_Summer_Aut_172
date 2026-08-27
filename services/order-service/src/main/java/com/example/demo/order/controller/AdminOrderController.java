package com.example.demo.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public PageResult<Orders> listOrders(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String type) {
        IPage<Orders> result = orderService.listOrders(page, pageSize, keyword, status, type);
        return PageResult.of(result);
    }

    @GetMapping("/{id}")
    public Result<Orders> getOrderDetail(@PathVariable Long id) {
        return Result.success(orderService.getAdminOrderDetail(id));
    }
}
