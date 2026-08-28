package com.example.demo;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.common.contract.order.MarkPaidRequest;
import com.example.demo.payment.client.OrderClient;
import com.example.demo.payment.entity.Payment;
import com.example.demo.payment.mapper.PaymentMapper;
import com.example.demo.payment.service.SettlementPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/settlement-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SettlementPaymentServiceTests {

    @Autowired
    private SettlementPaymentService settlementPaymentService;

    @Autowired
    private PaymentMapper paymentMapper;

    @org.springframework.boot.test.mock.mockito.MockBean
    private OrderClient orderClient;

    @Test
    void mockPaySuccessRequiresOrderToMoveToPendingAccept() {
        OrderInternalResponse order = new OrderInternalResponse();
        order.setId(70001L);
        order.setUserId(10001L);
        order.setStatus("pending_payment");
        order.setActualAmount(new BigDecimal("27.00"));
        when(orderClient.getOrder(70001L)).thenReturn(Result.success(order));

        OrderInternalResponse updated = new OrderInternalResponse();
        updated.setId(70001L);
        updated.setUserId(10001L);
        updated.setStatus("pending_payment");
        updated.setActualAmount(new BigDecimal("27.00"));
        updated.setPaidAt(LocalDateTime.now());
        when(orderClient.markPaid(any(Long.class), any(MarkPaidRequest.class))).thenReturn(Result.success(updated));

        long beforePayments = paymentMapper.selectCount(null);

        assertThatThrownBy(() -> settlementPaymentService.pay(10001L, 70001L, "ALIPAY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单支付确认未生效");

        assertThat(paymentMapper.selectCount(null)).isEqualTo(beforePayments);
    }

    @Test
    void mockPaySuccessRecordsPaymentWhenOrderIsConfirmed() {
        OrderInternalResponse order = new OrderInternalResponse();
        order.setId(70001L);
        order.setUserId(10001L);
        order.setStatus("pending_payment");
        order.setActualAmount(new BigDecimal("27.00"));
        when(orderClient.getOrder(70001L)).thenReturn(Result.success(order));

        OrderInternalResponse updated = new OrderInternalResponse();
        updated.setId(70001L);
        updated.setUserId(10001L);
        updated.setStatus("pending_accept");
        updated.setActualAmount(new BigDecimal("27.00"));
        updated.setPaidAt(LocalDateTime.now());
        when(orderClient.markPaid(any(Long.class), any(MarkPaidRequest.class))).thenReturn(Result.success(updated));

        OrderInternalResponse response = settlementPaymentService.pay(10001L, 70001L, "ALIPAY");

        assertThat(response.getStatus()).isEqualTo("pending_accept");
        assertThat(response.getPaidAt()).isNotNull();

        Payment payment = paymentMapper.selectList(null).stream()
                .filter(item -> item.getOrderId().equals(70001L))
                .findFirst()
                .orElseThrow();
        assertThat(payment.getStatus()).isEqualTo("SUCCESS");
        assertThat(payment.getPayTime()).isNotNull();
    }
}
