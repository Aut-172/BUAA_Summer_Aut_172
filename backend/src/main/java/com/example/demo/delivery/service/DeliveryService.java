package com.example.demo.delivery.service;

import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.delivery.dto.DeliveryVO;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrdersMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Delivery tracking service.
 */
@Service
public class DeliveryService {

    private final OrdersMapper ordersMapper;
    private final RiderMapper riderMapper;
    private final MerchantMapper merchantMapper;

    public DeliveryService(OrdersMapper ordersMapper, RiderMapper riderMapper, MerchantMapper merchantMapper) {
        this.ordersMapper = ordersMapper;
        this.riderMapper = riderMapper;
        this.merchantMapper = merchantMapper;
    }

    /**
     * Only the order owner can view delivery info.
     */
    public DeliveryVO getDeliveryInfo(Long userId, Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            return null;
        }
        if (userId == null || !userId.equals(order.getUserId())) {
            throw BusinessException.forbidden("无权查看该订单配送信息");
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

        Merchant merchant = merchantMapper.selectById(order.getMerchantId());
        List<DeliveryVO.TimelineItem> timeline = buildTimeline(order);
        String displayStatus = mapStatus(order.getStatus());
        String eta = estimateEta(order, merchant);

        return DeliveryVO.builder()
                .orderId(order.getId())
                .status(displayStatus)
                .riderName(riderName)
                .riderPhone(riderPhone)
                .eta(eta)
                .timeline(timeline)
                .build();
    }

    private List<DeliveryVO.TimelineItem> buildTimeline(Orders order) {
        List<DeliveryVO.TimelineItem> timeline = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        if (order.getCreateTime() != null) {
            timeline.add(DeliveryVO.TimelineItem.builder()
                    .label("已下单")
                    .time(order.getCreateTime().format(fmt))
                    .build());
        }

        if (order.getPaidAt() != null) {
            timeline.add(DeliveryVO.TimelineItem.builder()
                    .label("已支付")
                    .time(order.getPaidAt().format(fmt))
                    .build());
        }

        if ("delivering".equals(order.getStatus()) && order.getRiderId() != null) {
            timeline.add(DeliveryVO.TimelineItem.builder()
                    .label("配送中")
                    .time(LocalDateTime.now().format(fmt))
                    .build());
        }

        if ("completed".equals(order.getStatus()) && order.getCompletedAt() != null) {
            timeline.add(DeliveryVO.TimelineItem.builder()
                    .label("已完成")
                    .time(order.getCompletedAt().format(fmt))
                    .build());
        }

        return timeline;
    }

    private String estimateEta(Orders order, Merchant merchant) {
        if ("completed".equals(order.getStatus()) || "cancelled".equals(order.getStatus())) {
            return null;
        }

        if ("delivering".equals(order.getStatus())) {
            return "约25-30分钟";
        }

        if ("pending_accept".equals(order.getStatus())) {
            return merchant != null ? "商家接单中" : "等待接单";
        }

        if ("pending_payment".equals(order.getStatus())) {
            return "等待支付";
        }

        return null;
    }

    private String mapStatus(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "pending_payment" -> "待支付";
            case "pending_accept" -> "待取餐";
            case "delivering" -> "配送中";
            case "completed" -> "已完成";
            case "cancelled" -> "已取消";
            case "pending_use" -> "待使用";
            default -> status;
        };
    }
}
