package com.example.demo;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import com.example.demo.common.contract.merchant.StockChangeResponse;
import com.example.demo.common.contract.settlement.CouponLockResponse;
import com.example.demo.order.client.MerchantCatalogClient;
import com.example.demo.order.client.MerchantProductClient;
import com.example.demo.order.client.SettlementCouponClient;
import com.example.demo.order.client.UserClient;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.dto.CheckoutRequest.CheckoutItem;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.OrderCompensationRecord;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrderCompensationMapper;
import com.example.demo.order.mapper.OrderItemMapper;
import com.example.demo.order.mapper.OrdersMapper;
import com.example.demo.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OrderServiceInternalApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderCompensationMapper orderCompensationMapper;

    @org.springframework.boot.test.mock.mockito.MockBean
    private MerchantCatalogClient merchantCatalogClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private MerchantProductClient merchantProductClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private SettlementCouponClient settlementCouponClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private UserClient userClient;

    @BeforeEach
    void setUpMerchantSnapshot() {
        MerchantCatalogClient.MerchantSnapshot merchant = new MerchantCatalogClient.MerchantSnapshot();
        merchant.setId(20001L);
        merchant.setName("Campus Kitchen");
        merchant.setAvatar("/oss/life-assistant/demo/merchants/campus-kitchen.png");
        merchant.setStatus("active");
        merchant.setMinDeliveryFee(new BigDecimal("20.00"));
        merchant.setDeliveryFee(new BigDecimal("5.00"));
        lenient().when(merchantCatalogClient.getMerchant(20001L)).thenReturn(merchant);
    }

    @Test
    void getInternalOrderReturnsSnapshotAndItems() throws Exception {
        mockMvc.perform(get("/internal/orders/70002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("pending_payment"))
                .andExpect(jsonPath("$.data.items[0].name").value("Braised Pork Rice"));
    }

    @Test
    void markPaidMovesPendingPaymentOrderToPendingAccept() throws Exception {
        String body = """
                {
                  "transactionId": "pay-70001",
                  "payMethod": "mock",
                  "amount": 27.00
                }
                """;

        mockMvc.perform(post("/internal/orders/70001/mark-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("pending_accept"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());
    }

    @Test
    void markPaidRejectsAmountMismatch() throws Exception {
        String body = """
                {
                  "transactionId": "pay-70002",
                  "payMethod": "mock",
                  "amount": 1.00
                }
                """;

        mockMvc.perform(post("/internal/orders/70002/mark-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("支付金额与订单金额不一致"));
    }

    @Test
    void checkoutReleasesInventoryAndRecordsCompensationWhenCouponLockFails() {
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

        StockChangeResponse reserveResponse = new StockChangeResponse();
        reserveResponse.setSuccess(true);
        reserveResponse.setStatus("reserved");
        when(merchantProductClient.quote(any())).thenReturn(Result.success(quoteResponse));
        when(merchantProductClient.reserve(any())).thenReturn(reserveResponse);
        StockChangeResponse releaseResponse = new StockChangeResponse();
        releaseResponse.setSuccess(true);
        releaseResponse.setStatus("released");
        when(merchantProductClient.release(any())).thenReturn(releaseResponse);
        when(merchantProductClient.getChangeStatus(any())).thenReturn(reserveResponse);
        when(settlementCouponClient.lock(any())).thenThrow(BusinessException.badRequest("优惠券锁定失败"));

        long beforeOrders = ordersMapper.selectCount(null);
        long beforeItems = orderItemMapper.selectCount(null);
        long beforeCompensations = orderCompensationMapper.selectCount(null);

        CheckoutRequest request = new CheckoutRequest();
        request.setMerchantId(20001L);
        request.setAddress("No. 3 Dorm");
        request.setCouponId(60001L);
        CheckoutItem item = new CheckoutItem();
        item.setProductId(30001L);
        item.setQuantity(1);
        request.setItems(List.of(item));

        assertThatThrownBy(() -> orderService.checkout(10001L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("优惠券锁定失败");

        assertThat(ordersMapper.selectCount(null)).isEqualTo(beforeOrders);
        assertThat(orderItemMapper.selectCount(null)).isEqualTo(beforeItems);
        assertThat(orderCompensationMapper.selectCount(null)).isEqualTo(beforeCompensations + 1);

        OrderCompensationRecord record = orderCompensationMapper.selectList(null).stream()
                .filter(r -> "release_inventory".equals(r.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(record.getStatus()).isEqualTo("resolved");
        assertThat(record.getTargetService()).isEqualTo("merchant-service");
        assertThat(record.getMessage()).contains("库存已回滚");
    }

    @Test
    void checkoutRejectsWhenCouponLockResponseIsNotConfirmed() {
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

        StockChangeResponse reserveResponse = new StockChangeResponse();
        reserveResponse.setSuccess(true);
        reserveResponse.setStatus("reserved");
        when(merchantProductClient.quote(any())).thenReturn(Result.success(quoteResponse));
        when(merchantProductClient.reserve(any())).thenReturn(reserveResponse);

        StockChangeResponse releaseResponse = new StockChangeResponse();
        releaseResponse.setSuccess(true);
        releaseResponse.setStatus("released");
        when(merchantProductClient.getChangeStatus(any())).thenReturn(reserveResponse);
        when(merchantProductClient.release(any())).thenReturn(releaseResponse);

        CouponLockResponse couponLockResponse = new CouponLockResponse();
        couponLockResponse.setLocked(true);
        couponLockResponse.setStatus("processing");
        couponLockResponse.setMessage("优惠券锁定处理中");
        when(settlementCouponClient.lock(any())).thenReturn(couponLockResponse);

        long beforeOrders = ordersMapper.selectCount(null);
        long beforeCompensations = orderCompensationMapper.selectCount(null);

        CheckoutRequest request = new CheckoutRequest();
        request.setMerchantId(20001L);
        request.setAddress("No. 3 Dorm");
        request.setCouponId(60001L);
        CheckoutItem item = new CheckoutItem();
        item.setProductId(30001L);
        item.setQuantity(1);
        request.setItems(List.of(item));

        assertThatThrownBy(() -> orderService.checkout(10001L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("优惠券锁定处理中");

        assertThat(ordersMapper.selectCount(null)).isEqualTo(beforeOrders);
        assertThat(orderCompensationMapper.selectCount(null)).isEqualTo(beforeCompensations + 1);
        assertThat(orderCompensationMapper.selectList(null)).anyMatch(record ->
                "release_inventory".equals(record.getAction())
                        && "resolved".equals(record.getStatus())
                        && record.getMessage().contains("库存已回滚"));
    }

    @Test
    void checkoutRejectsWhenInventoryResponseIsStillProcessing() {
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

        StockChangeResponse reserveResponse = new StockChangeResponse();
        reserveResponse.setSuccess(false);
        reserveResponse.setStatus("processing");
        reserveResponse.setMessage("库存变更处理中");
        when(merchantProductClient.quote(any())).thenReturn(Result.success(quoteResponse));
        when(merchantProductClient.reserve(any())).thenReturn(reserveResponse);
        when(merchantProductClient.getChangeStatus(any())).thenReturn(reserveResponse);

        long beforeOrders = ordersMapper.selectCount(null);
        long beforeCompensations = orderCompensationMapper.selectCount(null);

        CheckoutRequest request = new CheckoutRequest();
        request.setMerchantId(20001L);
        request.setAddress("No. 3 Dorm");
        CheckoutItem item = new CheckoutItem();
        item.setProductId(30001L);
        item.setQuantity(1);
        request.setItems(List.of(item));

        assertThatThrownBy(() -> orderService.checkout(10001L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存变更处理中");

        assertThat(ordersMapper.selectCount(null)).isEqualTo(beforeOrders);
        assertThat(orderCompensationMapper.selectCount(null)).isEqualTo(beforeCompensations + 1);
        assertThat(orderCompensationMapper.selectList(null)).anyMatch(record ->
                "release_inventory".equals(record.getAction())
                        && "pending".equals(record.getStatus())
                        && record.getMessage().contains("未确认到已预留库存"));
    }

    @Test
    void cancelOrderReleasesInventoryAndKeepsCancellationWhenCouponReleaseFails() {
        Orders order = new Orders();
        order.setOrderNo("ORDTESTCANCEL001");
        order.setUserId(10001L);
        order.setMerchantId(20001L);
        order.setType("delivery");
        order.setTotalAmount(new BigDecimal("27.00"));
        order.setActualAmount(new BigDecimal("27.00"));
        order.setDeliveryFee(new BigDecimal("5.00"));
        order.setDiscount(BigDecimal.ZERO);
        order.setStatus("pending_payment");
        order.setCouponId(60001L);
        order.setStockReserved(true);
        order.setAddressDetail("No. 4 Dorm");
        ordersMapper.insert(order);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setProductId(30001L);
        orderItem.setName("Braised Pork Rice");
        orderItem.setPrice(new BigDecimal("22.00"));
        orderItem.setQuantity(1);
        orderItem.setImage("/oss/life-assistant/demo/products/braised-pork-rice.png");
        orderItem.setSubtotal(new BigDecimal("22.00"));
        orderItem.setReviewed(false);
        orderItemMapper.insert(orderItem);

        StockChangeResponse releaseResponse = new StockChangeResponse();
        releaseResponse.setSuccess(true);
        releaseResponse.setStatus("released");
        when(merchantProductClient.release(any())).thenReturn(releaseResponse);
        when(settlementCouponClient.release(order.getId())).thenThrow(BusinessException.badRequest("优惠券释放失败"));

        long beforeCompensations = orderCompensationMapper.selectCount(null);

        orderService.cancelOrder(10001L, order.getId());

        Orders updated = ordersMapper.selectById(order.getId());
        assertThat(updated.getStatus()).isEqualTo("cancelled");
        assertThat(updated.getStockReserved()).isFalse();
        assertThat(orderCompensationMapper.selectCount(null)).isEqualTo(beforeCompensations + 1);
        assertThat(orderCompensationMapper.selectList(null)).anyMatch(record ->
                "release_coupon".equals(record.getAction()) && "settlement-service".equals(record.getTargetService()));
    }
}
