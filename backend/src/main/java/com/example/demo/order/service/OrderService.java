package com.example.demo.order.service;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.coupon.service.CouponService;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.entity.ProductSpec;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.merchant.mapper.ProductSpecMapper;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.dto.MerchantOrderUpdateRequest;
import com.example.demo.order.dto.OrderVO;
import com.example.demo.order.entity.GroupCoupon;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.GroupCouponMapper;
import com.example.demo.order.mapper.OrderItemMapper;
import com.example.demo.order.mapper.OrdersMapper;
import com.example.demo.user.entity.Cart;
import com.example.demo.user.mapper.CartMapper;
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
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Order creation, payment-state transition and order query service.
 */
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
    private final GroupCouponMapper groupCouponMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;
    private final CartMapper cartMapper;
    private final CouponService couponService;
    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;

    private final Snowflake snowflake = IdUtil.getSnowflake(1, 1);

    public List<OrderVO> getUserOrders(Long userId) {
        List<Orders> orders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getUserId, userId)
                        .orderByDesc(Orders::getCreateTime)
        );
        return orders.stream().map(this::toOrderVO).collect(Collectors.toList());
    }

    public OrderVO getOrderDetail(Long userId, Long orderId) {
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getId, orderId)
                        .eq(Orders::getUserId, userId)
        );
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return toOrderVO(order);
    }

    public OrderVO getParticipantOrderDetail(Long participantId, String participantType, Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!isOrderParticipant(order, participantId, participantType)) {
            throw BusinessException.forbidden("无权访问该订单详情");
        }
        return toOrderVO(order);
    }

    @Transactional
    public OrderVO checkout(Long userId, CheckoutRequest request) {
        if (request.getMerchantId() == null) {
            throw BusinessException.badRequest("请选择商家");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw BusinessException.badRequest("订单商品不能为空");
        }
        if (request.getAddress() == null || request.getAddress().isBlank()) {
            throw BusinessException.badRequest("收货地址不能为空");
        }

        Merchant merchant = merchantMapper.selectById(request.getMerchantId());
        if (merchant == null || !"active".equals(merchant.getStatus())) {
            throw BusinessException.notFound("商家不存在或不可下单");
        }

        List<ResolvedOrderItem> resolvedItems = new ArrayList<>();
        BigDecimal goodsAmount = BigDecimal.ZERO;

        for (CheckoutRequest.CheckoutItem item : request.getItems()) {
            if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw BusinessException.badRequest("商品信息不合法");
            }

            Product product = productMapper.selectById(item.getProductId());
            if (product == null || !"active".equals(product.getStatus())) {
                throw BusinessException.badRequest("商品不存在或已下架");
            }
            if (!request.getMerchantId().equals(product.getMerchantId())) {
                throw BusinessException.badRequest("订单中包含不属于当前商家的商品");
            }
            if (product.getStock() != null && product.getStock() < item.getQuantity()) {
                throw BusinessException.badRequest("商品库存不足: " + product.getName());
            }

            BigDecimal unitPrice = product.getPrice();
            String requestedSpecLabel = item.getSpecLabel();
            String specLabel = normalizeSpecLabel(requestedSpecLabel);
            if (specLabel != null && !specLabel.isBlank()) {
                ProductSpec spec = productSpecMapper.selectOne(
                        new LambdaQueryWrapper<ProductSpec>()
                                .eq(ProductSpec::getProductId, product.getId())
                                .eq(ProductSpec::getLabel, specLabel.trim())
                                .last("limit 1")
                );
                if (spec == null) {
                    throw BusinessException.badRequest("商品规格不存在: " + requestedSpecLabel);
                }
                if (spec.getStock() != null && spec.getStock() < item.getQuantity()) {
                    throw BusinessException.badRequest("商品规格库存不足: " + requestedSpecLabel);
                }
                unitPrice = unitPrice.add(spec.getPrice() != null ? spec.getPrice() : BigDecimal.ZERO);
                specLabel = spec.getLabel();
            }

            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            goodsAmount = goodsAmount.add(subtotal);
            resolvedItems.add(new ResolvedOrderItem(product, item.getQuantity(), unitPrice, subtotal, specLabel));
        }

        BigDecimal minOrderAmount = merchant.getMinDeliveryFee() != null ? merchant.getMinDeliveryFee() : BigDecimal.ZERO;
        if (goodsAmount.compareTo(minOrderAmount) < 0) {
            throw BusinessException.badRequest("未达到商家起送金额");
        }

        BigDecimal deliveryFee = merchant.getDeliveryFee() != null ? merchant.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal totalAmount = goodsAmount.add(deliveryFee);
        reserveInventory(resolvedItems);

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

        for (ResolvedOrderItem resolvedItem : resolvedItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(resolvedItem.product().getId());
            orderItem.setName(resolvedItem.product().getName());
            orderItem.setPrice(resolvedItem.unitPrice());
            orderItem.setQuantity(resolvedItem.quantity());
            orderItem.setImage(resolvedItem.product().getImage());
            orderItem.setSpecLabel(resolvedItem.specLabel());
            orderItem.setSubtotal(resolvedItem.subtotal());
            orderItem.setReviewed(false);
            orderItemMapper.insert(orderItem);
        }

        if (request.getCouponId() != null) {
            BigDecimal discount = couponService.lockCouponForOrder(userId, request.getCouponId(), order.getId(), totalAmount);
            if (discount.compareTo(totalAmount) > 0) {
                discount = totalAmount;
            }
            order.setCouponId(request.getCouponId());
            order.setDiscount(discount);
            order.setActualAmount(totalAmount.subtract(discount));
            ordersMapper.updateById(order);
        }

        cartMapper.delete(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getMerchantId, request.getMerchantId())
        );

        return toOrderVO(order);
    }

    @Transactional
    public OrderVO cancelOrder(Long userId, Long orderId) {
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getId, orderId)
                        .eq(Orders::getUserId, userId)
        );
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!STATUS_PENDING_PAYMENT.equals(order.getStatus()) && !STATUS_PENDING_ACCEPT.equals(order.getStatus())) {
            throw BusinessException.badRequest("当前订单状态不允许取消");
        }

        restoreInventory(orderId);
        order.setStatus(STATUS_CANCELLED);
        ordersMapper.updateById(order);
        if (order.getCouponId() != null) {
            couponService.releaseCoupon(order.getId());
        }
        return toOrderVO(order);
    }

    @Transactional
    public OrderVO completeOrder(Long userId, Long orderId) {
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getId, orderId)
                        .eq(Orders::getUserId, userId)
        );
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!STATUS_DELIVERING.equals(order.getStatus())) {
            throw BusinessException.badRequest("当前订单状态不允许确认收货");
        }

        order.setStatus(STATUS_COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        ordersMapper.updateById(order);
        if (order.getCouponId() != null) {
            couponService.confirmUseCoupon(order.getId());
        }
        return toOrderVO(order);
    }

    public List<OrderVO> getMerchantOrders(Long merchantId) {
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
        );
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }

        String newStatus = normalizeStatusCode(request.getStatus());
        String currentStatus = order.getStatus();
        boolean validTransition = false;

        if (STATUS_PENDING_ACCEPT.equals(currentStatus) && STATUS_DELIVERING.equals(newStatus)) {
            validTransition = true;
        } else if (STATUS_DELIVERING.equals(currentStatus) && STATUS_COMPLETED.equals(newStatus)) {
            validTransition = true;
        } else if (STATUS_PENDING_ACCEPT.equals(currentStatus) && STATUS_COMPLETED.equals(newStatus)) {
            validTransition = true;
        }

        if (!validTransition) {
            throw BusinessException.badRequest("非法的订单状态变更");
        }

        order.setStatus(newStatus);
        if (STATUS_COMPLETED.equals(newStatus)) {
            order.setCompletedAt(LocalDateTime.now());
            if (order.getCouponId() != null) {
                couponService.confirmUseCoupon(order.getId());
            }
        }
        ordersMapper.updateById(order);
        return toOrderVO(order);
    }

    private void requireActiveMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw BusinessException.notFound("商家不存在");
        }
        if (!"active".equals(merchant.getStatus())) {
            throw BusinessException.forbidden("商家账号审核通过后才能使用该功能");
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
            default -> status;
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
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long id = snowflake.nextId();
        return "ORD" + datePart + String.format("%010d", id % 10000000000L);
    }

    private void reserveInventory(List<ResolvedOrderItem> resolvedItems) {
        for (ResolvedOrderItem resolvedItem : resolvedItems) {
            Product product = resolvedItem.product();
            if (product.getStock() != null) {
                product.setStock(product.getStock() - resolvedItem.quantity());
                productMapper.updateById(product);
            }

            if (resolvedItem.specLabel() == null || resolvedItem.specLabel().isBlank()) {
                continue;
            }

            ProductSpec spec = productSpecMapper.selectOne(
                    new LambdaQueryWrapper<ProductSpec>()
                            .eq(ProductSpec::getProductId, product.getId())
                            .eq(ProductSpec::getLabel, resolvedItem.specLabel())
                            .last("limit 1")
            );
            if (spec != null && spec.getStock() != null) {
                spec.setStock(spec.getStock() - resolvedItem.quantity());
                productSpecMapper.updateById(spec);
            }
        }
    }

    private void restoreInventory(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
        );

        for (OrderItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            if (product != null && product.getStock() != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productMapper.updateById(product);
            }

            if (item.getSpecLabel() == null || item.getSpecLabel().isBlank()) {
                continue;
            }

            ProductSpec spec = productSpecMapper.selectOne(
                    new LambdaQueryWrapper<ProductSpec>()
                            .eq(ProductSpec::getProductId, item.getProductId())
                            .eq(ProductSpec::getLabel, item.getSpecLabel())
                            .last("limit 1")
            );
            if (spec != null && spec.getStock() != null) {
                spec.setStock(spec.getStock() + item.getQuantity());
                productSpecMapper.updateById(spec);
            }
        }
    }

    private void generateGroupCoupons(Orders order) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        for (OrderItem item : items) {
            for (int i = 0; i < item.getQuantity(); i++) {
                GroupCoupon coupon = new GroupCoupon();
                coupon.setOrderId(order.getId());
                coupon.setOrderItemId(item.getId());
                coupon.setCode(generateCouponCode());
                coupon.setStatus("pending_use");
                coupon.setExpireAt(LocalDateTime.now().plusDays(30));
                groupCouponMapper.insert(coupon);
            }
        }
    }

    private String generateCouponCode() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    private OrderVO toOrderVO(Orders order) {
        String merchantName = "";
        String merchantAvatar = "";
        if (order.getMerchantId() != null) {
            Merchant merchant = merchantMapper.selectById(order.getMerchantId());
            if (merchant != null) {
                merchantName = merchant.getName();
                merchantAvatar = merchant.getAvatar();
            }
        }

        String riderName = null;
        String riderPhone = null;
        if (order.getRiderId() != null) {
            Rider rider = riderMapper.selectById(order.getRiderId());
            if (rider != null) {
                riderName = rider.getName();
                riderPhone = rider.getPhone();
            }
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
                .merchant(merchantName)
                .merchantAvatar(merchantAvatar)
                .status(mapStatus(order.getStatus()))
                .total(order.getActualAmount())
                .deliveryFee(order.getDeliveryFee())
                .discount(order.getDiscount())
                .eta("预计30分钟送达")
                .createdAt(order.getCreateTime())
                .paidAt(order.getPaidAt())
                .riderId(order.getRiderId())
                .riderName(riderName)
                .riderPhone(riderPhone)
                .address(order.getAddressDetail())
                .items(itemVOs)
                .reviewedProductIds(reviewedProductIds)
                .timeline(buildTimeline(order))
                .build();
    }

    private List<OrderVO.TimelineItem> buildTimeline(Orders order) {
        List<OrderVO.TimelineItem> timeline = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");

        timeline.add(OrderVO.TimelineItem.builder()
                .label("已下单")
                .time(order.getCreateTime().format(formatter))
                .build());

        if (order.getPaidAt() != null) {
            timeline.add(OrderVO.TimelineItem.builder()
                    .label("已支付")
                    .time(order.getPaidAt().format(formatter))
                    .build());
        }

        if (order.getCompletedAt() != null) {
            timeline.add(OrderVO.TimelineItem.builder()
                    .label("已完成")
                    .time(order.getCompletedAt().format(formatter))
                    .build());
        }

        if (STATUS_CANCELLED.equals(order.getStatus())) {
            timeline.add(OrderVO.TimelineItem.builder()
                    .label("已取消")
                    .time(LocalDateTime.now().format(formatter))
                    .build());
        }

        return timeline;
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

    private record ResolvedOrderItem(Product product,
                                     Integer quantity,
                                     BigDecimal unitPrice,
                                     BigDecimal subtotal,
                                     String specLabel) {
    }
}
