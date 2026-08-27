package com.example.demo.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.contract.settlement.CouponLockRequest;
import com.example.demo.common.contract.settlement.CouponLockResponse;
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

@Service
@RequiredArgsConstructor
public class SettlementCouponService {

    private static final String STATUS_RELEASED = "released";
    private static final String USER_COUPON_UNUSED = "unused";
    private static final String USER_COUPON_LOCKED = "locked";
    private static final String USER_COUPON_USED = "used";

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    public List<CouponVO> getUserCoupons(Long userId) {
        List<UserCoupon> userCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByDesc(UserCoupon::getCreateTime)
        );

        return userCoupons.stream()
                .map(userCoupon -> {
                    Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
                    if (coupon == null) {
                        return null;
                    }
                    CouponVO vo = toCouponVO(coupon);
                    vo.setStatus(userCoupon.getStatus());
                    return vo;
                })
                .filter(vo -> vo != null)
                .collect(Collectors.toList());
    }

    public List<CouponVO> getAvailableCoupons() {
        LocalDateTime now = LocalDateTime.now();
        return couponMapper.selectList(
                        new LambdaQueryWrapper<Coupon>()
                                .eq(Coupon::getStatus, STATUS_RELEASED)
                                .le(Coupon::getStartTime, now)
                                .ge(Coupon::getEndTime, now)
                                .orderByAsc(Coupon::getThreshold)
                ).stream()
                .map(this::toCouponVO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CouponVO claimCoupon(Long userId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw BusinessException.notFound("优惠券不存在");
        }
        validateCoupon(coupon, coupon.getThreshold() == null ? BigDecimal.ZERO : coupon.getThreshold());

        int claimedCount = coupon.getClaimedCount() == null ? 0 : coupon.getClaimedCount();
        int totalCount = coupon.getTotalCount() == null ? 0 : coupon.getTotalCount();
        if (totalCount <= 0 || claimedCount >= totalCount) {
            throw BusinessException.badRequest("该优惠券已被领完");
        }

        Long userClaimed = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getCouponId, couponId)
        );
        int limitPerUser = coupon.getLimitPerUser() == null ? 1 : coupon.getLimitPerUser();
        if (limitPerUser <= 0 || userClaimed >= limitPerUser) {
            throw BusinessException.badRequest("已达到领取上限");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(USER_COUPON_UNUSED);
        userCoupon.setClaimedAt(LocalDateTime.now());
        userCouponMapper.insert(userCoupon);

        couponMapper.update(null,
                new LambdaUpdateWrapper<Coupon>()
                        .eq(Coupon::getId, couponId)
                        .set(Coupon::getClaimedCount, claimedCount + 1));

        CouponVO vo = toCouponVO(coupon);
        vo.setStatus(USER_COUPON_UNUSED);
        return vo;
    }

    @Transactional
    public CouponLockResponse lockCoupon(CouponLockRequest request) {
        UserCoupon userCoupon = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, request.getUserId())
                        .eq(UserCoupon::getCouponId, request.getCouponId())
                        .and(w -> w.eq(UserCoupon::getStatus, USER_COUPON_UNUSED)
                                .or(q -> q.eq(UserCoupon::getStatus, USER_COUPON_LOCKED)
                                        .eq(UserCoupon::getOrderId, request.getOrderId())))
                        .last("limit 1")
        );
        if (userCoupon == null) {
            throw BusinessException.notFound("用户优惠券不存在");
        }
        if (USER_COUPON_LOCKED.equals(userCoupon.getStatus()) && request.getOrderId().equals(userCoupon.getOrderId())) {
            return response(request, userCoupon, true, "优惠券已锁定");
        }
        if (!USER_COUPON_UNUSED.equals(userCoupon.getStatus())) {
            throw BusinessException.badRequest("优惠券当前状态不可锁定");
        }

        Coupon coupon = couponMapper.selectById(request.getCouponId());
        validateCoupon(coupon, request.getOrderAmount());

        userCoupon.setStatus(USER_COUPON_LOCKED);
        userCoupon.setOrderId(request.getOrderId());
        userCouponMapper.updateById(userCoupon);
        return response(request, userCoupon, true, "锁券成功");
    }

    @Transactional
    public CouponLockResponse releaseCoupon(Long orderId) {
        UserCoupon userCoupon = findByOrderId(orderId);
        if (userCoupon == null) {
            return releasedOrConfirmedResponse(orderId, "未找到锁定优惠券");
        }
        if (!USER_COUPON_LOCKED.equals(userCoupon.getStatus())) {
            return releasedOrConfirmedResponse(orderId, "优惠券无需释放");
        }
        userCoupon.setStatus(USER_COUPON_UNUSED);
        userCoupon.setOrderId(null);
        userCouponMapper.updateById(userCoupon);
        return releasedOrConfirmedResponse(orderId, "释放成功");
    }

    @Transactional
    public CouponLockResponse confirmCoupon(Long orderId) {
        UserCoupon userCoupon = findByOrderId(orderId);
        if (userCoupon == null) {
            return releasedOrConfirmedResponse(orderId, "未找到锁定优惠券");
        }
        if (USER_COUPON_USED.equals(userCoupon.getStatus())) {
            return response(null, userCoupon, true, "优惠券已核销");
        }
        if (!USER_COUPON_LOCKED.equals(userCoupon.getStatus())) {
            throw BusinessException.badRequest("优惠券当前状态不可核销");
        }
        userCoupon.setStatus(USER_COUPON_USED);
        userCoupon.setUsedAt(LocalDateTime.now());
        userCouponMapper.updateById(userCoupon);
        return response(null, userCoupon, true, "核销成功");
    }

    private void validateCoupon(Coupon coupon, BigDecimal orderAmount) {
        if (coupon == null || !STATUS_RELEASED.equals(coupon.getStatus())) {
            throw BusinessException.badRequest("优惠券不可用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && coupon.getStartTime().isAfter(now)) {
            throw BusinessException.badRequest("优惠券尚未生效");
        }
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(now)) {
            throw BusinessException.badRequest("优惠券已过期");
        }
        BigDecimal threshold = coupon.getThreshold() == null ? BigDecimal.ZERO : coupon.getThreshold();
        if (orderAmount.compareTo(threshold) < 0) {
            throw BusinessException.badRequest("订单金额未达到优惠券门槛");
        }
    }

    private UserCoupon findByOrderId(Long orderId) {
        return userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getOrderId, orderId)
                        .last("limit 1")
        );
    }

    private CouponLockResponse response(CouponLockRequest request, UserCoupon userCoupon, boolean locked, String message) {
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        CouponLockResponse response = new CouponLockResponse();
        response.setRequestId(request == null ? null : request.getRequestId());
        response.setUserCouponId(userCoupon.getId());
        response.setCouponId(userCoupon.getCouponId());
        response.setOrderId(userCoupon.getOrderId());
        response.setLocked(locked);
        response.setStatus(userCoupon.getStatus());
        response.setDiscount(coupon == null || coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount());
        response.setMessage(message);
        return response;
    }

    private CouponLockResponse releasedOrConfirmedResponse(Long orderId, String message) {
        CouponLockResponse response = new CouponLockResponse();
        response.setOrderId(orderId);
        response.setLocked(false);
        response.setStatus("none");
        response.setMessage(message);
        return response;
    }

    private CouponVO toCouponVO(Coupon coupon) {
        return CouponVO.builder()
                .id(coupon.getId())
                .title(coupon.getName())
                .description(buildCouponDescription(coupon))
                .threshold(coupon.getThreshold())
                .discount(coupon.getDiscount())
                .expireAt(coupon.getEndTime())
                .build();
    }

    private String buildCouponDescription(Coupon coupon) {
        BigDecimal threshold = coupon.getThreshold() == null ? BigDecimal.ZERO : coupon.getThreshold();
        BigDecimal discount = coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
        return "满" + threshold.stripTrailingZeros().toPlainString()
                + "减" + discount.stripTrailingZeros().toPlainString();
    }
}
