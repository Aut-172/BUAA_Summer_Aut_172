package com.example.demo.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.BusinessException;
import com.example.demo.common.contract.merchant.ProductQuoteRequest;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import com.example.demo.common.contract.merchant.MerchantDashboardStats;
import com.example.demo.common.contract.merchant.StockChangeRequest;
import com.example.demo.common.contract.merchant.StockChangeResponse;
import com.example.demo.common.contract.order.MarkPaidRequest;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.common.contract.user.AddressSnapshot;
import com.example.demo.common.contract.settlement.CouponLockRequest;
import com.example.demo.common.contract.settlement.CouponLockResponse;
import com.example.demo.order.client.MerchantCatalogClient;
import com.example.demo.order.client.MerchantProductClient;
import com.example.demo.order.client.SettlementCouponClient;
import com.example.demo.order.client.UserClient;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.dto.MerchantOrderUpdateRequest;
import com.example.demo.order.dto.OrderVO;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrderItemMapper;
import com.example.demo.order.mapper.OrdersMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
    private final OrderCompensationService orderCompensationService;
    private final OrderCheckoutDraftService orderCheckoutDraftService;
    private final MerchantProductClient merchantProductClient;
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

    public MerchantDashboardStats getMerchantDashboard(Long merchantId) {
        merchantCatalogClient.getMerchant(merchantId);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long todayOrders = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getMerchantId, merchantId)
                        .ge(Orders::getCreateTime, startOfToday)
        );

        BigDecimal todayRevenue = ordersMapper.selectList(
                        new LambdaQueryWrapper<Orders>()
                                .eq(Orders::getMerchantId, merchantId)
                                .ge(Orders::getPaidAt, startOfToday)
                ).stream()
                .map(Orders::getActualAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingOrders = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getMerchantId, merchantId)
                        .in(Orders::getStatus, List.of(STATUS_PENDING_PAYMENT, STATUS_PENDING_ACCEPT))
        );

        MerchantDashboardStats stats = new MerchantDashboardStats();
        stats.setTodayOrders(Math.toIntExact(todayOrders));
        stats.setTodayRevenue(todayRevenue);
        stats.setPendingOrders(Math.toIntExact(pendingOrders));
        return stats;
    }

    @Transactional
    public OrderVO checkout(Long userId, CheckoutRequest request) {
        if (request == null || request.getMerchantId() == null) {
            throw BusinessException.badRequest("请选择商家");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw BusinessException.badRequest("订单商品不能为空");
        }
        if (request.getAddressId() == null && (request.getAddress() == null || request.getAddress().isBlank())) {
            throw BusinessException.badRequest("收货地址不能为空");
        }

        String stockRequestId = UUID.randomUUID().toString();
        StockChangeRequest stockRequest = toStockChangeRequest(request, stockRequestId, null);
        Orders order = null;
        MerchantCatalogClient.MerchantSnapshot merchant = merchantCatalogClient.getMerchant(request.getMerchantId());
        if (merchant == null || !"active".equals(merchant.getStatus())) {
            throw BusinessException.notFound("商家不存在或不可下单");
        }
        ResolvedCheckoutAddress resolvedAddress = resolveCheckoutAddress(userId, request);

        try {
            ProductQuoteResponse quote = orderCheckoutDraftService.quoteProducts(toQuoteRequest(request));
            BigDecimal goodsAmount = quote.getTotalAmount() == null ? BigDecimal.ZERO : quote.getTotalAmount();
            BigDecimal minOrderAmount = merchant.getMinDeliveryFee() == null ? BigDecimal.ZERO : merchant.getMinDeliveryFee();
            if (goodsAmount.compareTo(minOrderAmount) < 0) {
                throw BusinessException.badRequest("未达到商家起送金额");
            }

            BigDecimal deliveryFee = merchant.getDeliveryFee() == null ? BigDecimal.ZERO : merchant.getDeliveryFee();
            BigDecimal totalAmount = goodsAmount.add(deliveryFee);

            reserveInventoryWithConfirmation(stockRequest);

            order = new Orders();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setMerchantId(request.getMerchantId());
            order.setType("delivery");
            order.setTotalAmount(totalAmount);
            order.setActualAmount(totalAmount);
            order.setDeliveryFee(deliveryFee);
            order.setDiscount(BigDecimal.ZERO);
            order.setStatus(STATUS_PENDING_PAYMENT);
            order.setStockReserved(true);
            order.setAddressId(resolvedAddress.addressId());
            order.setAddressDetail(resolvedAddress.addressDetail());
            ordersMapper.insert(order);
            stockRequest.setOrderId(order.getId());

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
                CouponLockResponse coupon = ensureCouponOperationSucceeded(
                        settlementCouponClient.lock(toCouponLockRequest(userId, request.getCouponId(), order.getId(), totalAmount)),
                        "locked",
                        true,
                        "优惠券锁定"
                );
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
        } catch (RuntimeException ex) {
            releaseReservedInventoryAfterFailure(stockRequest, order == null ? null : order.getId(), ex);
            throw ex;
        }
    }

    @Transactional
    public OrderVO cancelOrder(Long userId, Long orderId) {
        Orders order = findUserOrder(userId, orderId);
        if (!STATUS_PENDING_PAYMENT.equals(order.getStatus()) && !STATUS_PENDING_ACCEPT.equals(order.getStatus())) {
            throw BusinessException.badRequest("当前订单状态不允许取消");
        }

        order.setStatus(STATUS_CANCELLED);
        if (Boolean.TRUE.equals(order.getStockReserved())) {
            StockChangeRequest stockRequest = toStockChangeRequest(order, UUID.randomUUID().toString());
            try {
                releaseInventoryWithConfirmation(stockRequest);
                order.setStockReserved(false);
            } catch (RuntimeException ex) {
                recordCompensation(stockRequest, order.getId(), "release_inventory", "merchant-service", "pending", "订单取消后库存释放失败: " + ex.getMessage());
                log.warn("Failed to release stock for cancelled order {}", order.getId(), ex);
            }
        }
        ordersMapper.updateById(order);
        if (order.getCouponId() != null) {
            try {
                CouponLockResponse releaseResponse = settlementCouponClient.release(order.getId());
                if (releaseResponse == null) {
                    recordCompensation(null, order.getId(), "release_coupon", "settlement-service", "pending", "订单取消后优惠券释放未确认: 无响应");
                } else if (!"released".equals(releaseResponse.getStatus()) || Boolean.TRUE.equals(releaseResponse.getLocked())) {
                    recordCompensation(null, order.getId(), "release_coupon", "settlement-service", "pending", "订单取消后优惠券释放未确认: " + releaseResponse.getMessage());
                }
            } catch (RuntimeException ex) {
                recordCompensation(null, order.getId(), "release_coupon", "settlement-service", "pending", "订单取消后优惠券释放失败: " + ex.getMessage());
                log.warn("Failed to release coupon for cancelled order {}", order.getId(), ex);
            }
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

        if (STATUS_COMPLETED.equals(newStatus)) {
            completeOrderState(order);
        } else {
            order.setStatus(newStatus);
            ordersMapper.updateById(order);
        }
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
        order.setStockReserved(false);
        ordersMapper.updateById(order);
        if (order.getCouponId() != null) {
            try {
                CouponLockResponse confirmResponse = ensureCouponOperationSucceeded(
                        settlementCouponClient.confirm(order.getId()),
                        "used",
                        true,
                        "优惠券核销"
                );
                if (confirmResponse == null) {
                    recordCompensation(null, order.getId(), "confirm_coupon", "settlement-service", "pending", "订单完成后优惠券核销未确认: 无响应");
                }
            } catch (RuntimeException ex) {
                recordCompensation(null, order.getId(), "confirm_coupon", "settlement-service", "pending", "订单完成后优惠券确认失败: " + ex.getMessage());
                log.warn("Failed to confirm coupon for completed order {}", order.getId(), ex);
            }
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

    private StockChangeRequest toStockChangeRequest(CheckoutRequest request, String requestId, Long orderId) {
        StockChangeRequest stockRequest = new StockChangeRequest();
        stockRequest.setRequestId(requestId);
        stockRequest.setOrderId(orderId);
        stockRequest.setMerchantId(request.getMerchantId());
        stockRequest.setItems(request.getItems().stream().map(item -> {
            StockChangeRequest.Item stockItem = new StockChangeRequest.Item();
            stockItem.setProductId(item.getProductId());
            stockItem.setSpecLabel(normalizeSpecLabel(item.getSpecLabel()));
            stockItem.setQuantity(item.getQuantity());
            return stockItem;
        }).collect(Collectors.toList()));
        return stockRequest;
    }

    private StockChangeRequest toStockChangeRequest(Orders order, String requestId) {
        StockChangeRequest stockRequest = new StockChangeRequest();
        stockRequest.setRequestId(requestId);
        stockRequest.setOrderId(order.getId());
        stockRequest.setMerchantId(order.getMerchantId());
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        stockRequest.setItems(items.stream().map(item -> {
            StockChangeRequest.Item stockItem = new StockChangeRequest.Item();
            stockItem.setProductId(item.getProductId());
            stockItem.setSpecLabel(item.getSpecLabel());
            stockItem.setQuantity(item.getQuantity());
            return stockItem;
        }).collect(Collectors.toList()));
        return stockRequest;
    }

    private void releaseReservedInventoryAfterFailure(StockChangeRequest request, Long orderId, RuntimeException cause) {
        try {
            StockChangeResponse stockStatus = merchantProductClient.getChangeStatus(request.getRequestId());
            if (stockStatus != null && "reserved".equals(stockStatus.getStatus())) {
                StockChangeRequest releaseRequest = copyStockChangeRequest(request, UUID.randomUUID().toString(), orderId);
                releaseInventoryWithConfirmation(releaseRequest);
                recordCompensation(releaseRequest, orderId, "release_inventory", "merchant-service", "resolved", "订单创建失败后库存已回滚: " + cause.getMessage());
            } else if (stockStatus != null && "released".equals(stockStatus.getStatus())) {
                recordCompensation(request, orderId, "release_inventory", "merchant-service", "resolved", "订单创建失败后库存已处于释放状态: " + cause.getMessage());
            } else if (stockStatus != null && "failed".equals(stockStatus.getStatus())) {
                recordCompensation(request, orderId, "release_inventory", "merchant-service", "resolved", "订单创建失败后库存预留已失败，无需回滚: " + cause.getMessage());
            } else {
                recordCompensation(request, orderId, "release_inventory", "merchant-service", "pending", "订单创建失败后未确认到已预留库存: " + cause.getMessage());
            }
        } catch (RuntimeException releaseEx) {
            recordCompensation(request, orderId, "release_inventory", "merchant-service", "pending", "订单创建失败后库存回滚失败: " + releaseEx.getMessage());
            log.warn("Failed to release inventory for checkout request {}", request.getRequestId(), releaseEx);
        }
    }

    private StockChangeRequest copyStockChangeRequest(StockChangeRequest source, String requestId, Long orderId) {
        StockChangeRequest copy = new StockChangeRequest();
        copy.setRequestId(requestId);
        copy.setOrderId(orderId);
        copy.setMerchantId(source.getMerchantId());
        copy.setItems(source.getItems().stream().map(item -> {
            StockChangeRequest.Item copyItem = new StockChangeRequest.Item();
            copyItem.setProductId(item.getProductId());
            copyItem.setSpecLabel(item.getSpecLabel());
            copyItem.setQuantity(item.getQuantity());
            return copyItem;
        }).collect(Collectors.toList()));
        return copy;
    }

    private StockChangeResponse reserveInventoryWithConfirmation(StockChangeRequest request) {
        try {
            StockChangeResponse response = merchantProductClient.reserve(request);
            return ensureStockChangeSucceeded(response, "reserved", "库存预留");
        } catch (RuntimeException ex) {
            try {
                StockChangeResponse stockStatus = merchantProductClient.getChangeStatus(request.getRequestId());
                if (stockStatus != null && "reserved".equals(stockStatus.getStatus())) {
                    return ensureStockChangeSucceeded(stockStatus, "reserved", "库存预留");
                }
            } catch (RuntimeException ignored) {
                // 继续走外层失败补偿。
            }
            throw ex;
        }
    }

    private StockChangeResponse releaseInventoryWithConfirmation(StockChangeRequest request) {
        try {
            StockChangeResponse response = merchantProductClient.release(request);
            return ensureStockChangeSucceeded(response, "released", "库存释放");
        } catch (RuntimeException ex) {
            try {
                StockChangeResponse stockStatus = merchantProductClient.getChangeStatus(request.getRequestId());
                if (stockStatus != null && "released".equals(stockStatus.getStatus())) {
                    return ensureStockChangeSucceeded(stockStatus, "released", "库存释放");
                }
            } catch (RuntimeException ignored) {
                // 继续走外层失败补偿。
            }
            throw ex;
        }
    }

    private StockChangeResponse ensureStockChangeSucceeded(StockChangeResponse response, String expectedStatus, String actionName) {
        if (response == null) {
            throw BusinessException.badRequest(actionName + "失败");
        }
        if (!Boolean.TRUE.equals(response.getSuccess()) || !expectedStatus.equals(response.getStatus())) {
            String message = response.getMessage();
            if (message == null || message.isBlank()) {
                message = actionName + "失败";
            }
            throw BusinessException.badRequest(message);
        }
        return response;
    }

    private CouponLockResponse ensureCouponOperationSucceeded(CouponLockResponse response, String expectedStatus, boolean expectedLocked, String actionName) {
        if (response == null) {
            throw BusinessException.badRequest(actionName + "失败");
        }
        if (!expectedStatus.equals(response.getStatus()) || !Boolean.valueOf(expectedLocked).equals(response.getLocked())) {
            String message = response.getMessage();
            if (message == null || message.isBlank()) {
                message = actionName + "失败";
            }
            throw BusinessException.badRequest(message);
        }
        return response;
    }

    private void recordCompensation(StockChangeRequest request, Long orderId, String action, String targetService, String status, String message) {
        String requestId = request == null ? UUID.randomUUID().toString() : request.getRequestId();
        String payload = request == null ? "" : describeStockRequest(request);
        try {
            orderCompensationService.record(requestId, orderId, action, targetService, payload, status, message);
        } catch (RuntimeException recordEx) {
            log.warn("Failed to persist compensation record for order {}", orderId, recordEx);
        }
    }

    private String describeStockRequest(StockChangeRequest request) {
        String items = request.getItems().stream()
                .map(item -> item.getProductId() + "x" + item.getQuantity() + (item.getSpecLabel() == null ? "" : "(" + item.getSpecLabel() + ")"))
                .collect(Collectors.joining(","));
        return "merchantId=" + request.getMerchantId() + ", items=[" + items + "]";
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

    private ResolvedCheckoutAddress resolveCheckoutAddress(Long userId, CheckoutRequest request) {
        if (request.getAddressId() != null) {
            AddressSnapshot address = userClient.getAddress(userId, request.getAddressId());
            if (address == null) {
                throw BusinessException.notFound("地址不存在");
            }
            return new ResolvedCheckoutAddress(address.getId(), formatAddress(address));
        }

        String address = request.getAddress() == null ? "" : request.getAddress().trim();
        if (address.isBlank()) {
            throw BusinessException.badRequest("收货地址不能为空");
        }
        return new ResolvedCheckoutAddress(null, address);
    }

    private String formatAddress(AddressSnapshot address) {
        List<String> parts = new ArrayList<>();
        if (address.getName() != null && !address.getName().isBlank()) {
            parts.add(address.getName().trim());
        }
        if (address.getPhone() != null && !address.getPhone().isBlank()) {
            parts.add(address.getPhone().trim());
        }
        if (address.getDetail() != null && !address.getDetail().isBlank()) {
            parts.add(address.getDetail().trim());
        }
        return String.join(" ", parts).trim();
    }

    private record ResolvedCheckoutAddress(Long addressId, String addressDetail) {
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
