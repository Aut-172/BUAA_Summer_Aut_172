package com.example.demo.payment.controller;

import com.example.demo.common.Result;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.payment.dto.PayRequest;
import com.example.demo.payment.dto.PaymentVO;
import com.example.demo.payment.service.SettlementPaymentService;
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
public class PaymentController {

    private final SettlementPaymentService settlementPaymentService;

    @PostMapping("/orders/{id}/pay")
    public Result<OrderInternalResponse> payOrder(HttpServletRequest request,
                                                  @PathVariable Long id,
                                                  @RequestBody(required = false) PayRequest body) {
        String payMethod = body == null ? "ALIPAY" : body.getPayMethod();
        return Result.success(settlementPaymentService.pay(getUserId(request), id, payMethod));
    }

    @GetMapping("/orders/{id}/payments")
    public Result<List<PaymentVO>> getOrderPayments(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(settlementPaymentService.getPaymentsByOrderId(getUserId(request), id));
    }

    @GetMapping("/payments/{id}")
    public Result<PaymentVO> getPayment(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(settlementPaymentService.getPaymentById(getUserId(request), id));
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
