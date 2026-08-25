package com.example.demo.payment.service;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrdersMapper;
import com.example.demo.payment.dto.PaymentVO;
import com.example.demo.payment.entity.Payment;
import com.example.demo.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simulated payment service with user ownership checks.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrdersMapper ordersMapper;

    private final Snowflake snowflake = IdUtil.getSnowflake(1, 2);

    @Transactional
    public PaymentVO pay(Long userId, Long orderId, String payMethod) {
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getId, orderId)
                        .eq(Orders::getUserId, userId)
        );
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!"pending_payment".equals(order.getStatus())) {
            throw BusinessException.badRequest("当前订单状态不允许支付");
        }

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(order.getActualAmount());
        payment.setPayMethod(payMethod != null ? payMethod : "ALIPAY");
        payment.setTransactionId("TXN" + snowflake.nextId());
        payment.setStatus("SUCCESS");
        payment.setPayTime(LocalDateTime.now());
        paymentMapper.insert(payment);

        order.setStatus("pending_accept");
        order.setPaidAt(LocalDateTime.now());
        ordersMapper.updateById(order);

        return toPaymentVO(payment);
    }

    public List<PaymentVO> getPaymentsByOrderId(Long userId, Long orderId) {
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getId, orderId)
                        .eq(Orders::getUserId, userId)
        );
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }

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

        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getId, payment.getOrderId())
                        .eq(Orders::getUserId, userId)
        );
        if (order == null) {
            throw BusinessException.notFound("支付记录不存在");
        }

        return toPaymentVO(payment);
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
