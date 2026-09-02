package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.JwtUtil;
import com.example.demo.common.Result;
import com.example.demo.common.contract.merchant.MerchantDashboardStats;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.entity.MerchantStockChangeRecord;
import com.example.demo.merchant.entity.ProductSpec;
import com.example.demo.merchant.client.OrderSummaryClient;
import com.example.demo.merchant.mapper.MerchantStockChangeMapper;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.merchant.mapper.ProductSpecMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/merchant-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class MerchantServiceApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSpecMapper productSpecMapper;

    @Autowired
    private MerchantStockChangeMapper merchantStockChangeMapper;

    @MockBean
    private OrderSummaryClient orderSummaryClient;

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
    void merchantSelfDashboardProfileAndProductsWorkWithMerchantToken() throws Exception {
        MerchantDashboardStats stats = new MerchantDashboardStats();
        stats.setTodayOrders(3);
        stats.setTodayRevenue(new java.math.BigDecimal("120.00"));
        stats.setPendingOrders(1);
        when(orderSummaryClient.getMerchantDashboardResult(20001L)).thenReturn(Result.success(stats));

        String token = jwtUtil.generateToken(20001L, "merchant", "merchant1");

        mockMvc.perform(get("/api/merchant/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.merchant.todayOrders").value(3))
                .andExpect(jsonPath("$.data.merchant.todayRevenue").value(120.00))
                .andExpect(jsonPath("$.data.merchant.pendingOrders").value(1))
                .andExpect(jsonPath("$.data.degraded").value(false));

        mockMvc.perform(get("/api/merchant/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(20001))
                .andExpect(jsonPath("$.data.name").value("Campus Kitchen"));

        mockMvc.perform(get("/api/merchant/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].merchantId").value(20001))
                .andExpect(jsonPath("$.data[0].name").value("Braised Pork Rice"));

        mockMvc.perform(get("/api/merchant/products/30001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(30001))
                .andExpect(jsonPath("$.data.name").value("Braised Pork Rice"));
    }

    @Test
    void merchantProductCreateRejectsPriceAboveLimit() throws Exception {
        String token = jwtUtil.generateToken(20001L, "merchant", "merchant1");
        String body = """
                {
                  "categoryId": 1,
                  "name": "Too Expensive Rice",
                  "price": 100000.00,
                  "stock": 30,
                  "status": "active"
                }
                """;

        mockMvc.perform(post("/api/merchant/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("商品价格不能超过 99999.99 元"));
    }

    @Test
    void merchantProductCreateRejectsStockAboveLimit() throws Exception {
        String token = jwtUtil.generateToken(20001L, "merchant", "merchant1");
        String body = """
                {
                  "categoryId": 1,
                  "name": "Too Many Rice",
                  "price": 16.00,
                  "stock": 1000000,
                  "status": "active"
                }
                """;

        mockMvc.perform(post("/api/merchant/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("商品库存不能超过 999999"));
    }

    @Test
    void merchantDashboardUsesFallbackWhenOrderServiceFails() throws Exception {
        doThrow(new RuntimeException("order service unavailable"))
                .when(orderSummaryClient).getMerchantDashboardResult(20001L);

        String token = jwtUtil.generateToken(20001L, "merchant", "merchant1");

        mockMvc.perform(get("/api/merchant/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.degraded").value(true))
                .andExpect(jsonPath("$.data.degradedDependency").value("order-service"))
                .andExpect(jsonPath("$.data.degradationMessage").value("订单服务暂不可用，已返回临时看板数据，请稍后刷新。"))
                .andExpect(jsonPath("$.data.merchant.todayOrders").value(0))
                .andExpect(jsonPath("$.data.merchant.todayRevenue").value(0))
                .andExpect(jsonPath("$.data.merchant.pendingOrders").value(0));
    }

    @Test
    void merchantDashboardUsesFallbackWhenOrderServiceCircuitBreaks() throws Exception {
        doThrow(new BusinessException(503, "依赖服务暂不可用，请稍后重试"))
                .when(orderSummaryClient).getMerchantDashboardResult(20001L);

        String token = jwtUtil.generateToken(20001L, "merchant", "merchant1");

        mockMvc.perform(get("/api/merchant/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.degraded").value(true))
                .andExpect(jsonPath("$.data.degradedDependency").value("order-service"))
                .andExpect(jsonPath("$.data.fallbackReason").value("remote code 503"));
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

    @Test
    void reserveAndReleaseRestoreStock() throws Exception {
        String body = """
                {
                  "requestId": "stock-change-1",
                  "merchantId": 20001,
                  "items": [
                    {"productId": 30001, "specLabel": "Large", "quantity": 2}
                  ]
                }
                """;

        mockMvc.perform(post("/internal/products/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.status").value("reserved"))
                .andExpect(jsonPath("$.data.items[0].remainingStock").value(48));

        ProductSpec reservedSpec = productSpecMapper.selectById(1L);
        Product product = productMapper.selectById(30001L);
        assertThat(reservedSpec.getStock()).isEqualTo(48);
        assertThat(product.getStock()).isEqualTo(100);

        mockMvc.perform(post("/internal/products/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.status").value("released"))
                .andExpect(jsonPath("$.data.items[0].remainingStock").value(50));

        ProductSpec restoredSpec = productSpecMapper.selectById(1L);
        assertThat(restoredSpec.getStock()).isEqualTo(50);
    }

    @Test
    void reserveRejectsInsufficientStock() throws Exception {
        String body = """
                {
                  "requestId": "stock-change-2",
                  "merchantId": 20001,
                  "items": [
                    {"productId": 30002, "quantity": 1}
                  ]
                }
                """;

        mockMvc.perform(post("/internal/products/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("库存不足"));

        assertThat(productMapper.selectById(30002L).getStock()).isEqualTo(0);
        MerchantStockChangeRecord record = merchantStockChangeMapper.selectList(null).stream()
                .filter(item -> "stock-change-2".equals(item.getRequestId()))
                .findFirst()
                .orElseThrow();
        assertThat(record.getStatus()).isEqualTo("failed");
        assertThat(record.getMessage()).contains("库存不足");

        mockMvc.perform(get("/internal/products/changes/stock-change-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.message").value("库存不足"));
    }
}
