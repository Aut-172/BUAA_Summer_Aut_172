package com.example.demo.payment.controller;

import com.example.demo.common.Result;
import com.example.demo.order.dto.OrderVO;
import com.example.demo.order.service.OrderService;
import com.example.demo.payment.dto.PayRequest;
import com.example.demo.payment.dto.PaymentVO;
import com.example.demo.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 支付控制器
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    /**
     * 支付订单
     * POST /api/orders/{id}/pay
     */
    @PostMapping("/orders/{id}/pay")
    public Result<OrderVO> payOrder(HttpServletRequest request,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) PayRequest body) {
        String payMethod = (body != null) ? body.getPayMethod() : "ALIPAY";
        paymentService.pay(getUserId(request), id, payMethod);
        return Result.success(orderService.getOrderDetail(getUserId(request), id));
    }

    /**
     * 查询订单支付记录
     * GET /api/orders/{id}/payments
     */
    @GetMapping("/orders/{id}/payments")
    public Result<List<PaymentVO>> getOrderPayments(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(paymentService.getPaymentsByOrderId(getUserId(request), id));
    }

    /**
     * 查询单条支付记录
     * GET /api/payments/{id}
     */
    @GetMapping("/payments/{id}")
    public Result<PaymentVO> getPayment(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(paymentService.getPaymentById(getUserId(request), id));
    }
}
