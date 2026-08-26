package com.example.demo.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.dashboard.dto.DashboardVO;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrdersMapper;
import com.example.demo.user.entity.UserFavoriteMerchant;
import com.example.demo.user.mapper.UserFavoriteMerchantMapper;
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
    private final RiderMapper riderMapper;
    private final MerchantMapper merchantMapper;
    private final UserFavoriteMerchantMapper favoriteMerchantMapper;

    public DashboardService(OrdersMapper ordersMapper, RiderMapper riderMapper,
                            MerchantMapper merchantMapper, UserFavoriteMerchantMapper favoriteMerchantMapper) {
        this.ordersMapper = ordersMapper;
        this.riderMapper = riderMapper;
        this.merchantMapper = merchantMapper;
        this.favoriteMerchantMapper = favoriteMerchantMapper;
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
                .favoriteMerchants(countActiveFavoriteMerchants(userId))
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

        java.util.List<Orders> completedOrders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getMerchantId, merchantId)
                        .eq(Orders::getStatus, "completed")
                        .ge(Orders::getCompletedAt, todayStart)
                        .le(Orders::getCompletedAt, todayEnd)
        );
        double todayRevenue = sumActualAmount(completedOrders);

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

        java.util.List<Orders> completedOrders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getRiderId, riderId)
                        .eq(Orders::getStatus, "completed")
                        .ge(Orders::getCompletedAt, todayStart)
                        .le(Orders::getCompletedAt, todayEnd)
        );

        Rider rider = riderMapper.selectById(riderId);
        int todayDeliveries = completedOrders != null ? completedOrders.size() : 0;
        double todayEarnings = sumDeliveryFee(completedOrders);

        return DashboardVO.RiderData.builder()
                .todayDeliveries(todayDeliveries)
                .todayEarnings(todayEarnings)
                .status(rider != null ? rider.getStatus() : null)
                .build();
    }

    private double sumActualAmount(java.util.List<Orders> orders) {
        if (orders == null) {
            return 0.0;
        }
        return orders.stream()
                .mapToDouble(order -> order.getActualAmount() != null ? order.getActualAmount().doubleValue() : 0.0)
                .sum();
    }

    private double sumDeliveryFee(java.util.List<Orders> orders) {
        if (orders == null) {
            return 0.0;
        }
        return orders.stream()
                .mapToDouble(order -> order.getDeliveryFee() != null ? order.getDeliveryFee().doubleValue() : 0.0)
                .sum();
    }

    private int countActiveFavoriteMerchants(Long userId) {
        java.util.List<UserFavoriteMerchant> favorites = favoriteMerchantMapper.selectList(
                new LambdaQueryWrapper<UserFavoriteMerchant>()
                        .eq(UserFavoriteMerchant::getUserId, userId)
        );
        if (favorites == null || favorites.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (UserFavoriteMerchant favorite : favorites) {
            Merchant merchant = merchantMapper.selectById(favorite.getMerchantId());
            if (merchant != null && "active".equals(merchant.getStatus())) {
                count++;
            }
        }
        return count;
    }
}
