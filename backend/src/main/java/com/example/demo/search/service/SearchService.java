package com.example.demo.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.search.dto.SearchResultVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 搜索服务
 * 支持按关键词搜索商家和商品
 */
@Service
public class SearchService {

    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;

    public SearchService(MerchantMapper merchantMapper, ProductMapper productMapper) {
        this.merchantMapper = merchantMapper;
        this.productMapper = productMapper;
    }

    /**
     * 复合搜索：优先匹配商家名称，其次匹配商品名称
     *
     * @param keyword  搜索关键词
     * @param category 商家类型筛选（可选）
     * @param sort     排序方式：rating(好评优先)/sales(销量优先)/distance(距离优先)
     * @param lat      用户纬度（用于距离计算）
     * @param lng      用户经度（用于距离计算）
     * @return 搜索结果列表
     */
    public List<SearchResultVO> search(String keyword, String category, String sort,
                                       BigDecimal lat, BigDecimal lng) {
        // 1. 查询所有活跃商家
        List<Merchant> merchants = merchantMapper.selectList(null);

        // 2. 按关键词过滤
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            merchants = merchants.stream()
                    .filter(m -> m.getName() != null && m.getName().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
        }

        // 3. 按分类过滤
        if (category != null && !category.trim().isEmpty()) {
            merchants = merchants.stream()
                    .filter(m -> category.equals(m.getCategory()))
                    .collect(Collectors.toList());
        }

        // 4. 构建结果
        List<SearchResultVO> result = new ArrayList<>();
        Map<Long, Integer> merchantSalesMap = new HashMap<>();

        for (Merchant merchant : merchants) {
            // 查询该商家的商品
            List<Product> products = productMapper.selectList(
                    new LambdaQueryWrapper<Product>()
                            .eq(Product::getMerchantId, merchant.getId())
                            .eq(Product::getStatus, "active")
            );

            // 如果有关键词，也匹配商品名称
            List<Product> matchedProducts = products;
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim().toLowerCase();
                matchedProducts = products.stream()
                        .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(kw))
                        .collect(Collectors.toList());
            }

            // 如果商家名和商品名都不匹配，跳过
            if (keyword != null && !keyword.trim().isEmpty() && matchedProducts.isEmpty()) {
                continue;
            }

            // 计算距离
            String distance = calculateDistance(merchant, lat, lng);

            // 构建商品列表
            List<SearchResultVO.ProductItem> productItems = matchedProducts.stream().map(p ->
                    SearchResultVO.ProductItem.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .desc(p.getDescription())
                            .price(p.getPrice())
                            .sales(p.getMonthlySales())
                            .stock(p.getStock())
                            .image(p.getImage())
                            .build()
            ).collect(Collectors.toList());

            // 构建商家标签
            List<String> tags = new ArrayList<>();
            if (merchant.getTags() != null && !merchant.getTags().isEmpty()) {
                String[] tagArr = merchant.getTags().split(",");
                for (String t : tagArr) {
                    tags.add(t.trim());
                }
            }

            // 配送费文案
            String feeText;
            if (merchant.getDeliveryFee() != null && merchant.getDeliveryFee().compareTo(BigDecimal.ZERO) > 0) {
                feeText = "配送费¥" + merchant.getDeliveryFee();
            } else {
                feeText = "免配送费";
            }

            // 销量文案
            String salesText;
            if (merchant.getMonthlySales() != null) {
                salesText = "月售" + merchant.getMonthlySales();
            } else {
                salesText = "月售0";
            }

            // 营业状态
            boolean open = "active".equals(merchant.getStatus());

            SearchResultVO vo = SearchResultVO.builder()
                    .id(merchant.getId())
                    .name(merchant.getName())
                    .category(merchant.getCategory())
                    .rating(merchant.getRating())
                    .sales(salesText)
                    .distance(distance)
                    .fee(feeText)
                    .open(open)
                    .description(merchant.getDescription())
                    .address(merchant.getAddress())
                    .phone(merchant.getPhone())
                    .avatar(merchant.getAvatar())
                    .tags(tags)
                    .products(productItems)
                    .build();

            result.add(vo);
            merchantSalesMap.put(merchant.getId(), merchant.getMonthlySales() != null ? merchant.getMonthlySales() : 0);
        }

        // 5. 排序
        if (sort != null) {
            switch (sort) {
                case "rating":
                    result.sort((a, b) -> b.getRating().compareTo(a.getRating()));
                    break;
                case "sales":
                    result.sort((a, b) -> {
                        int salesA = merchantSalesMap.getOrDefault(a.getId(), 0);
                        int salesB = merchantSalesMap.getOrDefault(b.getId(), 0);
                        return Integer.compare(salesB, salesA);
                    });
                    break;
                default:
                    break;
            }
        }

        return result;
    }

    /**
     * 计算距离（简化版：使用欧几里得距离近似）
     */
    private String calculateDistance(Merchant merchant, BigDecimal userLat, BigDecimal userLng) {
        if (userLat == null || userLng == null || merchant.getLatitude() == null || merchant.getLongitude() == null) {
            return "未知";
        }

        // 1度 ≈ 111km
        double latDiff = Math.abs(merchant.getLatitude().doubleValue() - userLat.doubleValue());
        double lngDiff = Math.abs(merchant.getLongitude().doubleValue() - userLng.doubleValue());
        double distanceKm = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111;

        if (distanceKm < 1) {
            int meters = (int) (distanceKm * 1000);
            return meters + "m";
        } else {
            return String.format("%.1fkm", distanceKm);
        }
    }
}
