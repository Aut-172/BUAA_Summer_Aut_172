package com.example.demo.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.cache.CacheProperties;
import com.example.demo.common.cache.RedisJsonCacheService;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.search.dto.SearchResultVO;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    private static final Duration SEARCH_TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<SearchResultVO>> SEARCH_RESULT_LIST_TYPE = new TypeReference<>() {
    };

    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;
    private final RedisJsonCacheService cacheService;
    private final CacheProperties cacheProperties;

    public SearchService(MerchantMapper merchantMapper,
                         ProductMapper productMapper,
                         RedisJsonCacheService cacheService,
                         CacheProperties cacheProperties) {
        this.merchantMapper = merchantMapper;
        this.productMapper = productMapper;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    public List<SearchResultVO> search(String keyword, String category, String sort,
                                       BigDecimal lat, BigDecimal lng) {
        return cacheService.getOrLoad(searchKey(keyword, category, sort, lat, lng), SEARCH_RESULT_LIST_TYPE,
                cacheProperties.ttl("merchant.search", SEARCH_TTL),
                () -> loadSearch(keyword, category, sort, lat, lng));
    }

    private List<SearchResultVO> loadSearch(String keyword, String category, String sort,
                                            BigDecimal lat, BigDecimal lng) {
        String normalizedKeyword = normalize(keyword);
        String normalizedCategory = category == null ? "" : category.trim();
        List<Merchant> merchants = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getStatus, "active")
                        .eq(!normalizedCategory.isEmpty(), Merchant::getCategory, normalizedCategory)
        );

        List<SearchResultVO> result = new ArrayList<>();
        Map<Long, Integer> merchantSalesMap = new HashMap<>();

        for (Merchant merchant : merchants) {
            List<Product> products = productMapper.selectList(
                    new LambdaQueryWrapper<Product>()
                            .eq(Product::getMerchantId, merchant.getId())
                            .eq(Product::getStatus, "active")
            );

            boolean hasKeyword = !normalizedKeyword.isEmpty();
            boolean merchantMatched = !hasKeyword || matches(merchant.getName(), normalizedKeyword)
                    || matches(merchant.getTags(), normalizedKeyword);

            List<Product> matchedProducts = hasKeyword
                    ? products.stream()
                            .filter(p -> matches(p.getName(), normalizedKeyword))
                            .collect(Collectors.toList())
                    : products;

            if (hasKeyword && !merchantMatched && matchedProducts.isEmpty()) {
                continue;
            }

            List<Product> displayProducts = hasKeyword && merchantMatched && matchedProducts.isEmpty()
                    ? products
                    : matchedProducts;

            String distance = calculateDistance(merchant, lat, lng);

            List<SearchResultVO.ProductItem> productItems = displayProducts.stream().map(p ->
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

            List<String> tags = new ArrayList<>();
            if (merchant.getTags() != null && !merchant.getTags().isEmpty()) {
                String[] tagArr = merchant.getTags().split(",");
                for (String t : tagArr) {
                    tags.add(t.trim());
                }
            }

            String feeText;
            if (merchant.getDeliveryFee() != null && merchant.getDeliveryFee().compareTo(BigDecimal.ZERO) > 0) {
                feeText = "配送费¥" + merchant.getDeliveryFee();
            } else {
                feeText = "免配送费";
            }

            String salesText;
            if (merchant.getMonthlySales() != null) {
                salesText = "月售" + merchant.getMonthlySales();
            } else {
                salesText = "月售0";
            }

            SearchResultVO vo = SearchResultVO.builder()
                    .id(merchant.getId())
                    .name(merchant.getName())
                    .category(merchant.getCategory())
                    .rating(merchant.getRating())
                    .sales(salesText)
                    .distance(distance)
                    .fee(feeText)
                    .open(true)
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String searchKey(String keyword, String category, String sort, BigDecimal lat, BigDecimal lng) {
        return "la:merchant:search:k:" + keyPart(keyword)
                + ":c:" + keyPart(category)
                + ":sort:" + keyPart(sort)
                + ":lat:" + decimalKey(lat)
                + ":lng:" + decimalKey(lng)
                + ":v1";
    }

    private String keyPart(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return URLEncoder.encode(value.trim().toLowerCase(), StandardCharsets.UTF_8);
    }

    private String decimalKey(BigDecimal value) {
        return value == null ? "_" : value.stripTrailingZeros().toPlainString();
    }

    private boolean matches(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
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
