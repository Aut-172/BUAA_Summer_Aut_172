package com.example.demo.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dashboard.dto.DashboardVO;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrdersMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 仪表盘服务
 */
@Service
public class DashboardService {

    private final OrdersMapper ordersMapper;

    public DashboardService(OrdersMapper ordersMapper) {
        this.ordersMapper = ordersMapper;
    }

    /**
     * 获取消费者仪表盘数据
     */
    public DashboardVO.ConsumerData getConsumerData(Long userId) {
        // 查询用户订单总数
        Long orderCount = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getUserId, userId)
        );

        return DashboardVO.ConsumerData.builder()
                .orderCount(orderCount != null ? orderCount.intValue() : 0)
                .favoriteMerchants(0) // 收藏功能暂未实现
                .build();
    }

    /**
     * 获取商家仪表盘数据
     */
    public DashboardVO.MerchantData getMerchantData(Long merchantId) {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 今日订单数
        Long todayOrders = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getMerchantId, merchantId)
                        .ge(Orders::getCreateTime, todayStart)
                        .le(Orders::getCreateTime, todayEnd)
        );

        // 待处理订单数（待接单）
        Long pendingOrders = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getMerchantId, merchantId)
                        .eq(Orders::getStatus, "pending_accept")
        );

        // 今日收入（已完成订单）
        Double todayRevenue = 0.0;
        // 简化处理：查询今日已完成订单的总金额
        java.util.List<Orders> completedOrders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getMerchantId, merchantId)
                        .eq(Orders::getStatus, "completed")
                        .ge(Orders::getCreateTime, todayStart)
                        .le(Orders::getCreateTime, todayEnd)
        );
        if (completedOrders != null) {
            todayRevenue = completedOrders.stream()
                    .mapToDouble(o -> o.getActualAmount() != null ? o.getActualAmount().doubleValue() : 0.0)
                    .sum();
        }

        return DashboardVO.MerchantData.builder()
                .todayOrders(todayOrders != null ? todayOrders.intValue() : 0)
                .todayRevenue(todayRevenue)
                .pendingOrders(pendingOrders != null ? pendingOrders.intValue() : 0)
                .build();
    }

    /**
     * 获取骑手仪表盘数据
     */
    public DashboardVO.RiderData getRiderData(Long riderId) {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 今日配送完成数
        Long todayDeliveries = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getRiderId, riderId)
                        .eq(Orders::getStatus, "completed")
                        .ge(Orders::getCreateTime, todayStart)
                        .le(Orders::getCreateTime, todayEnd)
        );

        // 今日收入（简化：每单固定配送费5元）
        double todayEarnings = (todayDeliveries != null ? todayDeliveries : 0) * 5.0;

        return DashboardVO.RiderData.builder()
                .todayDeliveries(todayDeliveries != null ? todayDeliveries.intValue() : 0)
                .todayEarnings(todayEarnings)
                .online(true) // 简化处理
                .build();
    }
}
