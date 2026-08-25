package com.example.demo.order.controller;

import com.example.demo.common.Result;
import com.example.demo.order.dto.MerchantOrderUpdateRequest;
import com.example.demo.order.dto.OrderVO;
import com.example.demo.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家端订单控制器
 */
@RestController
@RequestMapping("/api/merchant/orders")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final OrderService orderService;

    private Long getMerchantId(HttpServletRequest request) {
        return (Long) request.getAttribute("merchantId");
    }

    /**
     * 获取商家订单列表
     * GET /api/merchant/orders?merchantId=xxx
     */
    @GetMapping
    public Result<List<OrderVO>> getMerchantOrders(HttpServletRequest request) {
        return Result.success(orderService.getMerchantOrders(getMerchantId(request)));
    }

    /**
     * 更新商家订单状态
     * PUT /api/merchant/orders/{id}
     */
    @PutMapping("/{id}")
    public Result<OrderVO> updateMerchantOrder(HttpServletRequest request,
                                                @PathVariable Long id,
                                                @RequestBody MerchantOrderUpdateRequest body) {
        return Result.success(orderService.updateMerchantOrder(getMerchantId(request), id, body));
    }
}
