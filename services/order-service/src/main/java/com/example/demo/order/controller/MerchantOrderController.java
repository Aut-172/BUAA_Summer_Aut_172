package com.example.demo.order.controller;

import com.example.demo.common.Result;
import com.example.demo.order.dto.MerchantOrderUpdateRequest;
import com.example.demo.order.dto.OrderVO;
import com.example.demo.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/merchant/orders")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final OrderService orderService;

    @GetMapping
    public Result<List<OrderVO>> getMerchantOrders(HttpServletRequest request) {
        return Result.success(orderService.getMerchantOrders(getMerchantId(request)));
    }

    @PutMapping("/{id}")
    public Result<OrderVO> updateMerchantOrder(HttpServletRequest request,
                                               @PathVariable Long id,
                                               @RequestBody MerchantOrderUpdateRequest body) {
        return Result.success(orderService.updateMerchantOrder(getMerchantId(request), id, body));
    }

    private Long getMerchantId(HttpServletRequest request) {
        return (Long) request.getAttribute("merchantId");
    }
}
