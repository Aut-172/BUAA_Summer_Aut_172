package com.example.demo.payment.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.order.MarkPaidRequest;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.common.contract.settlement.MockPayRequest;
import com.example.demo.payment.client.OrderClient;
import com.example.demo.payment.entity.Payment;
import com.example.demo.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementPaymentService {

    private static final String STATUS_SUCCESS = "SUCCESS";

    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;

    @Transactional
    public OrderInternalResponse mockPaySuccess(MockPayRequest request) {
        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setAmount(request.getAmount());
        payment.setPayMethod(request.getPayMethod());
        payment.setTransactionId(request.getTransactionId() == null
                ? UUID.randomUUID().toString()
                : request.getTransactionId());
        payment.setStatus(STATUS_SUCCESS);
        payment.setPayTime(LocalDateTime.now());
        paymentMapper.insert(payment);

        MarkPaidRequest markPaidRequest = new MarkPaidRequest();
        markPaidRequest.setAmount(request.getAmount());
        markPaidRequest.setPayMethod(request.getPayMethod());
        markPaidRequest.setTransactionId(payment.getTransactionId());
        markPaidRequest.setPaidAt(payment.getPayTime());

        Result<OrderInternalResponse> result = orderClient.markPaid(request.getOrderId(), markPaidRequest);
        if (result == null) {
            throw BusinessException.badRequest("订单服务无响应，支付流水已记录，需补偿重试");
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
