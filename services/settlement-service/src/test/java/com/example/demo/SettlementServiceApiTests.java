package com.example.demo;

import com.example.demo.common.Result;
import com.example.demo.common.JwtUtil;
import com.example.demo.common.contract.order.MarkPaidRequest;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.coupon.entity.Coupon;
import com.example.demo.coupon.mapper.CouponMapper;
import com.example.demo.coupon.mapper.UserCouponMapper;
import com.example.demo.payment.client.OrderClient;
import com.example.demo.payment.entity.Payment;
import com.example.demo.payment.mapper.PaymentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/settlement-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SettlementServiceApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @MockitoBean
    private OrderClient orderClient;

    @BeforeEach
    void setUpClaimableCoupon() {
        Coupon coupon = new Coupon();
        coupon.setId(60003L);
        coupon.setName("Campus 5 Off");
        coupon.setDiscount(new BigDecimal("5.00"));
        coupon.setThreshold(new BigDecimal("20.00"));
        coupon.setStartTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        coupon.setEndTime(LocalDateTime.of(2027, 12, 31, 23, 59, 59));
        coupon.setTotalCount(100);
        coupon.setClaimedCount(0);
        coupon.setLimitPerUser(1);
        coupon.setStatus("released");
        couponMapper.insert(coupon);
    }

    @Test
    void couponApisExposeUserCouponsAvailableCouponsAndClaimFlow() throws Exception {
        String userToken = consumerToken();

        String userCouponsResponse = mockMvc.perform(get("/api/coupons")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode userCoupons = objectMapper.readTree(userCouponsResponse).path("data");
        assertThat(userCoupons.size()).isEqualTo(3);
        assertThat(userCoupons).anyMatch(node -> node.path("id").asLong() == 60001L
                && "unused".equals(node.path("status").asText()));
        assertThat(userCoupons).noneMatch(node -> node.path("id").asLong() == 60003L);

        String availableResponse = mockMvc.perform(get("/api/coupons/available")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode availableCoupons = objectMapper.readTree(availableResponse).path("data");
        assertThat(availableCoupons).anyMatch(node -> node.path("id").asLong() == 60003L);

        mockMvc.perform(post("/api/coupons/60003/claim")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(60003L))
                .andExpect(jsonPath("$.data.status").value("unused"));

        assertThat(userCouponMapper.selectCount(null)).isEqualTo(4);
        assertThat(userCouponMapper.selectList(null)).anyMatch(userCoupon ->
                userCoupon.getUserId().equals(10001L)
                        && userCoupon.getCouponId().equals(60003L)
                        && "unused".equals(userCoupon.getStatus()));
        assertThat(couponMapper.selectById(60003L).getClaimedCount()).isEqualTo(1);
    }

    @Test
    void paymentApisCreatePaymentAndExposePaymentQueries() throws Exception {
        String userToken = consumerToken();

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

        mockMvc.perform(post("/api/orders/70001/pay")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payMethod\":\"WECHAT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("pending_accept"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());

        Payment payment = paymentMapper.selectList(null).stream()
                .filter(item -> item.getOrderId().equals(70001L))
                .findFirst()
                .orElseThrow();
        assertThat(payment.getStatus()).isEqualTo("SUCCESS");
        assertThat(payment.getTransactionId()).isNotBlank();

        mockMvc.perform(get("/api/orders/70001/payments")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderId").value(70001L))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));

        mockMvc.perform(get("/api/payments/" + payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(payment.getId()))
                .andExpect(jsonPath("$.data.orderId").value(70001L))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void healthEndpointExposesVersionAndDatabaseStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.application").value("settlement-service"))
                .andExpect(jsonPath("$.data.version").isNotEmpty())
                .andExpect(jsonPath("$.data.databaseStatus").value("UP"));
    }

    private String consumerToken() {
        return "Bearer " + jwtUtil.generateToken(10001L, "consumer", "demo");
    }

}
