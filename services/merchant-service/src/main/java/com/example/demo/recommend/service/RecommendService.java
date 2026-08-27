package com.example.demo.recommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.recommend.dto.RecommendVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐服务
 * 基于地理位置、评分、销量等综合因素推荐商家
 */
@Service
public class RecommendService {

    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;

    public RecommendService(MerchantMapper merchantMapper, ProductMapper productMapper) {
        this.merchantMapper = merchantMapper;
        this.productMapper = productMapper;
    }

    /**
     * 获取推荐商家列表
     *
     * @param lat 用户纬度
     * @param lng 用户经度
     * @return 推荐商家列表（按综合权重排序）
     */
    public List<RecommendVO> getRecommendations(BigDecimal lat, BigDecimal lng) {
        // 1. 查询所有活跃商家
        List<Merchant> merchants = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getStatus, "active")
        );

        // 2. 计算综合得分并排序
        List<ScoredMerchant> scored = new ArrayList<>();
        for (Merchant merchant : merchants) {
            double score = calculateScore(merchant, lat, lng);
            scored.add(new ScoredMerchant(merchant, score));
        }

        // 按得分降序排列
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        // 3. 构建结果
        return scored.stream()
                .map(sm -> buildRecommendVO(sm.merchant, lat, lng))
                .collect(Collectors.toList());
    }

    /**
     * 计算商家综合得分
     * 权重：评分40%、月销量30%、距离30%
     */
    private double calculateScore(Merchant merchant, BigDecimal userLat, BigDecimal userLng) {
        double ratingScore = 0;
        double salesScore = 0;
        double distanceScore = 0;

        // 评分得分 (0-5分 → 0-40分)
        if (merchant.getRating() != null) {
            ratingScore = merchant.getRating().doubleValue() * 8;
        }

        // 月销量得分 (0-10000 → 0-30分)
        if (merchant.getMonthlySales() != null) {
            salesScore = Math.min(merchant.getMonthlySales(), 10000) / 10000.0 * 30;
        }

        // 距离得分 (越近越高, 0-10km → 30-0分)
        if (userLat != null && userLng != null
                && merchant.getLatitude() != null && merchant.getLongitude() != null) {
            double distanceKm = calculateDistanceKm(merchant, userLat, userLng);
            distanceScore = Math.max(0, 30 - distanceKm * 3);
        } else {
            distanceScore = 15; // 默认中等分数
        }

        return ratingScore + salesScore + distanceScore;
    }

    /**
     * 计算两点间距离（公里）
     */
    private double calculateDistanceKm(Merchant merchant, BigDecimal userLat, BigDecimal userLng) {
        double latDiff = Math.abs(merchant.getLatitude().doubleValue() - userLat.doubleValue());
        double lngDiff = Math.abs(merchant.getLongitude().doubleValue() - userLng.doubleValue());
        return Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111;
    }

    /**
     * 构建推荐 VO
     */
    private RecommendVO buildRecommendVO(Merchant merchant, BigDecimal userLat, BigDecimal userLng) {
        // 查询商品
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchant.getId())
                        .eq(Product::getStatus, "active")
                        .last("LIMIT 3") // 最多取3个
        );

        List<RecommendVO.ProductItem> productItems = products.stream().map(p ->
                RecommendVO.ProductItem.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .price(p.getPrice())
                        .sales(p.getMonthlySales())
                        .image(p.getImage())
                        .build()
        ).collect(Collectors.toList());

        // 距离
        String distance;
        if (userLat != null && userLng != null
                && merchant.getLatitude() != null && merchant.getLongitude() != null) {
            double km = calculateDistanceKm(merchant, userLat, userLng);
            if (km < 1) {
                distance = (int) (km * 1000) + "m";
            } else {
                distance = String.format("%.1fkm", km);
            }
        } else {
            distance = "未知";
        }

        // 标签
        List<String> tags = new ArrayList<>();
        if (merchant.getTags() != null && !merchant.getTags().isEmpty()) {
            String[] tagArr = merchant.getTags().split(",");
            for (String t : tagArr) {
                tags.add(t.trim());
            }
        }

        // 配送费
        String feeText;
        if (merchant.getDeliveryFee() != null && merchant.getDeliveryFee().compareTo(BigDecimal.ZERO) > 0) {
            feeText = "配送费¥" + merchant.getDeliveryFee();
        } else {
            feeText = "免配送费";
        }

        String salesText = "月售" + (merchant.getMonthlySales() != null ? merchant.getMonthlySales() : 0);

        return RecommendVO.builder()
                .id(merchant.getId())
                .name(merchant.getName())
                .category(merchant.getCategory())
                .rating(merchant.getRating())
                .sales(salesText)
                .distance(distance)
                .fee(feeText)
                .open("active".equals(merchant.getStatus()))
                .description(merchant.getDescription())
                .address(merchant.getAddress())
                .phone(merchant.getPhone())
                .avatar(merchant.getAvatar())
                .tags(tags)
                .products(productItems)
                .build();
    }

    /**
     * 带得分的商家内部类
     */
    private static class ScoredMerchant {
        final Merchant merchant;
        final double score;

        ScoredMerchant(Merchant merchant, double score) {
            this.merchant = merchant;
            this.score = score;
        }
    }
}
