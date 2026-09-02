package com.example.demo;

import com.example.demo.common.JwtUtil;
import com.example.demo.engagement.client.FulfillmentClient;
import com.example.demo.engagement.client.MerchantCatalogClient;
import com.example.demo.engagement.client.OrderClient;
import com.example.demo.engagement.client.UserClient;
import com.example.demo.engagement.event.EngagementEventPublisher;
import com.example.demo.review.entity.Review;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/engagement-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EngagementServiceApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private OrderClient orderClient;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private MerchantCatalogClient merchantCatalogClient;

    @MockitoBean
    private FulfillmentClient fulfillmentClient;

    @MockitoBean
    private EngagementEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        when(userClient.getUser(10001L)).thenReturn(user(10001L));
        when(merchantCatalogClient.getMerchant(20001L)).thenReturn(merchant());
        when(merchantCatalogClient.getProduct(30001L)).thenReturn(product(30001L));
        when(fulfillmentClient.getRider(40001L)).thenReturn(rider());
        when(orderClient.getOrder(70001L)).thenReturn(order(70001L, "completed", List.of(30001L)));
        when(orderClient.getParticipantOrder(70001L, 10001L, "user")).thenReturn(order(70001L, "completed", List.of(30001L)));
        when(orderClient.getParticipantOrder(70001L, 20001L, "merchant")).thenReturn(order(70001L, "completed", List.of(30001L)));
        when(orderClient.getParticipantOrder(70001L, 40001L, "rider")).thenReturn(order(70001L, "completed", List.of(30001L)));
        when(orderClient.getParticipantOrder(70002L, 10001L, "user")).thenReturn(order(70002L, "completed", List.of(30001L)));
    }

    @Test
    void submitReviewCreatesReviewsAndMarksOrderItemsReviewed() throws Exception {
        String body = """
                {
                  "orderId": 70001,
                  "items": [
                    {"productId": 30001, "rating": 5, "content": "Tasty", "images": ["/uploads/reviews/a.png"]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].rating").value(5))
                .andExpect(jsonPath("$.data[0].userName").value("Demo User"))
                .andExpect(jsonPath("$.data[0].productName").value("Braised Pork Rice"));

        verify(eventPublisher).publishReviewCreated(anyList());
        verify(orderClient).markReviewedItems(eq(70001L), eq(List.of(30001L)));
    }

    @Test
    void submitReviewRejectsDuplicateOrderReview() throws Exception {
        String body = """
                {
                  "orderId": 70002,
                  "items": [{"productId": 30001, "rating": 4, "content": "Again"}]
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该订单已评价，不可重复评价"));
    }

    @Test
    void submitReviewRejectsUnfinishedOrder() throws Exception {
        when(orderClient.getParticipantOrder(70003L, 10001L, "user"))
                .thenReturn(order(70003L, "delivering", List.of(30001L)));
        String body = """
                {
                  "orderId": 70003,
                  "items": [{"productId": 30001, "rating": 5}]
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只有已完成订单才能评价"));
    }

    @Test
    void submitReviewRejectsProductOutsideOrder() throws Exception {
        String body = """
                {
                  "orderId": 70001,
                  "items": [{"productId": 39999, "rating": 5}]
                }
                """;

        mockMvc.perform(post("/api/reviews")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("商品ID 39999 不属于该订单"));
    }

    @Test
    void getProductReviewsEnrichesUserAndProductSnapshots() throws Exception {
        mockMvc.perform(get("/api/products/30001/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].content").value("Already reviewed"))
                .andExpect(jsonPath("$.data[0].userName").value("Demo User"))
                .andExpect(jsonPath("$.data[0].productName").value("Braised Pork Rice"));
    }

    @Test
    void getMerchantReviewsRatingAndCurrentUserReviews() throws Exception {
        mockMvc.perform(get("/api/merchants/20001/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].merchantId").value(20001))
                .andExpect(jsonPath("$.data[0].content").value("Already reviewed"));

        mockMvc.perform(get("/api/merchants/20001/rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(4.0));

        mockMvc.perform(get("/api/user/reviews").header(HttpHeaders.AUTHORIZATION, consumerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].userId").value(10001))
                .andExpect(jsonPath("$.data[0].productName").value("Braised Pork Rice"));
    }

    @Test
    void uploadImagesAcceptsImagesAndRejectsNonImagesAndOversizedFiles() throws Exception {
        MockMultipartFile image = new MockMultipartFile("files", "meal.png", "image/png", "image".getBytes());

        mockMvc.perform(multipart("/api/uploads/images")
                        .file(image)
                        .param("scene", "chat")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0]").value(org.hamcrest.Matchers.startsWith("/uploads/chat/")));

        MockMultipartFile text = new MockMultipartFile("files", "note.txt", "text/plain", "not image".getBytes());
        mockMvc.perform(multipart("/api/uploads/images")
                        .file(text)
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只能上传图片文件"));

        MockMultipartFile oversized = new MockMultipartFile("files", "large.png", "image/png", new byte[1024 * 1024 + 1]);
        mockMvc.perform(multipart("/api/uploads/images")
                        .file(oversized)
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("单张图片不能超过1MB"));
    }

    @Test
    void uploadedImagesAreServedWithHttpCachingHeaders() throws Exception {
        MockMultipartFile image = new MockMultipartFile("files", "meal.png", "image/png", "image".getBytes());

        MvcResult upload = mockMvc.perform(multipart("/api/uploads/images")
                        .file(image)
                        .param("scene", "chat")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L)))
                .andExpect(status().isOk())
                .andReturn();

        String imageUrl = JsonPath.read(upload.getResponse().getContentAsString(), "$.data[0]");
        MvcResult read = mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=86400")))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();

        String etag = read.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get(imageUrl).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void sendMessageRequiresValidOrderParticipantAndPublishesEvent() throws Exception {
        String body = """
                {
                  "receiverId": 20001,
                  "receiverType": "merchant",
                  "orderId": 70001,
                  "content": "Please add napkins"
                }
                """;

        mockMvc.perform(post("/api/messages")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.senderId").value("10001"))
                .andExpect(jsonPath("$.data.receiverId").value("20001"))
                .andExpect(jsonPath("$.data.content").value("Please add napkins"));

        verify(eventPublisher).publishMessageSent(any(), eq(70001L), eq(20001L), eq("merchant"));
    }

    @Test
    void merchantCanReplyToUserInOrderConversation() throws Exception {
        String body = """
                {
                  "receiverId": 10001,
                  "receiverType": "user",
                  "orderId": 70001,
                  "content": "We are preparing your meal"
                }
                """;

        mockMvc.perform(post("/api/messages")
                        .header(HttpHeaders.AUTHORIZATION, merchantToken(20001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.senderType").value("merchant"))
                .andExpect(jsonPath("$.data.receiverType").value("user"))
                .andExpect(jsonPath("$.data.content").value("We are preparing your meal"));
    }

    @Test
    void riderCanMessageOrderUserWhenRiderIsParticipant() throws Exception {
        String body = """
                {
                  "receiverId": 10001,
                  "receiverType": "user",
                  "orderId": 70001,
                  "content": "I am on the way"
                }
                """;

        mockMvc.perform(post("/api/messages")
                        .header(HttpHeaders.AUTHORIZATION, riderToken(40001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.senderType").value("rider"))
                .andExpect(jsonPath("$.data.receiverType").value("user"))
                .andExpect(jsonPath("$.data.content").value("I am on the way"));
    }

    @Test
    void sendMessageRejectsReceiverOutsideOrderParticipants() throws Exception {
        MerchantCatalogClient.MerchantSnapshot unrelated = merchant();
        unrelated.setId(29999L);
        unrelated.setName("Other Merchant");
        when(merchantCatalogClient.getMerchant(29999L)).thenReturn(unrelated);

        String body = """
                {
                  "receiverId": 29999,
                  "receiverType": "merchant",
                  "orderId": 70001,
                  "content": "hello"
                }
                """;

        mockMvc.perform(post("/api/messages")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("接收方与订单无关"));
    }

    @Test
    void sendMessageRejectsMissingOrderIdAndSelfSend() throws Exception {
        String missingOrder = """
                {
                  "receiverId": 20001,
                  "receiverType": "merchant",
                  "content": "hello"
                }
                """;

        mockMvc.perform(post("/api/messages")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingOrder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择关联订单"));

        String selfSend = """
                {
                  "receiverId": 10001,
                  "receiverType": "user",
                  "orderId": 70001,
                  "content": "hello"
                }
                """;

        mockMvc.perform(post("/api/messages")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfSend))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能给自己发送消息"));
    }

    @Test
    void threadsReportUnreadMessageAndTargetSnapshot() throws Exception {
        mockMvc.perform(get("/api/messages/threads").header(HttpHeaders.AUTHORIZATION, consumerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].targetId").value("20001"))
                .andExpect(jsonPath("$.data[0].targetType").value("merchant"))
                .andExpect(jsonPath("$.data[0].targetName").value("Campus Kitchen"))
                .andExpect(jsonPath("$.data[0].orderNo").value("ORD70001"))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1));
    }

    @Test
    void readingMessagesMarksUnreadConversationAsRead() throws Exception {
        mockMvc.perform(get("/api/messages/unread-count").header(HttpHeaders.AUTHORIZATION, consumerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(get("/api/messages")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken(10001L))
                        .param("targetId", "20001")
                        .param("targetType", "merchant")
                        .param("orderId", "70001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].content").value("Ready soon"))
                .andExpect(jsonPath("$.data[0].isRead").value(true));

        mockMvc.perform(get("/api/messages/unread-count").header(HttpHeaders.AUTHORIZATION, consumerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }

    private String consumerToken(Long userId) {
        return "Bearer " + jwtUtil.generateToken(userId, "consumer", "user" + userId);
    }

    private String merchantToken(Long merchantId) {
        return "Bearer " + jwtUtil.generateToken(merchantId, "merchant", "merchant" + merchantId);
    }

    private String riderToken(Long riderId) {
        return "Bearer " + jwtUtil.generateToken(riderId, "rider", "rider" + riderId);
    }

    private UserClient.UserSnapshot user(Long id) {
        UserClient.UserSnapshot user = new UserClient.UserSnapshot();
        user.setId(id);
        user.setUsername("demo");
        user.setNickname("Demo User");
        user.setAvatar("/avatar.png");
        user.setStatus("active");
        return user;
    }

    private MerchantCatalogClient.MerchantSnapshot merchant() {
        MerchantCatalogClient.MerchantSnapshot merchant = new MerchantCatalogClient.MerchantSnapshot();
        merchant.setId(20001L);
        merchant.setName("Campus Kitchen");
        merchant.setAvatar("/merchant.png");
        return merchant;
    }

    private MerchantCatalogClient.ProductSnapshot product(Long id) {
        MerchantCatalogClient.ProductSnapshot product = new MerchantCatalogClient.ProductSnapshot();
        product.setId(id);
        product.setName("Braised Pork Rice");
        product.setImage("/product.png");
        return product;
    }

    private FulfillmentClient.RiderSnapshot rider() {
        FulfillmentClient.RiderSnapshot rider = new FulfillmentClient.RiderSnapshot();
        rider.setId(40001L);
        rider.setName("rider01");
        rider.setPhone("13800138004");
        rider.setStatus("active");
        return rider;
    }

    private OrderClient.OrderSnapshot order(Long orderId, String status, List<Long> productIds) {
        OrderClient.OrderSnapshot order = new OrderClient.OrderSnapshot();
        order.setId(orderId);
        order.setOrderNo("ORD" + orderId);
        order.setUserId(10001L);
        order.setMerchantId(20001L);
        order.setRiderId(40001L);
        order.setStatus(status);
        order.setTotal(new BigDecimal("32.00"));
        order.setItems(productIds.stream().map(this::orderItem).toList());
        return order;
    }

    private OrderClient.OrderItemSnapshot orderItem(Long productId) {
        OrderClient.OrderItemSnapshot item = new OrderClient.OrderItemSnapshot();
        item.setProductId(productId);
        item.setName("Braised Pork Rice");
        item.setPrice(new BigDecimal("22.00"));
        item.setQuantity(1);
        item.setImage("/product.png");
        item.setReviewed(false);
        return item;
    }
}
