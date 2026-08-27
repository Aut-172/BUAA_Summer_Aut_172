package com.example.demo.rider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.fulfillment.client.MerchantCatalogClient;
import com.example.demo.fulfillment.client.OrderClient;
import com.example.demo.rider.dto.RiderProfileUpdateRequest;
import com.example.demo.rider.dto.RiderTaskUpdateRequest;
import com.example.demo.rider.dto.RiderTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Rider service.
 */
@Service
@RequiredArgsConstructor
public class RiderService {

    private final RiderMapper riderMapper;
    private final OrderClient orderClient;
    private final MerchantCatalogClient merchantCatalogClient;

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
        List<OrderClient.OrderTaskSnapshot> availableOrders = orderClient.getAvailableTasks();
        List<OrderClient.OrderTaskSnapshot> assignedOrders = orderClient.getAssignedTasks(riderId);
        List<OrderClient.OrderTaskSnapshot> completedOrders = orderClient.getCompletedTasks(riderId);

        RiderTaskVO.RiderStats stats = RiderTaskVO.RiderStats.builder()
                .totalEarnings(sumDeliveryFee(completedOrders))
                .completedOrders(completedOrders.size())
                .totalDistance(null)
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

        String newStatus = normalizeTaskStatus(request.getStatus());
        OrderClient.OrderTaskSnapshot order = switch (newStatus) {
            case "pending_accept" -> orderClient.assignRider(orderId, riderId);
            case "delivering" -> orderClient.getTask(orderId, riderId);
            case "completed" -> orderClient.markDelivered(orderId, riderId);
            default -> throw BusinessException.badRequest("非法的任务状态: " + newStatus);
        };
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

    private double sumDeliveryFee(List<OrderClient.OrderTaskSnapshot> orders) {
        if (orders == null) {
            return 0.0;
        }
        return orders.stream()
                .mapToDouble(order -> order.getDeliveryFee() != null ? order.getDeliveryFee().doubleValue() : 0.0)
                .sum();
    }

    private RiderTaskVO.TaskItem toTaskItem(OrderClient.OrderTaskSnapshot order) {
        String merchantName = "";
        String merchantAvatar = "";
        String merchantAddress = "";
        if (order.getMerchantId() != null) {
            MerchantCatalogClient.MerchantSnapshot merchant = merchantCatalogClient.getMerchant(order.getMerchantId());
            if (merchant != null) {
                merchantName = merchant.getName();
                merchantAvatar = merchant.getAvatar();
                merchantAddress = merchant.getAddress();
            }
        }

        String itemsSummary = (order.getItems() == null ? List.<OrderClient.ItemSnapshot>of() : order.getItems()).stream()
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
