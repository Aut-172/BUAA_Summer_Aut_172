package com.example.demo.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.coupon.dto.CouponVO;
import com.example.demo.coupon.entity.Coupon;
import com.example.demo.coupon.entity.UserCoupon;
import com.example.demo.coupon.mapper.CouponMapper;
import com.example.demo.coupon.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Coupon application and lifecycle service.
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    public List<CouponVO> getUserCoupons(Long userId) {
        List<UserCoupon> userCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByDesc(UserCoupon::getCreateTime)
        );

        return userCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponMapper.selectById(uc.getCouponId());
                    if (coupon == null) {
                        return null;
                    }
                    return CouponVO.builder()
                            .id(coupon.getId())
                            .title(coupon.getName())
                            .description(buildCouponDescription(coupon))
                            .threshold(coupon.getThreshold())
                            .discount(coupon.getDiscount())
                            .expireAt(coupon.getEndTime())
                            .status(uc.getStatus())
                            .build();
                })
                .filter(vo -> vo != null)
                .collect(Collectors.toList());
    }

    public List<CouponVO> getAvailableCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, "released")
                        .le(Coupon::getStartTime, now)
                        .ge(Coupon::getEndTime, now)
        );

        return coupons.stream()
                .map(coupon -> CouponVO.builder()
                        .id(coupon.getId())
                        .title(coupon.getName())
                        .description(buildCouponDescription(coupon))
                        .threshold(coupon.getThreshold())
                        .discount(coupon.getDiscount())
                        .expireAt(coupon.getEndTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public CouponVO claimCoupon(Long userId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw BusinessException.notFound("优惠券不存在");
        }

        validateCouponTemplate(coupon);

        int claimedCount = coupon.getClaimedCount() != null ? coupon.getClaimedCount() : 0;
        int totalCount = coupon.getTotalCount() != null ? coupon.getTotalCount() : 0;
        if (totalCount <= 0 || claimedCount >= totalCount) {
            throw BusinessException.badRequest("该优惠券已被领完");
        }

        Long userClaimed = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getCouponId, couponId)
        );
        int limitPerUser = coupon.getLimitPerUser() != null ? coupon.getLimitPerUser() : 1;
        if (limitPerUser <= 0) {
            throw BusinessException.badRequest("该优惠券当前不可领取");
        }
        if (userClaimed >= limitPerUser) {
            throw BusinessException.badRequest("已达到领取上限");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus("unused");
        userCoupon.setClaimedAt(LocalDateTime.now());
        userCouponMapper.insert(userCoupon);

        coupon.setClaimedCount(claimedCount + 1);
        couponMapper.updateById(coupon);

        return CouponVO.builder()
                .id(coupon.getId())
                .title(coupon.getName())
                .description(buildCouponDescription(coupon))
                .threshold(coupon.getThreshold())
                .discount(coupon.getDiscount())
                .expireAt(coupon.getEndTime())
                .status("unused")
                .build();
    }

    /**
     * Lock a user coupon to an order and return the discount amount that can be applied.
     */
    @Transactional
    public BigDecimal lockCouponForOrder(Long userId, Long couponId, Long orderId, BigDecimal orderAmount) {
        UserCoupon userCoupon = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getCouponId, couponId)
                        .eq(UserCoupon::getStatus, "unused")
                        .orderByAsc(UserCoupon::getCreateTime)
                        .last("limit 1")
        );
        if (userCoupon == null) {
            throw BusinessException.badRequest("优惠券不可用或不属于当前用户");
        }

        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw BusinessException.notFound("优惠券不存在");
        }

        validateCouponTemplate(coupon);
        BigDecimal threshold = coupon.getThreshold() != null ? coupon.getThreshold() : BigDecimal.ZERO;
        if (orderAmount.compareTo(threshold) < 0) {
            throw BusinessException.badRequest("未满足优惠券使用门槛");
        }

        userCoupon.setStatus("locked");
        userCoupon.setOrderId(orderId);
        userCouponMapper.updateById(userCoupon);
        return coupon.getDiscount() != null ? coupon.getDiscount() : BigDecimal.ZERO;
    }

    @Transactional
    public void confirmUseCoupon(Long orderId) {
        UserCoupon userCoupon = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getOrderId, orderId)
        );
        if (userCoupon != null && "locked".equals(userCoupon.getStatus())) {
            userCoupon.setStatus("used");
            userCoupon.setUsedAt(LocalDateTime.now());
            userCouponMapper.updateById(userCoupon);
        }
    }

    @Transactional
    public void releaseCoupon(Long orderId) {
        UserCoupon userCoupon = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getOrderId, orderId)
        );
        if (userCoupon != null && "locked".equals(userCoupon.getStatus())) {
            userCouponMapper.update(
                    null,
                    new LambdaUpdateWrapper<UserCoupon>()
                            .eq(UserCoupon::getId, userCoupon.getId())
                            .set(UserCoupon::getStatus, "unused")
                            .set(UserCoupon::getOrderId, null)
                            .set(UserCoupon::getUsedAt, null)
            );
        }
    }

    private void validateCouponTemplate(Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        if (!"released".equals(coupon.getStatus())) {
            throw BusinessException.badRequest("该优惠券尚未发布");
        }
        if (coupon.getStartTime() == null || coupon.getEndTime() == null
                || now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw BusinessException.badRequest("该优惠券不在有效期内");
        }
    }

    private String buildCouponDescription(Coupon coupon) {
        BigDecimal threshold = coupon.getThreshold() != null ? coupon.getThreshold() : BigDecimal.ZERO;
        BigDecimal discount = coupon.getDiscount() != null ? coupon.getDiscount() : BigDecimal.ZERO;
        return "满" + threshold.stripTrailingZeros().toPlainString()
                + "减" + discount.stripTrailingZeros().toPlainString();
    }
}
