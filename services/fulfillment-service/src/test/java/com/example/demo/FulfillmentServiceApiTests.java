package com.example.demo;

import com.example.demo.common.JwtUtil;
import com.example.demo.fulfillment.client.MerchantCatalogClient;
import com.example.demo.fulfillment.client.OrderClient;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/fulfillment-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FulfillmentServiceApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private OrderClient orderClient;

    @MockitoBean
    private MerchantCatalogClient merchantCatalogClient;

    @BeforeEach
    void setUp() {
        when(merchantCatalogClient.getMerchant(20001L)).thenReturn(merchant());
    }

    @Test
    void riderRegisterThenLoginReturnsRiderToken() throws Exception {
        String registerBody = """
                {
                  "username": "newrider",
                  "phone": "13900000002",
                  "password": "123456",
                  "nickname": "New Rider"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/rider/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String loginBody = """
                {
                  "username": "13900000002",
                  "password": "123456"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/rider/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("rider"))
                .andExpect(jsonPath("$.data.user.status").value("pending"));
    }

    @Test
    void riderRegisterRejectsDuplicatePhone() throws Exception {
        String body = """
                {
                  "username": "duperider",
                  "phone": "13800138004",
                  "password": "123456"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/rider/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号已注册"));
    }

    @Test
    void adminCanListAuditFreezeAndUnfreezeRiders() throws Exception {
        mockMvc.perform(get("/api/admin/riders")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data[0].password").doesNotExist());

        mockMvc.perform(put("/api/admin/riders/40002/audit")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\",\"opinion\":\"通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.auditOpinion").value("通过"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/riders/40001")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("frozen"));

        mockMvc.perform(put("/api/admin/riders/40001/unfreeze").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void riderDashboardSummarizesTodayCompletedTasks() throws Exception {
        when(orderClient.getCompletedTasks(40001L)).thenReturn(List.of(
                task(70003L, 40001L, "completed"),
                task(70004L, 40001L, "completed")
        ));

        mockMvc.perform(get("/api/rider/dashboard").header(HttpHeaders.AUTHORIZATION, riderToken(40001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.rider.todayDeliveries").value(2))
                .andExpect(jsonPath("$.data.rider.todayEarnings").value(10.0))
                .andExpect(jsonPath("$.data.rider.status").value("active"));
    }

    @Test
    void getRiderProfileReturnsCurrentRider() throws Exception {
        mockMvc.perform(get("/api/rider/profile").header(HttpHeaders.AUTHORIZATION, riderToken(40001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("40001"))
                .andExpect(jsonPath("$.data.name").value("rider01"))
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void updateRiderProfileRejectsDuplicatePhone() throws Exception {
        mockMvc.perform(put("/api/rider/profile")
                        .header(HttpHeaders.AUTHORIZATION, riderToken(40001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138005\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号已被其他骑手使用"));
    }

    @Test
    void getTasksCombinesOrderTasksAndMerchantSnapshots() throws Exception {
        when(orderClient.getAvailableTasks()).thenReturn(List.of(task(70001L, null, "pending_accept")));
        when(orderClient.getAssignedTasks(40001L)).thenReturn(List.of(task(70002L, 40001L, "delivering")));
        when(orderClient.getCompletedTasks(40001L)).thenReturn(List.of(task(70003L, 40001L, "completed")));

        mockMvc.perform(get("/api/rider/tasks").header(HttpHeaders.AUTHORIZATION, riderToken(40001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available[0].merchant").value("Campus Kitchen"))
                .andExpect(jsonPath("$.data.available[0].status").value("待取餐"))
                .andExpect(jsonPath("$.data.assigned[0].status").value("配送中"))
                .andExpect(jsonPath("$.data.completed[0].status").value("已完成"))
                .andExpect(jsonPath("$.data.stats.completedOrders").value(1))
                .andExpect(jsonPath("$.data.stats.totalEarnings").value(5.0));
    }

    @Test
    void acceptTaskAssignsActiveRiderThroughOrderService() throws Exception {
        OrderClient.OrderTaskSnapshot assigned = task(70001L, 40001L, "delivering");
        when(orderClient.assignRider(70001L, 40001L)).thenReturn(assigned);

        mockMvc.perform(put("/api/rider/tasks/70001")
                        .header(HttpHeaders.AUTHORIZATION, riderToken(40001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"待接单\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("配送中"))
                .andExpect(jsonPath("$.data.destination").value("Dorm 1"));

        verify(orderClient).assignRider(70001L, 40001L);
    }

    @Test
    void frozenRiderCannotUpdateTask() throws Exception {
        mockMvc.perform(put("/api/rider/tasks/70001")
                        .header(HttpHeaders.AUTHORIZATION, riderToken(40002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"待接单\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("骑手账号审核通过后才能使用该功能"));
    }

    @Test
    void deliveryInfoOnlyAllowsOrderOwner() throws Exception {
        when(orderClient.getDeliveryOrder(70001L)).thenReturn(task(70001L, 40001L, "delivering"));

        mockMvc.perform(get("/api/delivery/70001").header(HttpHeaders.AUTHORIZATION, consumerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value("70001"))
                .andExpect(jsonPath("$.data.status").value("配送中"))
                .andExpect(jsonPath("$.data.riderName").value("rider01"));

        mockMvc.perform(get("/api/delivery/70001").header(HttpHeaders.AUTHORIZATION, consumerToken(10002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权查看该订单配送信息"));
    }

    @Test
    void internalRiderSnapshotReturnsNotFoundForMissingRider() throws Exception {
        mockMvc.perform(get("/internal/riders/49999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("骑手不存在"));
    }

    private String riderToken(Long riderId) {
        return "Bearer " + jwtUtil.generateToken(riderId, "rider", "rider" + riderId);
    }

    private String consumerToken(Long userId) {
        return "Bearer " + jwtUtil.generateToken(userId, "consumer", "user" + userId);
    }

    private String adminToken() {
        return "Bearer " + jwtUtil.generateToken(1L, "admin", "admin");
    }

    private MerchantCatalogClient.MerchantSnapshot merchant() {
        MerchantCatalogClient.MerchantSnapshot merchant = new MerchantCatalogClient.MerchantSnapshot();
        merchant.setId(20001L);
        merchant.setName("Campus Kitchen");
        merchant.setAvatar("/merchant.png");
        merchant.setAddress("Canteen 2");
        return merchant;
    }

    private OrderClient.OrderTaskSnapshot task(Long orderId, Long riderId, String status) {
        OrderClient.OrderTaskSnapshot task = new OrderClient.OrderTaskSnapshot();
        task.setId(orderId);
        task.setOrderNo("ORD" + orderId);
        task.setUserId(10001L);
        task.setMerchantId(20001L);
        task.setRiderId(riderId);
        task.setStatus(status);
        task.setAddressDetail("Dorm 1");
        task.setActualAmount(new BigDecimal("32.00"));
        task.setDeliveryFee(new BigDecimal("5.00"));
        task.setCreateTime(LocalDateTime.of(2026, 8, 27, 12, 0));
        task.setPaidAt(LocalDateTime.of(2026, 8, 27, 12, 1));
        task.setCompletedAt("completed".equals(status) ? LocalDateTime.now() : null);
        OrderClient.ItemSnapshot item = new OrderClient.ItemSnapshot();
        item.setName("Braised Pork Rice");
        item.setQuantity(2);
        task.setItems(List.of(item));
        return task;
    }
}
