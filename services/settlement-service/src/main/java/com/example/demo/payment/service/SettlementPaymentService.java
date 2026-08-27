package com.example.demo.payment.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.order.MarkPaidRequest;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.common.contract.settlement.MockPayRequest;
import com.example.demo.payment.client.OrderClient;
import com.example.demo.payment.dto.PaymentVO;
import com.example.demo.payment.entity.Payment;
import com.example.demo.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementPaymentService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String ORDER_PENDING_PAYMENT = "pending_payment";

    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;

    @Transactional
    public OrderInternalResponse pay(Long userId, Long orderId, String payMethod) {
        OrderInternalResponse order = requireUserOrder(userId, orderId);
        if (!ORDER_PENDING_PAYMENT.equals(order.getStatus())) {
            throw BusinessException.badRequest("当前订单状态不允许支付");
        }

        MockPayRequest request = new MockPayRequest();
        request.setOrderId(orderId);
        request.setAmount(order.getActualAmount());
        request.setPayMethod(payMethod == null || payMethod.isBlank() ? "ALIPAY" : payMethod.trim());
        request.setTransactionId("TXN" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        return mockPaySuccess(request);
    }

    public List<PaymentVO> getPaymentsByOrderId(Long userId, Long orderId) {
        requireUserOrder(userId, orderId);
        return paymentMapper.selectList(
                        new LambdaQueryWrapper<Payment>()
                                .eq(Payment::getOrderId, orderId)
                                .orderByDesc(Payment::getCreateTime)
                ).stream()
                .map(this::toPaymentVO)
                .collect(Collectors.toList());
    }

    public PaymentVO getPaymentById(Long userId, Long paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw BusinessException.notFound("支付记录不存在");
        }
        requireUserOrder(userId, payment.getOrderId());
        return toPaymentVO(payment);
    }

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

    private OrderInternalResponse requireUserOrder(Long userId, Long orderId) {
        Result<OrderInternalResponse> result = orderClient.getOrder(orderId);
        if (result == null) {
            throw new BusinessException(503, "订单服务暂不可用");
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        OrderInternalResponse order = result.getData();
        if (order == null || !userId.equals(order.getUserId())) {
            throw BusinessException.notFound("订单不存在");
        }
        return order;
    }

    private PaymentVO toPaymentVO(Payment payment) {
        return PaymentVO.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .payMethod(payment.getPayMethod())
                .transactionId(payment.getTransactionId())
                .status(payment.getStatus())
                .payTime(payment.getPayTime())
                .build();
    }
}
