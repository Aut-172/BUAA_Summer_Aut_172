package com.example.demo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrdersMapper;
import com.example.demo.payment.entity.Payment;
import com.example.demo.payment.mapper.PaymentMapper;
import com.example.demo.user.entity.Cart;
import com.example.demo.user.mapper.CartMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsumerApiTests extends BackendIntegrationTestSupport {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Test
    void profileUpdateRejectsDuplicatePhone() throws Exception {
        User otherUser = new User();
        otherUser.setId(10002L);
        otherUser.setUsername("otherConsumer");
        otherUser.setPassword("secret");
        otherUser.setPhone("13800139999");
        otherUser.setNickname("Other User");
        otherUser.setRole("consumer");
        otherUser.setStatus("active");
        userMapper.insert(otherUser);

        String consumerToken = login("/api/auth/login", "demo", "123456");

        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "Updated Demo",
                                  "phone": "13800139999"
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号已被其他用户使用"));
    }

    @Test
    void addressCrudKeepsOwnershipAndSingleDefaultAddress() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");

        String firstAddressBody = mockMvc.perform(post("/api/user/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice",
                                  "phone": "13800138001",
                                  "detail": "Dormitory 1",
                                  "isDefault": true
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long firstAddressId = readBody(firstAddressBody).path("data").path("id").asLong();

        String secondAddressBody = mockMvc.perform(post("/api/user/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bob",
                                  "phone": "13800138002",
                                  "detail": "Library Gate",
                                  "isDefault": true
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long secondAddressId = readBody(secondAddressBody).path("data").path("id").asLong();

        mockMvc.perform(get("/api/user/addresses")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(secondAddressId)))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].id").value(String.valueOf(firstAddressId)))
                .andExpect(jsonPath("$.data[1].isDefault").value(false));

        mockMvc.perform(put("/api/user/addresses/{id}", secondAddressId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "detail": "Library Gate East",
                                  "isDefault": true
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detail").value("Library Gate East"));

        mockMvc.perform(get("/api/user/addresses/{id}", 99999L)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(delete("/api/user/addresses/{id}", firstAddressId)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/user/addresses")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(secondAddressId)));
    }

    @Test
    void cartApiAddsUpdatesDeletesAndRejectsMissingProduct() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");

        String cartBody = mockMvc.perform(post("/api/user/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": 20001,
                                  "productId": 30001,
                                  "quantity": 2,
                                  "specLabel": "Large"
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.productId").value("30001"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long cartId = readBody(cartBody).path("data").path("id").asLong();

        mockMvc.perform(put("/api/user/cart/{id}", cartId)
                        .param("quantity", "5")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(5));

        mockMvc.perform(put("/api/user/cart/{id}", cartId)
                        .param("quantity", "0")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertNull(cartMapper.selectById(cartId));

        mockMvc.perform(post("/api/user/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": 20001,
                                  "productId": 99999,
                                  "quantity": 1
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("商品不存在"));
    }

    @Test
    void paymentRejectsOtherUsersOrderAndInvalidOrderStatus() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");

        Orders otherUserOrder = new Orders();
        otherUserOrder.setId(81001L);
        otherUserOrder.setOrderNo("ORD-PAY-OTHER");
        otherUserOrder.setUserId(10002L);
        otherUserOrder.setMerchantId(20001L);
        otherUserOrder.setType("delivery");
        otherUserOrder.setTotalAmount(new BigDecimal("30.00"));
        otherUserOrder.setActualAmount(new BigDecimal("30.00"));
        otherUserOrder.setDeliveryFee(new BigDecimal("5.00"));
        otherUserOrder.setDiscount(BigDecimal.ZERO);
        otherUserOrder.setStatus("pending_payment");
        otherUserOrder.setAddressDetail("Other Address");
        ordersMapper.insert(otherUserOrder);

        mockMvc.perform(post("/api/orders/{id}/pay", 81001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payMethod\":\"ALIPAY\"}")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        Orders paidOrder = new Orders();
        paidOrder.setId(81002L);
        paidOrder.setOrderNo("ORD-PAY-PAID");
        paidOrder.setUserId(10001L);
        paidOrder.setMerchantId(20001L);
        paidOrder.setType("delivery");
        paidOrder.setTotalAmount(new BigDecimal("40.00"));
        paidOrder.setActualAmount(new BigDecimal("40.00"));
        paidOrder.setDeliveryFee(new BigDecimal("5.00"));
        paidOrder.setDiscount(BigDecimal.ZERO);
        paidOrder.setStatus("pending_accept");
        paidOrder.setAddressDetail("Demo Address");
        ordersMapper.insert(paidOrder);

        mockMvc.perform(post("/api/orders/{id}/pay", 81002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payMethod\":\"ALIPAY\"}")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("当前订单状态不允许支付"));

        assertEquals(0L, paymentMapper.selectCount(
                new LambdaQueryWrapper<Payment>()
                        .in(Payment::getOrderId, 81001L, 81002L)
        ));
    }
}
