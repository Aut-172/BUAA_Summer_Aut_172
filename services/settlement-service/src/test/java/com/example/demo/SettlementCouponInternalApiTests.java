package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementCouponInternalApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void lockCouponMovesUnusedCouponToLocked() throws Exception {
        String body = """
                {
                  "requestId": "coupon-lock-1",
                  "userId": 10001,
                  "couponId": 60001,
                  "orderId": 70001,
                  "orderAmount": 50.00
                }
                """;

        mockMvc.perform(post("/internal/coupon-locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.discount").value(10.00))
                .andExpect(jsonPath("$.data.status").value("locked"));
    }

    @Test
    void lockCouponRejectsThresholdMismatch() throws Exception {
        String body = """
                {
                  "requestId": "coupon-lock-2",
                  "userId": 10001,
                  "couponId": 60002,
                  "orderId": 70002,
                  "orderAmount": 50.00
                }
                """;

        mockMvc.perform(post("/internal/coupon-locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("订单金额未达到优惠券门槛"));
    }

    @Test
    void releaseCouponMovesLockedCouponBackToUnused() throws Exception {
        mockMvc.perform(post("/internal/coupon-locks/70003/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.message").value("释放成功"));
    }
}
