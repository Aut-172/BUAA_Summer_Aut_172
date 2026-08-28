package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/merchant-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MerchantServiceApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void merchantRegisterThenLoginReturnsMerchantToken() throws Exception {
        String registerBody = """
                {
                  "username": "newmerchant",
                  "phone": "13900000011",
                  "password": "123456",
                  "nickname": "New Merchant"
                }
                """;

        mockMvc.perform(post("/api/auth/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String loginBody = """
                {
                  "username": "newmerchant",
                  "password": "123456"
                }
                """;

        mockMvc.perform(post("/api/auth/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("merchant"))
                .andExpect(jsonPath("$.data.user.nickname").value("New Merchant"))
                .andExpect(jsonPath("$.data.user.status").value("pending"));
    }

    @Test
    void merchantLoginAcceptsSeedAccountAndReturnsMerchantId() throws Exception {
        String body = """
                {
                  "username": "merchant1",
                  "password": "123456"
                }
                """;

        mockMvc.perform(post("/api/auth/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.user.id").value(20001))
                .andExpect(jsonPath("$.data.user.merchantId").value(20001))
                .andExpect(jsonPath("$.data.user.role").value("merchant"));
    }

    @Test
    void merchantLoginRejectsBadPasswordAndFrozenMerchant() throws Exception {
        mockMvc.perform(post("/api/auth/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"merchant1\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"frozen-merchant\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("商家账号已被冻结，无法登录"));
    }

    @Test
    void merchantRegisterRejectsDuplicatePhone() throws Exception {
        String body = """
                {
                  "username": "merchant-dupe",
                  "phone": "13800138002",
                  "password": "123456"
                }
                """;

        mockMvc.perform(post("/api/auth/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号已注册"));
    }

    @Test
    void merchantListOnlyReturnsActiveMerchants() throws Exception {
        mockMvc.perform(get("/api/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.data[0].name").value("Campus Kitchen"));
    }

    @Test
    void searchMatchesProductNameAndHidesInactiveMerchants() throws Exception {
        mockMvc.perform(get("/api/search").param("keyword", "Hidden Rice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/search").param("keyword", "Braised"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("Campus Kitchen"))
                .andExpect(jsonPath("$.data[0].products[0].name").value("Braised Pork Rice"));
    }

    @Test
    void quoteCalculatesSpecPriceAndStock() throws Exception {
        String body = """
                {
                  "requestId": "order-quote-1",
                  "merchantId": 20001,
                  "items": [
                    {"productId": 30001, "specLabel": "Large", "quantity": 2}
                  ]
                }
                """;

        String response = mockMvc.perform(post("/internal/products/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(25.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("totalAmount").decimalValue()).isEqualByComparingTo("50.00");
    }

    @Test
    void quoteReportsInsufficientStock() throws Exception {
        String body = """
                {
                  "requestId": "order-quote-2",
                  "merchantId": 20001,
                  "items": [
                    {"productId": 30002, "quantity": 1}
                  ]
                }
                """;

        mockMvc.perform(post("/internal/products/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.items[0].stockEnough").value(false))
                .andExpect(jsonPath("$.data.messages[0]").value("库存不足"));
    }
}
