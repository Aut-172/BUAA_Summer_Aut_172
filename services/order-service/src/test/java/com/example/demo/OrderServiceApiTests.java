package com.example.demo;

import com.example.demo.common.Result;
import com.example.demo.common.JwtUtil;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import com.example.demo.common.contract.merchant.StockChangeResponse;
import com.example.demo.common.contract.user.AddressSnapshot;
import com.example.demo.order.client.MerchantCatalogClient;
import com.example.demo.order.client.MerchantProductClient;
import com.example.demo.order.client.SettlementCouponClient;
import com.example.demo.order.client.UserClient;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.dto.CheckoutRequest.CheckoutItem;
import com.example.demo.order.dto.MerchantOrderUpdateRequest;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrderItemMapper;
import com.example.demo.order.mapper.OrdersMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/order-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderServiceApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @MockitoBean
    private MerchantCatalogClient merchantCatalogClient;

    @MockitoBean
    private MerchantProductClient merchantProductClient;

    @MockitoBean
    private SettlementCouponClient settlementCouponClient;

    @MockitoBean
    private UserClient userClient;

    @BeforeEach
    void setUpMerchantSnapshot() {
        lenient().when(merchantCatalogClient.getMerchant(20001L)).thenReturn(activeMerchant());
    }

    @Test
    void consumerPublicApisExposeOrdersCheckoutCancelAndComplete() throws Exception {
        String consumerToken = consumerToken();

        JsonNode orders = readData(mockMvc.perform(get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());
        assertThat(orders.size()).isEqualTo(2);
        assertThat(orders).anyMatch(node -> node.path("orderNo").asText().equals("ORD202608270000000001"));

        JsonNode orderDetail = readData(mockMvc.perform(get("/api/orders/70001")
                .header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());
        assertThat(orderDetail.path("merchant").asText()).isEqualTo("Campus Kitchen");
        assertThat(orderDetail.path("items").size()).isEqualTo(1);

        ProductQuoteResponse quoteResponse = new ProductQuoteResponse();
        quoteResponse.setMerchantId(20001L);
        quoteResponse.setAvailable(true);
        quoteResponse.setTotalAmount(new BigDecimal("22.00"));
        ProductQuoteResponse.Item quoteItem = new ProductQuoteResponse.Item();
        quoteItem.setProductId(30001L);
        quoteItem.setMerchantId(20001L);
        quoteItem.setName("Braised Pork Rice");
        quoteItem.setImage("/oss/life-assistant/demo/products/braised-pork-rice.png");
        quoteItem.setUnitPrice(new BigDecimal("22.00"));
        quoteItem.setQuantity(1);
        quoteItem.setStock(100);
        quoteItem.setSubtotal(new BigDecimal("22.00"));
        quoteItem.setActive(true);
        quoteItem.setStockEnough(true);
        quoteResponse.getItems().add(quoteItem);
        when(merchantProductClient.quote(any())).thenReturn(Result.success(quoteResponse));

        StockChangeResponse reserveResponse = new StockChangeResponse();
        reserveResponse.setSuccess(true);
        reserveResponse.setStatus("reserved");
        reserveResponse.setMessage("库存预留成功");
        when(merchantProductClient.reserve(any())).thenReturn(reserveResponse);

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setMerchantId(20001L);
        checkoutRequest.setAddress("No. 3 Dorm");
        CheckoutItem checkoutItem = new CheckoutItem();
        checkoutItem.setProductId(30001L);
        checkoutItem.setQuantity(1);
        checkoutRequest.setItems(List.of(checkoutItem));

        int beforeOrders = ordersMapper.selectCount(null).intValue();
        int beforeItems = orderItemMapper.selectCount(null).intValue();

                mockMvc.perform(post("/api/checkout")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("待支付"))
                .andExpect(jsonPath("$.data.merchant").value("Campus Kitchen"));

        assertThat(ordersMapper.selectCount(null).intValue()).isEqualTo(beforeOrders + 1);
        assertThat(orderItemMapper.selectCount(null).intValue()).isEqualTo(beforeItems + 1);

        mockMvc.perform(post("/api/orders/70001/cancel")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("已取消"));

        Orders deliveringOrder = ordersMapper.selectById(70002L);
        deliveringOrder.setStatus("delivering");
        ordersMapper.updateById(deliveringOrder);

        mockMvc.perform(post("/api/orders/70002/complete")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("已完成"));
    }

    @Test
    void checkoutUsesSavedAddressSnapshotFromUserService() throws Exception {
        String consumerToken = consumerToken();

        ProductQuoteResponse quoteResponse = new ProductQuoteResponse();
        quoteResponse.setMerchantId(20001L);
        quoteResponse.setAvailable(true);
        quoteResponse.setTotalAmount(new BigDecimal("22.00"));
        ProductQuoteResponse.Item quoteItem = new ProductQuoteResponse.Item();
        quoteItem.setProductId(30001L);
        quoteItem.setMerchantId(20001L);
        quoteItem.setName("Braised Pork Rice");
        quoteItem.setImage("/oss/life-assistant/demo/products/braised-pork-rice.png");
        quoteItem.setUnitPrice(new BigDecimal("22.00"));
        quoteItem.setQuantity(1);
        quoteItem.setStock(100);
        quoteItem.setSubtotal(new BigDecimal("22.00"));
        quoteItem.setActive(true);
        quoteItem.setStockEnough(true);
        quoteResponse.getItems().add(quoteItem);
        when(merchantProductClient.quote(any())).thenReturn(Result.success(quoteResponse));

        StockChangeResponse reserveResponse = new StockChangeResponse();
        reserveResponse.setSuccess(true);
        reserveResponse.setStatus("reserved");
        reserveResponse.setMessage("库存预留成功");
        when(merchantProductClient.reserve(any())).thenReturn(reserveResponse);

        AddressSnapshot savedAddress = new AddressSnapshot();
        savedAddress.setId(50001L);
        savedAddress.setName("Demo User");
        savedAddress.setPhone("13800138001");
        savedAddress.setDetail("Saved dorm address");
        when(userClient.getAddress(10001L, 50001L)).thenReturn(savedAddress);

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setMerchantId(20001L);
        checkoutRequest.setAddressId(50001L);
        CheckoutItem checkoutItem = new CheckoutItem();
        checkoutItem.setProductId(30001L);
        checkoutItem.setQuantity(1);
        checkoutRequest.setItems(List.of(checkoutItem));

        String created = mockMvc.perform(post("/api/checkout")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.address").value("Demo User 13800138001 Saved dorm address"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long orderId = Long.valueOf(objectMapper.readTree(created).path("data").path("id").asText());
        Orders storedOrder = ordersMapper.selectById(orderId);
        assertThat(storedOrder.getAddressId()).isEqualTo(50001L);
        assertThat(storedOrder.getAddressDetail()).isEqualTo("Demo User 13800138001 Saved dorm address");
    }

    @Test
    void merchantPublicApisExposeMerchantOrdersAndUpdates() throws Exception {
        String merchantToken = merchantToken();

        Orders pendingAccept = ordersMapper.selectById(70001L);
        pendingAccept.setStatus("pending_accept");
        ordersMapper.updateById(pendingAccept);

        JsonNode merchantOrders = readData(mockMvc.perform(get("/api/merchant/orders")
                .header(HttpHeaders.AUTHORIZATION, merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());
        assertThat(merchantOrders).anyMatch(node -> node.path("orderNo").asText().equals("ORD202608270000000001"));

        MerchantOrderUpdateRequest request = new MerchantOrderUpdateRequest();
        request.setStatus("已完成");
        mockMvc.perform(put("/api/merchant/orders/70001")
                        .header(HttpHeaders.AUTHORIZATION, merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("已完成"));
    }

    @Test
    void adminPublicApisExposeOrderListAndDetail() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/admin/orders/70001")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("ORD202608270000000001"));
    }

    private JsonNode readData(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MerchantCatalogClient.MerchantSnapshot activeMerchant() {
        MerchantCatalogClient.MerchantSnapshot merchant = new MerchantCatalogClient.MerchantSnapshot();
        merchant.setId(20001L);
        merchant.setName("Campus Kitchen");
        merchant.setAvatar("/oss/life-assistant/demo/merchants/campus-kitchen.png");
        merchant.setStatus("active");
        merchant.setMinDeliveryFee(new BigDecimal("20.00"));
        merchant.setDeliveryFee(new BigDecimal("5.00"));
        return merchant;
    }

    private String consumerToken() {
        return "Bearer " + jwtUtil.generateToken(10001L, "consumer", "demo");
    }

    private String merchantToken() {
        return "Bearer " + jwtUtil.generateToken(20001L, "merchant", "merchant1");
    }

    private String adminToken() {
        return "Bearer " + jwtUtil.generateToken(1L, "admin", "admin");
    }
}
