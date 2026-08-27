package com.example.demo.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.contract.order.MarkPaidRequest;
import com.example.demo.common.contract.order.OrderInternalResponse;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrderItemMapper;
import com.example.demo.order.mapper.OrdersMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Internal order state service owned by order-service.
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

    public OrderInternalResponse getOrder(Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return toInternalResponse(order);
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
}
