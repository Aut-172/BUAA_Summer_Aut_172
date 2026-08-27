package com.example.demo.payment.controller;

import com.example.demo.common.Result;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.common.contract.settlement.MockPayRequest;
import com.example.demo.payment.service.SettlementPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final SettlementPaymentService settlementPaymentService;

    @PostMapping("/mock-success")
    public Result<OrderInternalResponse> mockSuccess(@Valid @RequestBody MockPayRequest request) {
        return Result.success(settlementPaymentService.mockPaySuccess(request));
    }
}
