package com.example.demo.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrderItemMapper;
import com.example.demo.order.mapper.OrdersMapper;
import com.example.demo.review.dto.ReviewRequest;
import com.example.demo.review.dto.ReviewVO;
import com.example.demo.review.entity.Review;
import com.example.demo.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评价服务
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;

    /**
     * 提交评价
     * 一个已完成订单只能评价一次
     */
    @Transactional
    public List<ReviewVO> submitReview(Long userId, ReviewRequest request) {
        // 校验订单
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getId, request.getOrderId())
                        .eq(Orders::getUserId, userId)
        );
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!"completed".equals(order.getStatus())) {
            throw BusinessException.badRequest("只有已完成订单才能评价");
        }

        // 校验是否已评价过（一个订单只能评价一次）
        Long existingCount = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderId, request.getOrderId())
        );
        if (existingCount > 0) {
            throw BusinessException.badRequest("该订单已评价，不可重复评价");
        }

        // 获取订单商品列表
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, request.getOrderId())
        );
        Map<Long, OrderItem> itemMap = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getProductId, item -> item));

        List<Review> reviews = new ArrayList<>();

        for (ReviewRequest.ItemReview itemReview : request.getItems()) {
            // 校验商品是否属于该订单
            if (!itemMap.containsKey(itemReview.getProductId())) {
                throw BusinessException.badRequest("商品ID " + itemReview.getProductId() + " 不属于该订单");
            }

            // 校验评分范围
            if (itemReview.getRating() == null || itemReview.getRating() < 1 || itemReview.getRating() > 5) {
                throw BusinessException.badRequest("评分必须在1-5之间");
            }

            // 校验评价内容长度
            if (itemReview.getContent() != null && itemReview.getContent().length() > 200) {
                throw BusinessException.badRequest("评价内容不能超过200字");
            }

            Review review = new Review();
            review.setOrderId(request.getOrderId());
            review.setUserId(userId);
            review.setMerchantId(order.getMerchantId());
            review.setProductId(itemReview.getProductId());
            review.setRating(itemReview.getRating());
            review.setContent(itemReview.getContent());
            reviewMapper.insert(review);
            reviews.add(review);

            // 标记订单明细为已评价
            OrderItem item = itemMap.get(itemReview.getProductId());
            item.setReviewed(true);
            orderItemMapper.updateById(item);
        }

        // 更新商家综合评分
        updateMerchantRating(order.getMerchantId());

        return reviews.stream().map(this::toReviewVO).collect(Collectors.toList());
    }

    /**
     * 获取商品评价列表
     */
    public List<ReviewVO> getProductReviews(Long productId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProductId, productId)
                        .orderByDesc(Review::getCreateTime)
        );
        return buildReviewVOs(reviews);
    }

    /**
     * 获取商家评价列表
     */
    public List<ReviewVO> getMerchantReviews(Long merchantId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getMerchantId, merchantId)
                        .orderByDesc(Review::getCreateTime)
        );
        return buildReviewVOs(reviews);
    }

    /**
     * 获取用户自己的评价列表
     */
    public List<ReviewVO> getUserReviews(Long userId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreateTime)
        );
        return buildReviewVOs(reviews);
    }

    /**
     * 获取商家综合评分
     */
    public BigDecimal getMerchantRating(Long merchantId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getMerchantId, merchantId)
        );
        if (reviews.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double avg = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
        return BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 更新商家综合评分
     */
    private void updateMerchantRating(Long merchantId) {
        BigDecimal rating = getMerchantRating(merchantId);
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant != null) {
            merchant.setRating(rating);
            merchantMapper.updateById(merchant);
        }
    }

    /**
     * 构建评价VO列表（关联用户昵称、商品名称等）
     */
    private List<ReviewVO> buildReviewVOs(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询用户信息
        List<Long> userIds = reviews.stream()
                .map(Review::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 批量查询商品信息
        List<Long> productIds = reviews.stream()
                .map(Review::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return reviews.stream().map(review -> {
            ReviewVO.ReviewVOBuilder builder = ReviewVO.builder()
                    .id(review.getId())
                    .orderId(review.getOrderId())
                    .userId(review.getUserId())
                    .merchantId(review.getMerchantId())
                    .productId(review.getProductId())
                    .rating(review.getRating())
                    .content(review.getContent())
                    .createTime(review.getCreateTime());

            User user = userMap.get(review.getUserId());
            if (user != null) {
                builder.userName(user.getNickname());
                builder.userAvatar(user.getAvatar());
            }

            Product product = productMap.get(review.getProductId());
            if (product != null) {
                builder.productName(product.getName());
                builder.productImage(product.getImage());
            }

            return builder.build();
        }).collect(Collectors.toList());
    }

    private ReviewVO toReviewVO(Review review) {
        return buildReviewVOs(List.of(review)).get(0);
    }
}
