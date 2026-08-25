package com.example.demo.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrdersMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理后台服务
 */
@Service
public class AdminService {

    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;
    private final OrdersMapper ordersMapper;

    public AdminService(UserMapper userMapper, MerchantMapper merchantMapper,
                        RiderMapper riderMapper, OrdersMapper ordersMapper) {
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.riderMapper = riderMapper;
        this.ordersMapper = ordersMapper;
    }

    // ==================== 用户管理 ====================

    /**
     * 分页查询用户列表
     */
    public IPage<User> listUsers(int page, int pageSize, String keyword, String status) {
        Page<User> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "consumer")
                .like(keyword != null && !keyword.isEmpty(), User::getNickname, keyword)
                .or(keyword != null && !keyword.isEmpty())
                .like(keyword != null && !keyword.isEmpty(), User::getPhone, keyword)
                .eq(status != null && !status.isEmpty(), User::getStatus, status)
                .orderByDesc(User::getCreateTime);
        return userMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 获取用户详情
     */
    public User getUserDetail(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    /**
     * 删除用户（冻结账号）
     */
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        user.setStatus("frozen");
        userMapper.updateById(user);
    }

    /**
     * 解冻用户
     */
    public void unfreezeUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        user.setStatus("active");
        userMapper.updateById(user);
    }

    // ==================== 商家管理 ====================

    /**
     * 分页查询商家列表
     */
    public IPage<Merchant> listMerchants(int page, int pageSize, String keyword, String status) {
        Page<Merchant> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .like(keyword != null && !keyword.isEmpty(), Merchant::getName, keyword)
                .or(keyword != null && !keyword.isEmpty())
                .like(keyword != null && !keyword.isEmpty(), Merchant::getPhone, keyword)
                .eq(status != null && !status.isEmpty(), Merchant::getStatus, status)
                .orderByDesc(Merchant::getCreateTime);
        return merchantMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 获取商家详情
     */
    public Merchant getMerchantDetail(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw BusinessException.notFound("商家不存在");
        }
        return merchant;
    }

    /**
     * 审核商家
     */
    public void auditMerchant(Long id, String status, String opinion) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw BusinessException.notFound("商家不存在");
        }
        merchant.setStatus(status);
        merchantMapper.updateById(merchant);
    }

    /**
     * 删除商家（冻结）
     */
    public void deleteMerchant(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw BusinessException.notFound("商家不存在");
        }
        merchant.setStatus("frozen");
        merchantMapper.updateById(merchant);
    }

    /**
     * 解冻商家
     */
    public void unfreezeMerchant(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw BusinessException.notFound("商家不存在");
        }
        merchant.setStatus("active");
        merchantMapper.updateById(merchant);
    }

    // ==================== 骑手管理 ====================

    /**
     * 分页查询骑手列表
     */
    public IPage<Rider> listRiders(int page, int pageSize, String keyword, String status) {
        Page<Rider> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Rider> wrapper = new LambdaQueryWrapper<Rider>()
                .like(keyword != null && !keyword.isEmpty(), Rider::getName, keyword)
                .or(keyword != null && !keyword.isEmpty())
                .like(keyword != null && !keyword.isEmpty(), Rider::getPhone, keyword)
                .eq(status != null && !status.isEmpty(), Rider::getStatus, status)
                .orderByDesc(Rider::getCreateTime);
        return riderMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 获取骑手详情
     */
    public Rider getRiderDetail(Long id) {
        Rider rider = riderMapper.selectById(id);
        if (rider == null) {
            throw BusinessException.notFound("骑手不存在");
        }
        return rider;
    }

    /**
     * 审核骑手
     */
    public void auditRider(Long id, String status, String opinion) {
        Rider rider = riderMapper.selectById(id);
        if (rider == null) {
            throw BusinessException.notFound("骑手不存在");
        }
        rider.setStatus(status);
        rider.setAuditOpinion(opinion);
        riderMapper.updateById(rider);
    }

    /**
     * 删除骑手（冻结）
     */
    public void deleteRider(Long id) {
        Rider rider = riderMapper.selectById(id);
        if (rider == null) {
            throw BusinessException.notFound("骑手不存在");
        }
        rider.setStatus("frozen");
        riderMapper.updateById(rider);
    }

    /**
     * 解冻骑手
     */
    public void unfreezeRider(Long id) {
        Rider rider = riderMapper.selectById(id);
        if (rider == null) {
            throw BusinessException.notFound("骑手不存在");
        }
        rider.setStatus("active");
        riderMapper.updateById(rider);
    }

    // ==================== 订单管理 ====================

    /**
     * 分页查询订单列表
     */
    public IPage<Orders> listOrders(int page, int pageSize, String keyword, String status, String type) {
        Page<Orders> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(status != null && !status.isEmpty(), Orders::getStatus, status)
                .eq(type != null && !type.isEmpty(), Orders::getType, type)
                .like(keyword != null && !keyword.isEmpty(), Orders::getOrderNo, keyword)
                .orderByDesc(Orders::getCreateTime);
        return ordersMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 获取订单详情
     */
    public Orders getOrderDetail(Long id) {
        Orders order = ordersMapper.selectById(id);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return order;
    }
}
