package com.example.demo.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.BusinessException;
import com.example.demo.common.contract.merchant.ProductQuoteRequest;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import com.example.demo.common.contract.order.MarkPaidRequest;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.common.contract.settlement.CouponLockRequest;
import com.example.demo.common.contract.settlement.CouponLockResponse;
import com.example.demo.order.client.MerchantCatalogClient;
import com.example.demo.order.client.SettlementCouponClient;
import com.example.demo.order.client.UserClient;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.dto.MerchantOrderUpdateRequest;
import com.example.demo.order.dto.OrderVO;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrderItemMapper;
import com.example.demo.order.mapper.OrdersMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    public static final String STATUS_PENDING_PAYMENT = "pending_payment";
    public static final String STATUS_PENDING_ACCEPT = "pending_accept";
    public static final String STATUS_DELIVERING = "delivering";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_PENDING_USE = "pending_use";

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderCheckoutDraftService orderCheckoutDraftService;
    private final MerchantCatalogClient merchantCatalogClient;
    private final SettlementCouponClient settlementCouponClient;
    private final UserClient userClient;

    public List<OrderVO> getUserOrders(Long userId) {
        List<Orders> orders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getUserId, userId)
                        .orderByDesc(Orders::getCreateTime)
        );
        return orders.stream().map(this::toOrderVO).collect(Collectors.toList());
    }

    public OrderVO getOrderDetail(Long userId, Long orderId) {
        Orders order = findUserOrder(userId, orderId);
        return toOrderVO(order);
    }

    @Transactional
    public OrderVO checkout(Long userId, CheckoutRequest request) {
        if (request == null || request.getMerchantId() == null) {
            throw BusinessException.badRequest("请选择商家");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw BusinessException.badRequest("订单商品不能为空");
        }
        if (request.getAddress() == null || request.getAddress().isBlank()) {
            throw BusinessException.badRequest("收货地址不能为空");
        }

        MerchantCatalogClient.MerchantSnapshot merchant = merchantCatalogClient.getMerchant(request.getMerchantId());
        if (merchant == null || !"active".equals(merchant.getStatus())) {
            throw BusinessException.notFound("商家不存在或不可下单");
        }

        ProductQuoteResponse quote = orderCheckoutDraftService.quoteProducts(toQuoteRequest(request));
        BigDecimal goodsAmount = quote.getTotalAmount() == null ? BigDecimal.ZERO : quote.getTotalAmount();
        BigDecimal minOrderAmount = merchant.getMinDeliveryFee() == null ? BigDecimal.ZERO : merchant.getMinDeliveryFee();
        if (goodsAmount.compareTo(minOrderAmount) < 0) {
            throw BusinessException.badRequest("未达到商家起送金额");
        }

        BigDecimal deliveryFee = merchant.getDeliveryFee() == null ? BigDecimal.ZERO : merchant.getDeliveryFee();
        BigDecimal totalAmount = goodsAmount.add(deliveryFee);

        Orders order = new Orders();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setMerchantId(request.getMerchantId());
        order.setType("delivery");
        order.setTotalAmount(totalAmount);
        order.setActualAmount(totalAmount);
        order.setDeliveryFee(deliveryFee);
        order.setDiscount(BigDecimal.ZERO);
        order.setStatus(STATUS_PENDING_PAYMENT);
        order.setAddressDetail(request.getAddress().trim());
        ordersMapper.insert(order);

        for (ProductQuoteResponse.Item item : quote.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(item.getProductId());
            orderItem.setName(item.getName());
            orderItem.setPrice(item.getUnitPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setImage(item.getImage());
            orderItem.setSpecLabel(item.getSpecLabel());
            orderItem.setSubtotal(item.getSubtotal());
            orderItem.setReviewed(false);
            orderItemMapper.insert(orderItem);
        }

        if (request.getCouponId() != null) {
            CouponLockResponse coupon = settlementCouponClient.lock(toCouponLockRequest(userId, request.getCouponId(), order.getId(), totalAmount));
            BigDecimal discount = coupon == null || coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
            if (discount.compareTo(totalAmount) > 0) {
                discount = totalAmount;
            }
            order.setCouponId(request.getCouponId());
            order.setDiscount(discount);
            order.setActualAmount(totalAmount.subtract(discount));
            ordersMapper.updateById(order);
        }

        clearCartBestEffort(userId, request.getMerchantId());
        return toOrderVO(order);
    }

    @Transactional
    public OrderVO cancelOrder(Long userId, Long orderId) {
        Orders order = findUserOrder(userId, orderId);
        if (!STATUS_PENDING_PAYMENT.equals(order.getStatus()) && !STATUS_PENDING_ACCEPT.equals(order.getStatus())) {
            throw BusinessException.badRequest("当前订单状态不允许取消");
        }

        order.setStatus(STATUS_CANCELLED);
        ordersMapper.updateById(order);
        if (order.getCouponId() != null) {
            settlementCouponClient.release(order.getId());
        }
        return toOrderVO(order);
    }

    @Transactional
    public OrderVO completeOrder(Long userId, Long orderId) {
        Orders order = findUserOrder(userId, orderId);
        if (!STATUS_DELIVERING.equals(order.getStatus())) {
            throw BusinessException.badRequest("当前订单状态不允许确认收货");
        }
        completeOrderState(order);
        return toOrderVO(order);
    }

    public List<OrderVO> getMerchantOrders(Long merchantId) {
        requireActiveMerchant(merchantId);
        List<Orders> orders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getMerchantId, merchantId)
                        .orderByDesc(Orders::getCreateTime)
        );
        return orders.stream().map(this::toOrderVO).collect(Collectors.toList());
    }

    @Transactional
    public OrderVO updateMerchantOrder(Long merchantId, Long orderId, MerchantOrderUpdateRequest request) {
        requireActiveMerchant(merchantId);
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getId, orderId)
                        .eq(Orders::getMerchantId, merchantId)
                        .last("limit 1")
        );
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }

        String newStatus = normalizeStatusCode(request == null ? null : request.getStatus());
        String currentStatus = order.getStatus();
        boolean validTransition = STATUS_PENDING_ACCEPT.equals(currentStatus) && STATUS_DELIVERING.equals(newStatus)
                || STATUS_PENDING_ACCEPT.equals(currentStatus) && STATUS_COMPLETED.equals(newStatus)
                || STATUS_DELIVERING.equals(currentStatus) && STATUS_COMPLETED.equals(newStatus);
        if (!validTransition) {
            throw BusinessException.badRequest("非法的订单状态变更");
        }

        order.setStatus(newStatus);
        if (STATUS_COMPLETED.equals(newStatus)) {
            order.setCompletedAt(LocalDateTime.now());
            if (order.getCouponId() != null) {
                settlementCouponClient.confirm(order.getId());
            }
        }
        ordersMapper.updateById(order);
        return toOrderVO(order);
    }

    public IPage<Orders> listOrders(int page, int pageSize, String keyword, String status, String type) {
        Page<Orders> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(status != null && !status.isBlank(), Orders::getStatus, status)
                .eq(type != null && !type.isBlank(), Orders::getType, type)
                .like(keyword != null && !keyword.isBlank(), Orders::getOrderNo, keyword)
                .orderByDesc(Orders::getCreateTime);
        return ordersMapper.selectPage(pageParam, wrapper);
    }

    public Orders getAdminOrderDetail(Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return order;
    }

    public OrderInternalResponse getOrder(Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return toInternalResponse(order);
    }

    public OrderInternalResponse getParticipantOrder(Long orderId, Long participantId, String participantType) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!isOrderParticipant(order, participantId, participantType)) {
            throw BusinessException.forbidden("无权访问该订单详情");
        }
        return toInternalResponse(order);
    }

    public List<OrderInternalResponse> getAvailableFulfillmentTasks() {
        return ordersMapper.selectList(
                        new LambdaQueryWrapper<Orders>()
                                .eq(Orders::getStatus, STATUS_PENDING_ACCEPT)
                                .isNull(Orders::getRiderId)
                                .orderByAsc(Orders::getPaidAt)
                ).stream()
                .map(this::toInternalResponse)
                .collect(Collectors.toList());
    }

    public List<OrderInternalResponse> getAssignedFulfillmentTasks(Long riderId) {
        return ordersMapper.selectList(
                        new LambdaQueryWrapper<Orders>()
                                .eq(Orders::getRiderId, riderId)
                                .eq(Orders::getStatus, STATUS_DELIVERING)
                                .orderByDesc(Orders::getUpdateTime)
                ).stream()
                .map(this::toInternalResponse)
                .collect(Collectors.toList());
    }

    public List<OrderInternalResponse> getCompletedFulfillmentTasks(Long riderId) {
        return ordersMapper.selectList(
                        new LambdaQueryWrapper<Orders>()
                                .eq(Orders::getRiderId, riderId)
                                .eq(Orders::getStatus, STATUS_COMPLETED)
                                .orderByDesc(Orders::getCompletedAt)
                ).stream()
                .map(this::toInternalResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderInternalResponse assignRider(Long orderId, Long riderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!STATUS_PENDING_ACCEPT.equals(order.getStatus()) || order.getRiderId() != null) {
            throw BusinessException.badRequest("订单不可接单");
        }
        order.setRiderId(riderId);
        order.setStatus(STATUS_DELIVERING);
        ordersMapper.updateById(order);
        return toInternalResponse(order);
    }

    @Transactional
    public OrderInternalResponse markDelivered(Long orderId, Long riderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!riderId.equals(order.getRiderId())) {
            throw BusinessException.forbidden("无权操作该订单");
        }
        if (!STATUS_DELIVERING.equals(order.getStatus())) {
            throw BusinessException.badRequest("当前订单状态不允许送达");
        }
        completeOrderState(order);
        return toInternalResponse(order);
    }

    public OrderInternalResponse getFulfillmentTask(Long orderId, Long riderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!riderId.equals(order.getRiderId())) {
            throw BusinessException.forbidden("无权查看该任务");
        }
        return toInternalResponse(order);
    }

    @Transactional
    public void markReviewedItems(Long orderId, List<Long> productIds) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
                        .in(OrderItem::getProductId, productIds)
        );
        for (OrderItem item : items) {
            item.setReviewed(true);
            orderItemMapper.updateById(item);
        }
    }

    @Transactional
    public OrderInternalResponse markPaid(Long orderId, MarkPaidRequest request) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (STATUS_PENDING_ACCEPT.equals(order.getStatus()) && order.getPaidAt() != null) {
            return toInternalResponse(order);
        }
        if (!STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            throw BusinessException.badRequest("当前订单状态不允许支付确认");
        }
        if (request.getAmount().compareTo(order.getActualAmount()) != 0) {
            throw BusinessException.badRequest("支付金额与订单金额不一致");
        }

        order.setStatus(STATUS_PENDING_ACCEPT);
        order.setPaidAt(request.getPaidAt() != null ? request.getPaidAt() : LocalDateTime.now());
        ordersMapper.updateById(order);
        return toInternalResponse(order);
    }

    private Orders findUserOrder(Long userId, Long orderId) {
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getId, orderId)
                        .eq(Orders::getUserId, userId)
                        .last("limit 1")
        );
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return order;
    }

    private void completeOrderState(Orders order) {
        order.setStatus(STATUS_COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        ordersMapper.updateById(order);
        if (order.getCouponId() != null) {
            settlementCouponClient.confirm(order.getId());
        }
    }

    private void requireActiveMerchant(Long merchantId) {
        MerchantCatalogClient.MerchantSnapshot merchant = merchantCatalogClient.getMerchant(merchantId);
        if (merchant == null) {
            throw BusinessException.notFound("商家不存在");
        }
        if (!"active".equals(merchant.getStatus())) {
            throw BusinessException.forbidden("商家账号审核通过后才能使用该功能");
        }
    }

    private ProductQuoteRequest toQuoteRequest(CheckoutRequest request) {
        ProductQuoteRequest quoteRequest = new ProductQuoteRequest();
        quoteRequest.setRequestId(UUID.randomUUID().toString());
        quoteRequest.setMerchantId(request.getMerchantId());
        quoteRequest.setItems(request.getItems().stream().map(item -> {
            ProductQuoteRequest.Item quoteItem = new ProductQuoteRequest.Item();
            quoteItem.setProductId(item.getProductId());
            quoteItem.setSpecLabel(normalizeSpecLabel(item.getSpecLabel()));
            quoteItem.setQuantity(item.getQuantity());
            return quoteItem;
        }).collect(Collectors.toList()));
        return quoteRequest;
    }

    private CouponLockRequest toCouponLockRequest(Long userId, Long couponId, Long orderId, BigDecimal orderAmount) {
        CouponLockRequest lockRequest = new CouponLockRequest();
        lockRequest.setRequestId(UUID.randomUUID().toString());
        lockRequest.setUserId(userId);
        lockRequest.setCouponId(couponId);
        lockRequest.setOrderId(orderId);
        lockRequest.setOrderAmount(orderAmount);
        return lockRequest;
    }

    private void clearCartBestEffort(Long userId, Long merchantId) {
        try {
            userClient.clearCartByMerchant(userId, merchantId);
        } catch (RuntimeException ignored) {
            // 下单已完成，购物车清理失败不阻断主链路。
        }
    }

    private String normalizeStatusCode(String status) {
        if (status == null) {
            return null;
        }
        return switch (status.trim()) {
            case "待支付" -> STATUS_PENDING_PAYMENT;
            case "待取餐", "待接单" -> STATUS_PENDING_ACCEPT;
            case "配送中" -> STATUS_DELIVERING;
            case "已完成" -> STATUS_COMPLETED;
            case "已取消" -> STATUS_CANCELLED;
            case "待使用" -> STATUS_PENDING_USE;
            default -> status.trim();
        };
    }

    private String normalizeSpecLabel(String specLabel) {
        if (specLabel == null) {
            return null;
        }
        String trimmed = specLabel.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.replaceFirst("\\s*\\(\\+\\s*(?:￥|¥)?\\s*\\d+(?:\\.\\d+)?\\)\\s*$", "").trim();
    }

    private boolean isOrderParticipant(Orders order, Long participantId, String participantType) {
        if (order == null || participantId == null || participantType == null) {
            return false;
        }
        return switch (participantType) {
            case "user" -> participantId.equals(order.getUserId());
            case "merchant" -> participantId.equals(order.getMerchantId());
            case "rider" -> participantId.equals(order.getRiderId());
            default -> false;
        };
    }

    private String generateOrderNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "ORD" + datePart + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private OrderVO toOrderVO(Orders order) {
        MerchantCatalogClient.MerchantSnapshot merchant = null;
        if (order.getMerchantId() != null) {
            merchant = merchantCatalogClient.getMerchant(order.getMerchantId());
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        List<OrderVO.OrderItemVO> itemVOs = items.stream()
                .map(item -> OrderVO.OrderItemVO.builder()
                        .productId(item.getProductId())
                        .name(item.getName())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .image(item.getImage())
                        .specLabel(item.getSpecLabel())
                        .reviewed(Boolean.TRUE.equals(item.getReviewed()))
                        .build())
                .collect(Collectors.toList());
        List<String> reviewedProductIds = items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getReviewed()))
                .map(i -> String.valueOf(i.getProductId()))
                .collect(Collectors.toList());

        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .merchantId(order.getMerchantId())
                .merchant(merchant == null ? "" : merchant.getName())
                .merchantAvatar(merchant == null ? "" : merchant.getAvatar())
                .status(mapStatus(order.getStatus()))
                .total(order.getActualAmount())
                .deliveryFee(order.getDeliveryFee())
                .discount(order.getDiscount())
                .eta("预计30分钟送达")
                .createdAt(order.getCreateTime())
                .paidAt(order.getPaidAt())
                .riderId(order.getRiderId())
                .address(order.getAddressDetail())
                .items(itemVOs)
                .reviewedProductIds(reviewedProductIds)
                .timeline(buildTimeline(order))
                .build();
    }

    private List<OrderVO.TimelineItem> buildTimeline(Orders order) {
        List<OrderVO.TimelineItem> timeline = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        if (order.getCreateTime() != null) {
            timeline.add(OrderVO.TimelineItem.builder().label("已下单").time(order.getCreateTime().format(formatter)).build());
        }
        if (order.getPaidAt() != null) {
            timeline.add(OrderVO.TimelineItem.builder().label("已支付").time(order.getPaidAt().format(formatter)).build());
        }
        if (order.getCompletedAt() != null) {
            timeline.add(OrderVO.TimelineItem.builder().label("已完成").time(order.getCompletedAt().format(formatter)).build());
        }
        if (STATUS_CANCELLED.equals(order.getStatus())) {
            timeline.add(OrderVO.TimelineItem.builder().label("已取消").time(LocalDateTime.now().format(formatter)).build());
        }
        return timeline;
    }

    private OrderInternalResponse toInternalResponse(Orders order) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        OrderInternalResponse response = new OrderInternalResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setMerchantId(order.getMerchantId());
        response.setRiderId(order.getRiderId());
        response.setType(order.getType());
        response.setTotalAmount(order.getTotalAmount());
        response.setActualAmount(order.getActualAmount());
        response.setDeliveryFee(order.getDeliveryFee());
        response.setDiscount(order.getDiscount());
        response.setStatus(order.getStatus());
        response.setAddressId(order.getAddressId());
        response.setAddressDetail(order.getAddressDetail());
        response.setCouponId(order.getCouponId());
        response.setPaidAt(order.getPaidAt());
        response.setCompletedAt(order.getCompletedAt());
        response.setCreateTime(order.getCreateTime());
        for (OrderItem item : items) {
            OrderInternalResponse.Item responseItem = new OrderInternalResponse.Item();
            responseItem.setProductId(item.getProductId());
            responseItem.setName(item.getName());
            responseItem.setPrice(item.getPrice());
            responseItem.setQuantity(item.getQuantity());
            responseItem.setImage(item.getImage());
            responseItem.setSpecLabel(item.getSpecLabel());
            responseItem.setSubtotal(item.getSubtotal());
            responseItem.setReviewed(Boolean.TRUE.equals(item.getReviewed()));
            response.getItems().add(responseItem);
        }
        return response;
    }

    private String mapStatus(String status) {
        Map<String, String> map = new HashMap<>();
        map.put(STATUS_PENDING_PAYMENT, "待支付");
        map.put(STATUS_PENDING_ACCEPT, "待取餐");
        map.put(STATUS_DELIVERING, "配送中");
        map.put(STATUS_COMPLETED, "已完成");
        map.put(STATUS_CANCELLED, "已取消");
        map.put(STATUS_PENDING_USE, "待使用");
        return map.getOrDefault(status, status);
    }
}
