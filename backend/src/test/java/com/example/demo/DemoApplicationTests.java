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
import com.example.demo.dashboard.dto.DashboardVO;
import com.example.demo.dashboard.service.DashboardService;
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
import com.example.demo.recommend.dto.RecommendVO;
import com.example.demo.recommend.service.RecommendService;
import com.example.demo.rider.dto.RiderTaskUpdateRequest;
import com.example.demo.rider.service.RiderService;
import com.example.demo.review.entity.Review;
import com.example.demo.review.mapper.ReviewMapper;
import com.example.demo.search.dto.SearchResultVO;
import com.example.demo.search.service.SearchService;
import com.example.demo.user.dto.FavoriteMerchantVO;
import com.example.demo.user.entity.UserFavoriteMerchant;
import com.example.demo.user.mapper.UserFavoriteMerchantMapper;
import com.example.demo.user.service.UserService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private DashboardService dashboardService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private RiderService riderService;

    @Autowired
    private SearchService searchService;

    @Autowired
    private UserService userService;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private UserFavoriteMerchantMapper userFavoriteMerchantMapper;

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
    void authenticatedUsersCanUploadImagesForProfilesAndProducts() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");
        MockMultipartFile avatar = new MockMultipartFile(
                "files",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/uploads/images")
                        .file(avatar)
                        .param("scene", "avatars")
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0]").value(org.hamcrest.Matchers.startsWith("/uploads/avatars/")));
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

    @Test
    void searchAndRecommendOnlyExposeActiveMerchantsWithExpectedKeywordMatches() {
        Merchant taggedMerchant = new Merchant();
        taggedMerchant.setId(21001L);
        taggedMerchant.setUsername("taggedMerchant");
        taggedMerchant.setPassword("secret");
        taggedMerchant.setName("Sunrise Breakfast");
        taggedMerchant.setPhone("13800138101");
        taggedMerchant.setAddress("East Gate");
        taggedMerchant.setCategory("Food");
        taggedMerchant.setDescription("Fresh breakfast");
        taggedMerchant.setTags("breakfast,coffee");
        taggedMerchant.setStatus("active");
        taggedMerchant.setRating(new BigDecimal("4.8"));
        taggedMerchant.setMonthlySales(800);
        taggedMerchant.setDeliveryFee(new BigDecimal("3.00"));
        taggedMerchant.setMinDeliveryFee(new BigDecimal("15.00"));
        taggedMerchant.setLatitude(new BigDecimal("39.9800000"));
        taggedMerchant.setLongitude(new BigDecimal("116.3500000"));
        authMerchantMapper.insert(taggedMerchant);

        Product taggedProduct = new Product();
        taggedProduct.setId(31001L);
        taggedProduct.setMerchantId(21001L);
        taggedProduct.setCategoryId(1L);
        taggedProduct.setName("Campus Bagel");
        taggedProduct.setPrice(new BigDecimal("12.00"));
        taggedProduct.setDescription("Warm bagel");
        taggedProduct.setMonthlySales(90);
        taggedProduct.setStock(20);
        taggedProduct.setType("delivery");
        taggedProduct.setStatus("active");
        productMapper.insert(taggedProduct);

        Merchant inactiveMerchant = new Merchant();
        inactiveMerchant.setId(21002L);
        inactiveMerchant.setUsername("inactiveMerchant");
        inactiveMerchant.setPassword("secret");
        inactiveMerchant.setName("Campus Bagel Closed");
        inactiveMerchant.setPhone("13800138102");
        inactiveMerchant.setAddress("West Gate");
        inactiveMerchant.setCategory("Food");
        inactiveMerchant.setTags("breakfast");
        inactiveMerchant.setStatus("frozen");
        inactiveMerchant.setRating(new BigDecimal("5.0"));
        inactiveMerchant.setMonthlySales(9999);
        inactiveMerchant.setDeliveryFee(BigDecimal.ZERO);
        authMerchantMapper.insert(inactiveMerchant);

        Product inactiveProduct = new Product();
        inactiveProduct.setId(31002L);
        inactiveProduct.setMerchantId(21002L);
        inactiveProduct.setCategoryId(1L);
        inactiveProduct.setName("Campus Bagel");
        inactiveProduct.setPrice(new BigDecimal("1.00"));
        inactiveProduct.setStock(99);
        inactiveProduct.setType("delivery");
        inactiveProduct.setStatus("active");
        productMapper.insert(inactiveProduct);

        List<SearchResultVO> productMatches = searchService.search("bagel", null, null, null, null);
        assertTrue(productMatches.stream().anyMatch(result -> result.getId().equals(21001L)));
        assertFalse(productMatches.stream().anyMatch(result -> result.getId().equals(21002L)));
        SearchResultVO activeResult = productMatches.stream()
                .filter(result -> result.getId().equals(21001L))
                .findFirst()
                .orElseThrow();
        assertEquals(1, activeResult.getProducts().size());
        assertEquals("Campus Bagel", activeResult.getProducts().get(0).getName());

        List<SearchResultVO> tagMatches = searchService.search("coffee", null, null, null, null);
        assertTrue(tagMatches.stream().anyMatch(result -> result.getId().equals(21001L)));

        List<RecommendVO> recommendations = recommendService.getRecommendations(null, null);
        assertTrue(recommendations.stream().anyMatch(result -> result.getId().equals(21001L)));
        assertFalse(recommendations.stream().anyMatch(result -> result.getId().equals(21002L)));
    }

    @Test
    void favoriteMerchantApiIsIdempotentAndDashboardCountsOnlyActiveFavorites() throws Exception {
        String consumerToken = login("/api/auth/login", "demo", "123456");

        mockMvc.perform(post("/api/user/favorites/{merchantId}", 20001L)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.merchantId").value("20001"));

        mockMvc.perform(post("/api/user/favorites/{merchantId}", 20001L)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.merchantId").value("20001"));

        assertEquals(1L, userFavoriteMerchantMapper.selectCount(
                new LambdaQueryWrapper<UserFavoriteMerchant>()
                        .eq(UserFavoriteMerchant::getUserId, 10001L)
                        .eq(UserFavoriteMerchant::getMerchantId, 20001L)
        ));

        Merchant inactiveMerchant = new Merchant();
        inactiveMerchant.setId(22001L);
        inactiveMerchant.setUsername("inactiveFavoriteMerchant");
        inactiveMerchant.setPassword("secret");
        inactiveMerchant.setName("Inactive Favorite");
        inactiveMerchant.setPhone("13800138201");
        inactiveMerchant.setAddress("Hidden Street");
        inactiveMerchant.setCategory("Food");
        inactiveMerchant.setStatus("frozen");
        inactiveMerchant.setRating(new BigDecimal("4.1"));
        inactiveMerchant.setMonthlySales(10);
        inactiveMerchant.setDeliveryFee(BigDecimal.ZERO);
        authMerchantMapper.insert(inactiveMerchant);

        UserFavoriteMerchant inactiveFavorite = new UserFavoriteMerchant();
        inactiveFavorite.setId(71001L);
        inactiveFavorite.setUserId(10001L);
        inactiveFavorite.setMerchantId(22001L);
        userFavoriteMerchantMapper.insert(inactiveFavorite);

        List<FavoriteMerchantVO> favorites = userService.getFavoriteMerchants(10001L);
        assertEquals(1, favorites.size());
        assertEquals(20001L, favorites.get(0).getMerchantId());

        DashboardVO.ConsumerData dashboard = dashboardService.getConsumerData(10001L);
        assertEquals(1, dashboard.getFavoriteMerchants());

        mockMvc.perform(get("/api/user/favorites/{merchantId}", 20001L)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/user/favorites/{merchantId}", 20001L)
                        .header("Authorization", bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        DashboardVO.ConsumerData dashboardAfterDelete = dashboardService.getConsumerData(10001L);
        assertEquals(0, dashboardAfterDelete.getFavoriteMerchants());
    }

    @Test
    void dashboardMetricsUseCompletedAtDeliveryFeeAndRiderStatus() {
        LocalDateTime now = LocalDateTime.now();

        Orders completedToday = new Orders();
        completedToday.setOrderNo("ORD-DASHBOARD-TODAY");
        completedToday.setUserId(10001L);
        completedToday.setMerchantId(20001L);
        completedToday.setRiderId(40001L);
        completedToday.setType("delivery");
        completedToday.setTotalAmount(new BigDecimal("66.00"));
        completedToday.setActualAmount(new BigDecimal("56.00"));
        completedToday.setDeliveryFee(new BigDecimal("6.00"));
        completedToday.setDiscount(new BigDecimal("10.00"));
        completedToday.setStatus("completed");
        completedToday.setAddressDetail("Today Address");
        completedToday.setCompletedAt(now);
        ordersMapper.insert(completedToday);

        Orders completedYesterday = new Orders();
        completedYesterday.setOrderNo("ORD-DASHBOARD-YESTERDAY");
        completedYesterday.setUserId(10001L);
        completedYesterday.setMerchantId(20001L);
        completedYesterday.setRiderId(40001L);
        completedYesterday.setType("delivery");
        completedYesterday.setTotalAmount(new BigDecimal("88.00"));
        completedYesterday.setActualAmount(new BigDecimal("78.00"));
        completedYesterday.setDeliveryFee(new BigDecimal("8.00"));
        completedYesterday.setDiscount(new BigDecimal("10.00"));
        completedYesterday.setStatus("completed");
        completedYesterday.setAddressDetail("Yesterday Address");
        completedYesterday.setCompletedAt(now.minusDays(1));
        ordersMapper.insert(completedYesterday);

        Orders pending = new Orders();
        pending.setOrderNo("ORD-DASHBOARD-PENDING");
        pending.setUserId(10001L);
        pending.setMerchantId(20001L);
        pending.setType("delivery");
        pending.setTotalAmount(new BigDecimal("20.00"));
        pending.setActualAmount(new BigDecimal("20.00"));
        pending.setDeliveryFee(new BigDecimal("5.00"));
        pending.setDiscount(BigDecimal.ZERO);
        pending.setStatus("pending_accept");
        pending.setAddressDetail("Pending Address");
        ordersMapper.insert(pending);

        DashboardVO.MerchantData merchantDashboard = dashboardService.getMerchantData(20001L);
        assertEquals(3, merchantDashboard.getTodayOrders());
        assertEquals(1, merchantDashboard.getPendingOrders());
        assertEquals(56.0, merchantDashboard.getTodayRevenue());

        DashboardVO.RiderData riderDashboard = dashboardService.getRiderData(40001L);
        assertEquals(1, riderDashboard.getTodayDeliveries());
        assertEquals(6.0, riderDashboard.getTodayEarnings());
        assertEquals("active", riderDashboard.getStatus());
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
