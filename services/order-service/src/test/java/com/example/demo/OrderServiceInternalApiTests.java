package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderServiceInternalApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getInternalOrderReturnsSnapshotAndItems() throws Exception {
        mockMvc.perform(get("/internal/orders/70002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("pending_payment"))
                .andExpect(jsonPath("$.data.items[0].name").value("Braised Pork Rice"));
    }

    @Test
    void markPaidMovesPendingPaymentOrderToPendingAccept() throws Exception {
        String body = """
                {
                  "transactionId": "pay-70001",
                  "payMethod": "mock",
                  "amount": 27.00
                }
                """;

        mockMvc.perform(post("/internal/orders/70001/mark-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("pending_accept"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());
    }

    @Test
    void markPaidRejectsAmountMismatch() throws Exception {
        String body = """
                {
                  "transactionId": "pay-70002",
                  "payMethod": "mock",
                  "amount": 1.00
                }
                """;

        mockMvc.perform(post("/internal/orders/70002/mark-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("支付金额与订单金额不一致"));
    }
}
