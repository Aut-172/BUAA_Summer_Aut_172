package com.example.demo.delivery.controller;

import com.example.demo.common.Result;
import com.example.demo.delivery.dto.DeliveryVO;
import com.example.demo.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配送追踪控制器.
 */
@Tag(name = "配送追踪")
@RestController
@RequestMapping("/api")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @Operation(summary = "获取配送追踪信息")
    @GetMapping("/delivery/{id}")
    public Result<DeliveryVO> getDeliveryInfo(HttpServletRequest request, @PathVariable Long id) {
        DeliveryVO delivery = deliveryService.getDeliveryInfo(getUserId(request), id);
        if (delivery == null) {
            return Result.notFound("订单不存在");
        }
        return Result.success(delivery);
    }
}
