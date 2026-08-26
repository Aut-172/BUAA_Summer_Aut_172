package com.example.demo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.admin.service.AdminService;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.dto.RegisterRequest;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.auth.service.AuthService;
import com.example.demo.common.BusinessException;
import com.example.demo.coupon.entity.Coupon;
import com.example.demo.coupon.entity.UserCoupon;
import com.example.demo.coupon.mapper.CouponMapper;
import com.example.demo.coupon.mapper.UserCouponMapper;
import com.example.demo.coupon.service.CouponService;
import com.example.demo.delivery.dto.DeliveryVO;
import com.example.demo.delivery.service.DeliveryService;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.entity.ProductSpec;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.merchant.mapper.ProductSpecMapper;
import com.example.demo.merchant.service.MerchantService;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrdersMapper;
import com.example.demo.payment.entity.Payment;
import com.example.demo.payment.mapper.PaymentMapper;
import com.example.demo.rider.dto.RiderTaskUpdateRequest;
import com.example.demo.rider.service.RiderService;
import com.example.demo.review.entity.Review;
import com.example.demo.review.mapper.ReviewMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoApplicationTests {

    private static final String STATUS_PENDING_PAYMENT_TEXT = "\u5f85\u652f\u4ed8";
    private static final String STATUS_PENDING_ACCEPT_TEXT = "\u5f85\u53d6\u9910";
    private static final String STATUS_DELIVERING_TEXT = "\u914d\u9001\u4e2d";
    private static final String STATUS_COMPLETED_TEXT = "\u5df2\u5b8c\u6210";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CouponService couponService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private RiderService riderService;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSpecMapper productSpecMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private MerchantMapper authMerchantMapper;

    @Autowired
    private RiderMapper authRiderMapper;

    @Autowired
    private MerchantService merchantService;

    @Test
    void merchantAndRiderRegistrationCanLoginButRequiresAdminAuditForCoreFeatures() {
        RegisterRequest merchantRequest = new RegisterRequest();
        merchantRequest.setUsername("pendingMerchant");
        merchantRequest.setPhone("13800138991");
        merchantRequest.setPassword("123456");
        merchantRequest.setNickname("Pending Merchant");

        authService.registerMerchant(merchantRequest);

        Merchant merchant = authMerchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, "pendingMerchant"));
        assertNotNull(merchant);
        assertEquals("pending", merchant.getStatus());

        LoginRequest merchantLogin = new LoginRequest();
        merchantLogin.setUsername("pendingMerchant");
        merchantLogin.setPassword("123456");
        LoginResponse pendingMerchantResponse = authService.loginMerchant(merchantLogin);
        assertEquals("merchant", pendingMerchantResponse.getUser().getRole());
        assertEquals("pending", pendingMerchantResponse.getUser().getStatus());

        Product blockedProduct = new Product();
        blockedProduct.setName("Blocked Product");
        blockedProduct.setCategoryId(1L);
        blockedProduct.setPrice(new BigDecimal("9.90"));
        blockedProduct.setStock(10);

        BusinessException merchantBlocked = assertThrows(BusinessException.class,
                () -> merchantService.addProduct(merchant.getId(), blockedProduct));
        assertEquals(403, merchantBlocked.getCode());

        adminService.auditMerchant(merchant.getId(), "active", "通过");
        LoginResponse merchantResponse = authService.loginMerchant(merchantLogin);
        assertEquals("merchant", merchantResponse.getUser().getRole());
        assertEquals("active", merchantResponse.getUser().getStatus());

        Product allowedProduct = new Product();
        allowedProduct.setName("Allowed Product");
        allowedProduct.setCategoryId(1L);
        allowedProduct.setPrice(new BigDecimal("12.90"));
        allowedProduct.setStock(10);
        Product insertedProduct = merchantService.addProduct(merchant.getId(), allowedProduct);
        assertNotNull(insertedProduct.getId());

        RegisterRequest riderRequest = new RegisterRequest();
        riderRequest.setUsername("pendingRider");
        riderRequest.setPhone("13800138992");
        riderRequest.setPassword("123456");
        riderRequest.setNickname("Pending Rider");

        authService.registerRider(riderRequest);

        Rider rider = authRiderMapper.selectOne(new LambdaQueryWrapper<Rider>()
                .eq(Rider::getPhone, "13800138992"));
        assertNotNull(rider);
        assertEquals("pending", rider.getStatus());

        LoginRequest riderLogin = new LoginRequest();
        riderLogin.setUsername("13800138992");
        riderLogin.setPassword("123456");
        LoginResponse pendingRiderResponse = authService.loginRider(riderLogin);
        assertEquals("rider", pendingRiderResponse.getUser().getRole());
        assertEquals("pending", pendingRiderResponse.getUser().getStatus());

        Orders order = new Orders();
        order.setOrderNo("ORD-PENDING-RIDER-1");
        order.setUserId(10001L);
        order.setMerchantId(20001L);
        order.setType("delivery");
        order.setTotalAmount(new BigDecimal("20.00"));
        order.setActualAmount(new BigDecimal("20.00"));
        order.setDeliveryFee(new BigDecimal("5.00"));
        order.setDiscount(BigDecimal.ZERO);
        order.setStatus("pending_accept");
        order.setAddressDetail("Dormitory 2");
        ordersMapper.insert(order);

        RiderTaskUpdateRequest taskRequest = new RiderTaskUpdateRequest();
        taskRequest.setStatus(STATUS_PENDING_ACCEPT_TEXT);
        BusinessException riderBlocked = assertThrows(BusinessException.class,
                () -> riderService.updateTask(rider.getId(), order.getId(), taskRequest));
        assertEquals(403, riderBlocked.getCode());

        adminService.auditRider(rider.getId(), "active", "通过");
        LoginResponse riderResponse = authService.loginRider(riderLogin);
        assertEquals("rider", riderResponse.getUser().getRole());
        assertEquals("active", riderResponse.getUser().getStatus());

        riderService.updateTask(rider.getId(), order.getId(), taskRequest);
        Orders updatedOrder = ordersMapper.selectById(order.getId());
        assertEquals("delivering", updatedOrder.getStatus());
        assertEquals(rider.getId(), updatedOrder.getRiderId());
    }

    @Test
    void claimCouponRejectsEmptyInventoryAndNullLimit() {
        Coupon coupon = new Coupon();
        coupon.setName("empty");
        coupon.setDiscount(new BigDecimal("8.00"));
        coupon.setThreshold(new BigDecimal("20.00"));
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(1));
        coupon.setTotalCount(0);
        coupon.setClaimedCount(null);
        coupon.setLimitPerUser(null);
        coupon.setStatus("released");
        couponMapper.insert(coupon);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.claimCoupon(10001L, coupon.getId()));
        assertEquals(400, ex.getCode());
    }

    @Test
    void deliveryInfoRequiresOrderOwner() {
        Orders order = new Orders();
        order.setOrderNo("ORD-DELIVERY-1");
        order.setUserId(10001L);
        order.setMerchantId(20001L);
        order.setType("delivery");
        order.setTotalAmount(new BigDecimal("25.00"));
        order.setActualAmount(new BigDecimal("25.00"));
        order.setDeliveryFee(new BigDecimal("5.00"));
        order.setDiscount(BigDecimal.ZERO);
        order.setStatus("pending_accept");
        order.setAddressDetail("Test address");
        ordersMapper.insert(order);

        DeliveryVO delivery = deliveryService.getDeliveryInfo(10001L, order.getId());
        assertNotNull(delivery);
        assertEquals(order.getId(), delivery.getOrderId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deliveryService.getDeliveryInfo(99999L, order.getId()));
        assertEquals(403, ex.getCode());
    }

    @Test
    void riderCompletionMarksLockedCouponAsUsed() {
        Orders order = new Orders();
        order.setOrderNo("ORD-RIDER-1");
        order.setUserId(10001L);
        order.setMerchantId(20001L);
        order.setRiderId(40001L);
        order.setType("delivery");
        order.setTotalAmount(new BigDecimal("38.00"));
        order.setActualAmount(new BigDecimal("28.00"));
        order.setDeliveryFee(new BigDecimal("5.00"));
        order.setDiscount(new BigDecimal("10.00"));
        order.setStatus("delivering");
        order.setAddressDetail("Dormitory 1");
        order.setCouponId(60001L);
        order.setPaidAt(LocalDateTime.now().minusMinutes(20));
        ordersMapper.insert(order);

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(10001L);
        userCoupon.setCouponId(60001L);
        userCoupon.setStatus("locked");
        userCoupon.setOrderId(order.getId());
        userCoupon.setClaimedAt(LocalDateTime.now().minusDays(1));
        userCouponMapper.insert(userCoupon);

        RiderTaskUpdateRequest request = new RiderTaskUpdateRequest();
        request.setStatus(STATUS_COMPLETED_TEXT);

        riderService.updateTask(40001L, order.getId(), request);

        Orders updatedOrder = ordersMapper.selectById(order.getId());
        UserCoupon updatedCoupon = userCouponMapper.selectById(userCoupon.getId());
        assertEquals("completed", updatedOrder.getStatus());
        assertNotNull(updatedOrder.getCompletedAt());
        assertEquals("used", updatedCoupon.getStatus());
        assertNotNull(updatedCoupon.getUsedAt());
    }

    @Test
    void releaseCouponClearsOrderBinding() {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(10001L);
        userCoupon.setCouponId(60001L);
        userCoupon.setStatus("locked");
        userCoupon.setOrderId(123456L);
        userCoupon.setClaimedAt(LocalDateTime.now().minusDays(1));
        userCoupon.setUsedAt(LocalDateTime.now().minusHours(1));
        userCouponMapper.insert(userCoupon);

        couponService.releaseCoupon(123456L);

        UserCoupon updated = userCouponMapper.selectById(userCoupon.getId());
        assertEquals("unused", updated.getStatus());
        assertNull(updated.getOrderId());
        assertNull(updated.getUsedAt());
    }

    @Test
    void fullConsumerRiderFlowWorksEndToEnd() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");
        String riderToken = login("/api/auth/rider/login", "rider01", "123456");

        mockMvc.perform(post("/api/coupons/60001/claim")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("60001"));

        String checkoutBody = """
                {
                  "merchantId": 20001,
                  "address": "Dormitory 1 Room 302",
                  "couponId": 60001,
                  "items": [
                    {
                      "productId": 30001,
                      "quantity": 2,
                      "specLabel": "Large"
                    }
                  ]
                }
                """;

        JsonNode checkoutResponse = readBody(mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(STATUS_PENDING_PAYMENT_TEXT))
                .andReturn().getResponse().getContentAsString());

        long orderId = checkoutResponse.path("data").path("id").asLong();
        Orders createdOrder = ordersMapper.selectById(orderId);
        assertEquals(new BigDecimal("55.00"), createdOrder.getTotalAmount());
        assertEquals(new BigDecimal("45.00"), createdOrder.getActualAmount());
        assertEquals(new BigDecimal("10.00"), createdOrder.getDiscount());

        UserCoupon lockedCoupon = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, 10001L)
                        .eq(UserCoupon::getCouponId, 60001L)
                        .eq(UserCoupon::getOrderId, orderId)
        );
        assertNotNull(lockedCoupon);
        assertEquals("locked", lockedCoupon.getStatus());

        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payMethod\":\"ALIPAY\"}")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(STATUS_PENDING_ACCEPT_TEXT));

        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId)
        );
        assertNotNull(payment);
        assertEquals("SUCCESS", payment.getStatus());

        mockMvc.perform(put("/api/rider/tasks/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + STATUS_PENDING_ACCEPT_TEXT + "\"}")
                        .header("Authorization", bearer(riderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(STATUS_DELIVERING_TEXT));

        mockMvc.perform(get("/api/delivery/{id}", orderId)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(STATUS_DELIVERING_TEXT))
                .andExpect(jsonPath("$.data.riderName").value("rider01"));

        mockMvc.perform(post("/api/orders/{id}/complete", orderId)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(STATUS_COMPLETED_TEXT));

        Orders completedOrder = ordersMapper.selectById(orderId);
        assertEquals("completed", completedOrder.getStatus());
        assertNotNull(completedOrder.getCompletedAt());

        UserCoupon usedCoupon = userCouponMapper.selectById(lockedCoupon.getId());
        assertEquals("used", usedCoupon.getStatus());
        assertNotNull(usedCoupon.getUsedAt());

        MockMultipartFile image = new MockMultipartFile(
                "files",
                "review.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        JsonNode uploadResponse = readBody(mockMvc.perform(multipart("/api/reviews/images")
                        .file(image)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString());
        String reviewImageUrl = uploadResponse.path("data").path(0).asText();

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "%s",
                                  "items": [
                                    {
                                      "productId": "30001",
                                      "rating": 5,
                                      "content": "味道很好，包装完整",
                                      "images": ["%s"]
                                    }
                                  ]
                                }
                                """.formatted(orderId, reviewImageUrl))
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].rating").value(5))
                .andExpect(jsonPath("$.data[0].images[0]").value(reviewImageUrl));

        Review savedReview = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderId, orderId)
                        .eq(Review::getProductId, 30001L)
        );
        assertNotNull(savedReview);
        assertEquals("味道很好，包装完整", savedReview.getContent());
        assertEquals("[\"" + reviewImageUrl + "\"]", savedReview.getImages());
    }

    @Test
    void merchantApiAcceptsChineseStatusValue() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");
        String merchantToken = login("/api/auth/merchant/login", "merchant1", "123456");

        JsonNode checkoutResponse = readBody(mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": 20001,
                                  "address": "Library Gate",
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
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString());

        long orderId = checkoutResponse.path("data").path("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payMethod\":\"ALIPAY\"}")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(STATUS_PENDING_ACCEPT_TEXT));

        mockMvc.perform(put("/api/merchant/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + STATUS_COMPLETED_TEXT + "\"}")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(STATUS_COMPLETED_TEXT));

        Orders updatedOrder = ordersMapper.selectById(orderId);
        assertEquals("completed", updatedOrder.getStatus());
        assertNotNull(updatedOrder.getCompletedAt());
    }

    @Test
    void checkoutReservesInventoryAndCancelRestoresIt() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");

        Product productBefore = productMapper.selectById(30001L);
        ProductSpec specBefore = productSpecMapper.selectById(1L);
        assertNotNull(productBefore);
        assertNotNull(specBefore);
        int initialProductStock = productBefore.getStock();
        int initialSpecStock = specBefore.getStock();

        JsonNode checkoutResponse = readBody(mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": 20001,
                                  "address": "Science Building 201",
                                  "items": [
                                    {
                                      "productId": 30001,
                                      "quantity": 2,
                                      "specLabel": "Large"
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString());

        long orderId = checkoutResponse.path("data").path("id").asLong();
        Orders createdOrder = ordersMapper.selectById(orderId);
        assertEquals("pending_payment", createdOrder.getStatus());

        Product productAfterCheckout = productMapper.selectById(30001L);
        ProductSpec specAfterCheckout = productSpecMapper.selectById(1L);
        assertEquals(initialProductStock - 2, productAfterCheckout.getStock());
        assertEquals(initialSpecStock - 2, specAfterCheckout.getStock());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Product productAfterCancel = productMapper.selectById(30001L);
        ProductSpec specAfterCancel = productSpecMapper.selectById(1L);
        Orders cancelledOrder = ordersMapper.selectById(orderId);
        assertEquals("cancelled", cancelledOrder.getStatus());
        assertEquals(initialProductStock, productAfterCancel.getStock());
        assertEquals(initialSpecStock, specAfterCancel.getStock());
    }

    private String login(String path, String username, String password) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return readBody(response).path("data").path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode readBody(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
