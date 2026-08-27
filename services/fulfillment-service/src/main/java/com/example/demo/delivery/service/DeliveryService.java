package com.example.demo.delivery.service;

import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.delivery.dto.DeliveryVO;
import com.example.demo.fulfillment.client.MerchantCatalogClient;
import com.example.demo.fulfillment.client.OrderClient;
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

    private final RiderMapper riderMapper;
    private final OrderClient orderClient;
    private final MerchantCatalogClient merchantCatalogClient;

    public DeliveryService(RiderMapper riderMapper, OrderClient orderClient, MerchantCatalogClient merchantCatalogClient) {
        this.riderMapper = riderMapper;
        this.orderClient = orderClient;
        this.merchantCatalogClient = merchantCatalogClient;
    }

    /**
     * Only the order owner can view delivery info.
     */
    public DeliveryVO getDeliveryInfo(Long userId, Long orderId) {
        OrderClient.OrderTaskSnapshot order = orderClient.getDeliveryOrder(orderId);
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

        MerchantCatalogClient.MerchantSnapshot merchant = merchantCatalogClient.getMerchant(order.getMerchantId());
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

    private List<DeliveryVO.TimelineItem> buildTimeline(OrderClient.OrderTaskSnapshot order) {
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

    private String estimateEta(OrderClient.OrderTaskSnapshot order, MerchantCatalogClient.MerchantSnapshot merchant) {
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
