package com.example.demo;

import com.example.demo.common.BusinessException;
import com.example.demo.common.JwtUtil;
import com.example.demo.user.client.MerchantCatalogClient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/user-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserServiceApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MerchantCatalogClient merchantCatalogClient;

    @BeforeEach
    void setUp() {
        when(merchantCatalogClient.getMerchant(anyLong())).thenAnswer(invocation -> activeMerchant(invocation.getArgument(0)));
        when(merchantCatalogClient.getProduct(anyLong())).thenAnswer(invocation -> product(invocation.getArgument(0), 20001L));
        when(merchantCatalogClient.getProductQuote(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> quote(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    }

    @Test
    void consumerRegisterThenLoginReturnsConsumerToken() throws Exception {
        String registerBody = """
                {
                  "username": "newuser",
                  "phone": "13900000001",
                  "password": "123456",
                  "nickname": "New User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String loginBody = """
                {
                  "username": "newuser",
                  "password": "123456"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("consumer"))
                .andExpect(jsonPath("$.data.user.nickname").value("New User"));
    }

    @Test
    void consumerRegisterRejectsDuplicatePhone() throws Exception {
        String body = """
                {
                  "username": "dupe",
                  "phone": "13800138001",
                  "password": "123456"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号已注册"));
    }

    @Test
    void adminLoginReturnsAdminToken() throws Exception {
        String body = """
                {
                  "username": "admin",
                  "password": "admin123"
                }
                """;

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("admin"));
    }

    @Test
    void adminCanListFreezeAndUnfreezeConsumers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data[0].role").value("consumer"));

        mockMvc.perform(delete("/api/admin/users/10001").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("frozen"));

        mockMvc.perform(put("/api/admin/users/10001/unfreeze").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void consumerCannotUseAdminUserApi() throws Exception {
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问管理员接口"));
    }

    @Test
    void frozenConsumerTokenCannotAccessUserApis() throws Exception {
        mockMvc.perform(delete("/api/admin/users/10001").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("frozen"));

        mockMvc.perform(get("/api/user/profile").header(HttpHeaders.AUTHORIZATION, consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("用户账号已被冻结，无法使用该功能"));

        mockMvc.perform(get("/api/user/cart").header(HttpHeaders.AUTHORIZATION, consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("用户账号已被冻结，无法使用该功能"));
    }

    @Test
    void getProfileReturnsCurrentConsumer() throws Exception {
        mockMvc.perform(get("/api/user/profile").header(HttpHeaders.AUTHORIZATION, consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10001))
                .andExpect(jsonPath("$.data.username").value("demo"))
                .andExpect(jsonPath("$.data.role").value("consumer"));
    }

    @Test
    void updateProfileRejectsDuplicatePhone() throws Exception {
        mockMvc.perform(put("/api/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138099\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号已被其他用户使用"));
    }

    @Test
    void addingDefaultAddressClearsPreviousDefaultAddress() throws Exception {
        String body = """
                {
                  "name": "New Receiver",
                  "phone": "13900000000",
                  "detail": "New default address",
                  "isDefault": true
                }
                """;

        String created = mockMvc.perform(post("/api/user/addresses")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long newAddressId = objectMapper.readTree(created).path("data").path("id").asLong();
        String response = mockMvc.perform(get("/api/user/addresses").header(HttpHeaders.AUTHORIZATION, consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode addresses = objectMapper.readTree(response).path("data");
        JsonNode oldDefault = findById(addresses, 50001L);
        JsonNode newDefault = findById(addresses, newAddressId);
        assertThat(oldDefault.path("isDefault").asBoolean()).isFalse();
        assertThat(newDefault.path("isDefault").asBoolean()).isTrue();
    }

    @Test
    void addCartUsesMerchantServiceQuoteAndCalculatesSubtotal() throws Exception {
        String body = """
                {
                  "merchantId": 20001,
                  "productId": 30003,
                  "quantity": 2,
                  "specLabel": "Large"
                }
                """;

        mockMvc.perform(post("/api/user/cart")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.merchantName").value("Campus Kitchen"))
                .andExpect(jsonPath("$.data.productId").value(30003))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(50.00));
    }

    @Test
    void addExistingCartItemIncrementsQuantityWithoutNewQuote() throws Exception {
        String body = """
                {
                  "merchantId": 20001,
                  "productId": 30001,
                  "quantity": 2
                }
                """;

        mockMvc.perform(post("/api/user/cart")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(51001))
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andExpect(jsonPath("$.data.subtotal").value(66.00));
    }

    @Test
    void settingCartQuantityToZeroDeletesItem() throws Exception {
        mockMvc.perform(put("/api/user/cart/51001")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken())
                        .param("quantity", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/user/cart").header(HttpHeaders.AUTHORIZATION, consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void favoriteRejectsInactiveMerchantFromMerchantService() throws Exception {
        MerchantCatalogClient.MerchantSnapshot inactive = activeMerchant(20002L);
        inactive.setStatus("closed");
        when(merchantCatalogClient.getMerchant(20002L)).thenReturn(inactive);

        mockMvc.perform(post("/api/user/favorites/20002").header(HttpHeaders.AUTHORIZATION, consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只能收藏正常营业商家"));
    }

    @Test
    void cartReturnsServiceUnavailableWhenMerchantServiceFails() throws Exception {
        when(merchantCatalogClient.getMerchant(20001L)).thenThrow(new BusinessException(503, "商家服务暂不可用"));

        mockMvc.perform(get("/api/user/cart").header(HttpHeaders.AUTHORIZATION, consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("商家服务暂不可用"));
    }

    @Test
    void internalClearCartByMerchantOnlyDeletesOwnedMerchantItems() throws Exception {
        mockMvc.perform(delete("/internal/users/10001/cart").param("merchantId", "20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/user/cart").header(HttpHeaders.AUTHORIZATION, consumerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private String consumerToken() {
        return "Bearer " + jwtUtil.generateToken(10001L, "consumer", "demo");
    }

    private String adminToken() {
        return "Bearer " + jwtUtil.generateToken(1L, "admin", "admin");
    }

    private JsonNode findById(JsonNode array, long id) {
        for (JsonNode item : array) {
            if (item.path("id").asLong() == id) {
                return item;
            }
        }
        throw new AssertionError("Expected id " + id + " in " + array);
    }

    private MerchantCatalogClient.MerchantSnapshot activeMerchant(Long merchantId) {
        MerchantCatalogClient.MerchantSnapshot merchant = new MerchantCatalogClient.MerchantSnapshot();
        merchant.setId(merchantId);
        merchant.setName("Campus Kitchen");
        merchant.setCategory("Rice");
        merchant.setDescription("Campus food");
        merchant.setAvatar("/merchant.png");
        merchant.setTags("rice,fast");
        merchant.setStatus("active");
        merchant.setRating(new BigDecimal("4.8"));
        merchant.setMonthlySales(120);
        merchant.setMinDeliveryFee(new BigDecimal("20.00"));
        merchant.setDeliveryFee(new BigDecimal("5.00"));
        return merchant;
    }

    private MerchantCatalogClient.ProductSnapshot product(Long productId, Long merchantId) {
        MerchantCatalogClient.ProductSnapshot product = new MerchantCatalogClient.ProductSnapshot();
        product.setId(productId);
        product.setMerchantId(merchantId);
        product.setName(productId == 30003L ? "Roast Chicken Rice" : "Braised Pork Rice");
        product.setImage("/product.png");
        product.setPrice(new BigDecimal("25.00"));
        product.setStatus("active");
        return product;
    }

    private MerchantCatalogClient.ProductQuote quote(Long merchantId, Long productId, String specLabel) {
        MerchantCatalogClient.ProductQuote quote = new MerchantCatalogClient.ProductQuote();
        quote.setMerchantId(merchantId);
        quote.setProductId(productId);
        quote.setName(productId == 30003L ? "Roast Chicken Rice" : "Braised Pork Rice");
        quote.setImage("/product.png");
        quote.setPrice("Large".equals(specLabel) ? new BigDecimal("25.00") : new BigDecimal("22.00"));
        quote.setSpecLabel(specLabel);
        quote.setStatus("active");
        quote.setAvailable(true);
        return quote;
    }
}
