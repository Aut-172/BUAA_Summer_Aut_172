package com.example.demo;

import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrderItemMapper;
import com.example.demo.order.mapper.OrdersMapper;
import com.example.demo.review.entity.Review;
import com.example.demo.review.mapper.ReviewMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthOrderReviewRiderApiTests extends BackendIntegrationTestSupport {

    private static final String DEMO_PASSWORD_HASH = "$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private RiderMapper riderMapper;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Test
    void authApiRejectsDuplicateRegistrationWrongPasswordAndFrozenLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "newConsumer",
                                  "phone": "13800139001",
                                  "password": "123456",
                                  "nickname": "New Consumer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "newConsumer",
                                  "phone": "13800139002",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "newConsumer2",
                                  "phone": "13800139001",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号已注册"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "password": "bad-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        var demo = userMapper.selectById(10001L);
        demo.setStatus("frozen");
        userMapper.updateById(demo);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("账号已被冻结，无法登录"));
    }

    @Test
    void checkoutApiRejectsInvalidAddressMerchantProductAndSpec() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");

        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": 20001,
                                  "address": "  ",
                                  "items": [
                                    {
                                      "productId": 30001,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("收货地址不能为空"));

        Merchant inactiveMerchant = new Merchant();
        inactiveMerchant.setId(24001L);
        inactiveMerchant.setUsername("inactiveCheckoutMerchant");
        inactiveMerchant.setPassword("secret");
        inactiveMerchant.setName("Inactive Checkout Merchant");
        inactiveMerchant.setPhone("13800138501");
        inactiveMerchant.setAddress("Closed Street");
        inactiveMerchant.setCategory("Food");
        inactiveMerchant.setStatus("frozen");
        inactiveMerchant.setRating(new BigDecimal("4.0"));
        inactiveMerchant.setMonthlySales(0);
        inactiveMerchant.setDeliveryFee(BigDecimal.ZERO);
        merchantMapper.insert(inactiveMerchant);

        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": 24001,
                                  "address": "Dormitory",
                                  "items": [
                                    {
                                      "productId": 30001,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("商家不存在或不可下单"));

        Merchant otherMerchant = new Merchant();
        otherMerchant.setId(24002L);
        otherMerchant.setUsername("otherCheckoutMerchant");
        otherMerchant.setPassword("secret");
        otherMerchant.setName("Other Checkout Merchant");
        otherMerchant.setPhone("13800138502");
        otherMerchant.setAddress("Other Street");
        otherMerchant.setCategory("Food");
        otherMerchant.setStatus("active");
        otherMerchant.setRating(new BigDecimal("4.0"));
        otherMerchant.setMonthlySales(0);
        otherMerchant.setMinDeliveryFee(BigDecimal.ZERO);
        otherMerchant.setDeliveryFee(BigDecimal.ZERO);
        merchantMapper.insert(otherMerchant);

        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": 24002,
                                  "address": "Dormitory",
                                  "items": [
                                    {
                                      "productId": 30001,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("订单中包含不属于当前商家的商品"));

        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": 20001,
                                  "address": "Dormitory",
                                  "items": [
                                    {
                                      "productId": 30001,
                                      "quantity": 1,
                                      "specLabel": "Missing"
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("商品规格不存在: Missing"));
    }

    @Test
    void reviewApiRejectsIncompleteInvalidAndDuplicateReviews() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");

        Orders pendingOrder = createReviewOrder(85001L, "ORD-REVIEW-PENDING", "pending_accept");
        createReviewItem(pendingOrder.getId(), 86001L, 30001L);

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 85001,
                                  "items": [
                                    {
                                      "productId": 30001,
                                      "rating": 5,
                                      "content": "Good"
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只有已完成订单才能评价"));

        Orders completedOrder = createReviewOrder(85002L, "ORD-REVIEW-COMPLETED", "completed");
        createReviewItem(completedOrder.getId(), 86002L, 30001L);

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 85002,
                                  "items": [
                                    {
                                      "productId": 30001,
                                      "rating": 6,
                                      "content": "Too high"
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("评分必须在1-5之间"));

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 85002,
                                  "items": [
                                    {
                                      "productId": 99999,
                                      "rating": 5,
                                      "content": "Wrong product"
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("商品ID 99999 不属于该订单"));

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 85002,
                                  "items": [
                                    {
                                      "productId": 30001,
                                      "rating": 5,
                                      "content": "Good"
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].rating").value(5));

        Review savedReview = reviewMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderId, 85002L)
        );
        assertNotNull(savedReview);
        assertEquals("Good", savedReview.getContent());

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 85002,
                                  "items": [
                                    {
                                      "productId": 30001,
                                      "rating": 4,
                                      "content": "Again"
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该订单已评价，不可重复评价"));
    }

    @Test
    void riderApiRejectsDuplicatePhoneAndOtherRiderTaskCompletion() throws Exception {
        Rider otherRider = new Rider();
        otherRider.setId(44001L);
        otherRider.setName("rider02");
        otherRider.setPassword(DEMO_PASSWORD_HASH);
        otherRider.setPhone("13800138601");
        otherRider.setStatus("active");
        otherRider.setServiceArea("Campus");
        riderMapper.insert(otherRider);

        String riderToken = login("/api/auth/rider/login", "rider01", "123456");
        String otherRiderToken = login("/api/auth/rider/login", "rider02", "123456");

        mockMvc.perform(put("/api/rider/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "Updated Rider",
                                  "phone": "13800138601",
                                  "serviceArea": "North Campus"
                                }
                                """)
                        .header("Authorization", bearer(riderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号已被其他骑手使用"));

        Orders assignedOrder = new Orders();
        assignedOrder.setId(87001L);
        assignedOrder.setOrderNo("ORD-RIDER-OTHER");
        assignedOrder.setUserId(10001L);
        assignedOrder.setMerchantId(20001L);
        assignedOrder.setRiderId(40001L);
        assignedOrder.setType("delivery");
        assignedOrder.setTotalAmount(new BigDecimal("38.00"));
        assignedOrder.setActualAmount(new BigDecimal("33.00"));
        assignedOrder.setDeliveryFee(new BigDecimal("5.00"));
        assignedOrder.setDiscount(BigDecimal.ZERO);
        assignedOrder.setStatus("delivering");
        assignedOrder.setAddressDetail("Rider Address");
        ordersMapper.insert(assignedOrder);

        mockMvc.perform(put("/api/rider/tasks/{id}", 87001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"已完成\"}")
                        .header("Authorization", bearer(otherRiderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该订单不属于当前骑手"));

        assertEquals("delivering", ordersMapper.selectById(87001L).getStatus());
        assertEquals(40001L, ordersMapper.selectById(87001L).getRiderId());
    }

    private Orders createReviewOrder(Long id, String orderNo, String status) {
        Orders order = new Orders();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setUserId(10001L);
        order.setMerchantId(20001L);
        order.setType("delivery");
        order.setTotalAmount(new BigDecimal("30.00"));
        order.setActualAmount(new BigDecimal("25.00"));
        order.setDeliveryFee(new BigDecimal("5.00"));
        order.setDiscount(BigDecimal.ZERO);
        order.setStatus(status);
        order.setAddressDetail("Review Address");
        order.setCompletedAt("completed".equals(status) ? LocalDateTime.now() : null);
        ordersMapper.insert(order);
        return order;
    }

    private void createReviewItem(Long orderId, Long itemId, Long productId) {
        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setName("Braised Pork Rice");
        item.setPrice(new BigDecimal("22.00"));
        item.setQuantity(1);
        item.setSpecLabel("Large");
        item.setSubtotal(new BigDecimal("25.00"));
        item.setReviewed(false);
        orderItemMapper.insert(item);
    }
}
