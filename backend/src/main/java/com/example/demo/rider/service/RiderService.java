package com.example.demo.rider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.coupon.service.CouponService;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrderItemMapper;
import com.example.demo.order.mapper.OrdersMapper;
import com.example.demo.rider.dto.RiderProfileUpdateRequest;
import com.example.demo.rider.dto.RiderTaskUpdateRequest;
import com.example.demo.rider.dto.RiderTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rider service.
 */
@Service
@RequiredArgsConstructor
public class RiderService {

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;
    private final CouponService couponService;

    public Rider getProfile(Long riderId) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw BusinessException.notFound("骑手不存在");
        }
        return rider;
    }

    public Rider updateProfile(Long riderId, RiderProfileUpdateRequest request) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw BusinessException.notFound("骑手不存在");
        }

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            rider.setName(request.getNickname().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            Rider duplicate = riderMapper.selectOne(
                    new LambdaQueryWrapper<Rider>()
                            .eq(Rider::getPhone, request.getPhone().trim())
                            .ne(Rider::getId, riderId)
                            .last("limit 1")
            );
            if (duplicate != null) {
                throw BusinessException.badRequest("手机号已被其他骑手使用");
            }
            rider.setPhone(request.getPhone().trim());
        }
        if (request.getServiceArea() != null) {
            rider.setServiceArea(request.getServiceArea().trim());
        }

        riderMapper.updateById(rider);
        return riderMapper.selectById(riderId);
    }

    public RiderTaskVO getTasks(Long riderId) {
        List<Orders> availableOrders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getStatus, "pending_accept")
                        .isNull(Orders::getRiderId)
                        .orderByDesc(Orders::getCreateTime)
        );

        List<Orders> assignedOrders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getRiderId, riderId)
                        .eq(Orders::getStatus, "delivering")
                        .orderByDesc(Orders::getCreateTime)
        );

        List<Orders> completedOrders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getRiderId, riderId)
                        .eq(Orders::getStatus, "completed")
                        .orderByDesc(Orders::getCreateTime)
        );

        RiderTaskVO.RiderStats stats = RiderTaskVO.RiderStats.builder()
                .totalEarnings(completedOrders.size() * 5.0)
                .completedOrders(completedOrders.size())
                .totalDistance(completedOrders.size() * 2.0 + "km")
                .build();

        return RiderTaskVO.builder()
                .available(availableOrders.stream().map(this::toTaskItem).collect(Collectors.toList()))
                .assigned(assignedOrders.stream().map(this::toTaskItem).collect(Collectors.toList()))
                .completed(completedOrders.stream().map(this::toTaskItem).collect(Collectors.toList()))
                .stats(stats)
                .build();
    }

    @Transactional
    public RiderTaskVO.TaskItem updateTask(Long riderId, Long orderId, RiderTaskUpdateRequest request) {
        requireActiveRider(riderId);

        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }

        String newStatus = normalizeTaskStatus(request.getStatus());
        String currentStatus = order.getStatus();

        switch (newStatus) {
            case "pending_accept":
                if (!"pending_accept".equals(currentStatus)) {
                    throw BusinessException.badRequest("当前订单状态不允许接单");
                }
                if (order.getRiderId() != null) {
                    throw BusinessException.badRequest("该订单已被其他骑手接单");
                }
                order.setRiderId(riderId);
                order.setStatus("delivering");
                break;

            case "delivering":
                if (!"delivering".equals(currentStatus)) {
                    throw BusinessException.badRequest("当前订单状态不允许标记配送中");
                }
                if (!riderId.equals(order.getRiderId())) {
                    throw BusinessException.badRequest("该订单不属于当前骑手");
                }
                break;

            case "completed":
                if (!"delivering".equals(currentStatus)) {
                    throw BusinessException.badRequest("当前订单状态不允许确认送达");
                }
                if (!riderId.equals(order.getRiderId())) {
                    throw BusinessException.badRequest("该订单不属于当前骑手");
                }
                order.setStatus("completed");
                order.setCompletedAt(LocalDateTime.now());
                break;

            default:
                throw BusinessException.badRequest("非法的任务状态: " + newStatus);
        }

        ordersMapper.updateById(order);
        if ("completed".equals(order.getStatus()) && order.getCouponId() != null) {
            couponService.confirmUseCoupon(order.getId());
        }
        return toTaskItem(order);
    }

    private void requireActiveRider(Long riderId) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw BusinessException.notFound("骑手不存在");
        }
        if (!"active".equals(rider.getStatus())) {
            throw BusinessException.forbidden("骑手账号审核通过后才能使用该功能");
        }
    }

    private String normalizeTaskStatus(String status) {
        if (status == null) {
            return null;
        }

        return switch (status.trim()) {
            case "待取餐", "待接单" -> "pending_accept";
            case "配送中" -> "delivering";
            case "已完成" -> "completed";
            default -> status.trim();
        };
    }

    private RiderTaskVO.TaskItem toTaskItem(Orders order) {
        String merchantName = "";
        String merchantAvatar = "";
        String merchantAddress = "";
        if (order.getMerchantId() != null) {
            Merchant merchant = merchantMapper.selectById(order.getMerchantId());
            if (merchant != null) {
                merchantName = merchant.getName();
                merchantAvatar = merchant.getAvatar();
                merchantAddress = merchant.getAddress();
            }
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        String itemsSummary = items.stream()
                .map(item -> item.getName() + "x" + item.getQuantity())
                .collect(Collectors.joining("、"));

        String statusText = switch (order.getStatus()) {
            case "pending_accept" -> "待取餐";
            case "delivering" -> "配送中";
            case "completed" -> "已完成";
            default -> order.getStatus();
        };

        return RiderTaskVO.TaskItem.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .merchantId(order.getMerchantId())
                .merchant(merchantName)
                .merchantAvatar(merchantAvatar)
                .items(itemsSummary)
                .pickup(merchantAddress)
                .destination(order.getAddressDetail())
                .status(statusText)
                .eta("预计30分钟送达")
                .total(order.getActualAmount() != null ? order.getActualAmount().doubleValue() : 0.0)
                .build();
    }
}
