package com.example.demo;

import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrdersMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAndMessageApiTests extends BackendIntegrationTestSupport {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private RiderMapper riderMapper;

    @Autowired
    private OrdersMapper ordersMapper;

    @Test
    void adminEndpointsRequireAdminRoleAndCanManageSubjectStatus() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");
        String adminToken = login("/api/auth/admin/login", "admin", "123456");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问管理员接口"));

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("keyword", "Demo")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value("10001"));

        mockMvc.perform(delete("/api/admin/users/{id}", 10001L)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("frozen", userMapper.selectById(10001L).getStatus());

        mockMvc.perform(put("/api/admin/users/{id}/unfreeze", 10001L)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("active", userMapper.selectById(10001L).getStatus());
    }

    @Test
    void adminCanAuditPendingMerchantAndRiderAndQueryOrders() throws Exception {
        String adminToken = login("/api/auth/admin/login", "admin", "123456");

        Merchant pendingMerchant = new Merchant();
        pendingMerchant.setId(23001L);
        pendingMerchant.setUsername("pendingAdminMerchant");
        pendingMerchant.setPassword("secret");
        pendingMerchant.setName("Pending Admin Merchant");
        pendingMerchant.setPhone("13800138301");
        pendingMerchant.setAddress("Audit Street");
        pendingMerchant.setCategory("Food");
        pendingMerchant.setStatus("pending");
        pendingMerchant.setRating(new BigDecimal("4.0"));
        pendingMerchant.setMonthlySales(0);
        pendingMerchant.setDeliveryFee(BigDecimal.ZERO);
        merchantMapper.insert(pendingMerchant);

        Rider pendingRider = new Rider();
        pendingRider.setId(43001L);
        pendingRider.setName("pendingAdminRider");
        pendingRider.setPassword("secret");
        pendingRider.setPhone("13800138401");
        pendingRider.setStatus("pending");
        pendingRider.setServiceArea("Campus");
        riderMapper.insert(pendingRider);

        mockMvc.perform(put("/api/admin/merchants/{id}/audit", 23001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "active",
                                  "opinion": "pass"
                                }
                                """)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("active", merchantMapper.selectById(23001L).getStatus());

        mockMvc.perform(put("/api/admin/riders/{id}/audit", 43001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "active",
                                  "opinion": "pass"
                                }
                                """)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        Rider auditedRider = riderMapper.selectById(43001L);
        assertEquals("active", auditedRider.getStatus());
        assertEquals("pass", auditedRider.getAuditOpinion());

        Orders order = new Orders();
        order.setId(83001L);
        order.setOrderNo("ORD-ADMIN-QUERY");
        order.setUserId(10001L);
        order.setMerchantId(20001L);
        order.setRiderId(40001L);
        order.setType("delivery");
        order.setTotalAmount(new BigDecimal("35.00"));
        order.setActualAmount(new BigDecimal("30.00"));
        order.setDeliveryFee(new BigDecimal("5.00"));
        order.setDiscount(BigDecimal.ZERO);
        order.setStatus("completed");
        order.setAddressDetail("Admin Query Address");
        ordersMapper.insert(order);

        mockMvc.perform(get("/api/admin/orders")
                        .param("keyword", "ORD-ADMIN")
                        .param("status", "completed")
                        .param("type", "delivery")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value("83001"));

        mockMvc.perform(get("/api/admin/orders/{id}", 83001L)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-ADMIN-QUERY"));
    }

    @Test
    void messageConversationTracksUnreadCountsAndMarksMessagesRead() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");
        String merchantToken = login("/api/auth/merchant/login", "merchant1", "123456");

        Orders order = new Orders();
        order.setId(84001L);
        order.setOrderNo("ORD-MESSAGE-1");
        order.setUserId(10001L);
        order.setMerchantId(20001L);
        order.setRiderId(40001L);
        order.setType("delivery");
        order.setTotalAmount(new BigDecimal("38.00"));
        order.setActualAmount(new BigDecimal("33.00"));
        order.setDeliveryFee(new BigDecimal("5.00"));
        order.setDiscount(BigDecimal.ZERO);
        order.setStatus("pending_accept");
        order.setAddressDetail("Message Address");
        ordersMapper.insert(order);

        String messageBody = mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverId": 20001,
                                  "receiverType": "merchant",
                                  "orderId": 84001,
                                  "content": "Please pack carefully"
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.receiverType").value("merchant"))
                .andExpect(jsonPath("$.data.isRead").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNotNull(readBody(messageBody).path("data").path("id").asText(null));

        mockMvc.perform(get("/api/messages/unread-count")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(get("/api/messages/threads")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].targetId").value("10001"))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1));

        mockMvc.perform(get("/api/messages")
                        .param("targetId", "10001")
                        .param("targetType", "user")
                        .param("orderId", "84001")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].content").value("Please pack carefully"))
                .andExpect(jsonPath("$.data[0].isRead").value(true));

        mockMvc.perform(get("/api/messages/unread-count")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));

        mockMvc.perform(get("/api/messages/orders/{orderId}", 84001L)
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-MESSAGE-1"));
    }

    @Test
    void messageApiRejectsBlankContentAndUnrelatedOrderParticipant() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");

        Orders order = new Orders();
        order.setId(84002L);
        order.setOrderNo("ORD-MESSAGE-2");
        order.setUserId(10001L);
        order.setMerchantId(20001L);
        order.setType("delivery");
        order.setTotalAmount(new BigDecimal("38.00"));
        order.setActualAmount(new BigDecimal("33.00"));
        order.setDeliveryFee(new BigDecimal("5.00"));
        order.setDiscount(BigDecimal.ZERO);
        order.setStatus("pending_accept");
        order.setAddressDetail("Message Address");
        ordersMapper.insert(order);

        Merchant unrelatedMerchant = new Merchant();
        unrelatedMerchant.setId(23002L);
        unrelatedMerchant.setUsername("unrelatedMerchant");
        unrelatedMerchant.setPassword("secret");
        unrelatedMerchant.setName("Unrelated Merchant");
        unrelatedMerchant.setPhone("13800138302");
        unrelatedMerchant.setAddress("Other Street");
        unrelatedMerchant.setCategory("Food");
        unrelatedMerchant.setStatus("active");
        unrelatedMerchant.setRating(new BigDecimal("4.0"));
        unrelatedMerchant.setMonthlySales(0);
        unrelatedMerchant.setDeliveryFee(BigDecimal.ZERO);
        merchantMapper.insert(unrelatedMerchant);

        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverId": 20001,
                                  "receiverType": "merchant",
                                  "orderId": 84002,
                                  "content": "   "
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("消息内容不能为空"));

        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverId": 23002,
                                  "receiverType": "merchant",
                                  "orderId": 84002,
                                  "content": "Hello"
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("接收方与订单无关"));
    }
}
