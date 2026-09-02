package com.example.demo.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.cache.CacheProperties;
import com.example.demo.common.cache.RedisJsonCacheService;
import com.example.demo.engagement.client.MerchantCatalogClient;
import com.example.demo.engagement.client.OrderClient;
import com.example.demo.engagement.client.UserClient;
import com.example.demo.engagement.event.EngagementEventPublisher;
import com.example.demo.review.dto.ReviewRequest;
import com.example.demo.review.dto.ReviewVO;
import com.example.demo.review.entity.Review;
import com.example.demo.review.mapper.ReviewMapper;
import com.example.demo.review.storage.ImageStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_CONTENT_LENGTH = 200;
    private static final int MAX_IMAGE_COUNT = 6;
    private static final int MAX_IMAGE_URL_LENGTH = 500;
    private static final long DEFAULT_MAX_IMAGE_SIZE = 20 * 1024 * 1024L;
    private static final Duration REVIEW_LIST_TTL = Duration.ofMinutes(5);
    private static final Duration RATING_TTL = Duration.ofMinutes(10);
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<ReviewVO>> REVIEW_VO_LIST_TYPE = new TypeReference<>() {
    };

    private final ReviewMapper reviewMapper;
    private final OrderClient orderClient;
    private final UserClient userClient;
    private final MerchantCatalogClient merchantCatalogClient;
    private final EngagementEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ImageStorageService imageStorageService;
    private final RedisJsonCacheService cacheService;
    private final CacheProperties cacheProperties;

    @Value("${app.upload.max-image-size-bytes:20971520}")
    private long maxImageSizeBytes;

    @Transactional
    public List<ReviewVO> submitReview(Long userId, ReviewRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw BusinessException.badRequest("订单信息不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw BusinessException.badRequest("评价商品不能为空");
        }

        OrderClient.OrderSnapshot order = orderClient.getParticipantOrder(request.getOrderId(), userId, "user");
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!isCompletedStatus(order.getStatus())) {
            throw BusinessException.badRequest("只有已完成订单才能评价");
        }

        Long existingCount = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderId, request.getOrderId())
        );
        if (existingCount > 0) {
            throw BusinessException.badRequest("该订单已评价，不可重复评价");
        }

        List<OrderClient.OrderItemSnapshot> orderItems = order.getItems() == null ? List.of() : order.getItems();
        Map<Long, OrderClient.OrderItemSnapshot> itemMap = orderItems.stream()
                .filter(item -> item.getProductId() != null)
                .collect(Collectors.toMap(OrderClient.OrderItemSnapshot::getProductId, item -> item));

        List<Review> reviews = new ArrayList<>();
        for (ReviewRequest.ItemReview itemReview : request.getItems()) {
            if (itemReview.getProductId() == null) {
                throw BusinessException.badRequest("商品ID不能为空");
            }
            if (!itemMap.containsKey(itemReview.getProductId())) {
                throw BusinessException.badRequest("商品ID " + itemReview.getProductId() + " 不属于该订单");
            }
            if (itemReview.getRating() == null || itemReview.getRating() < 1 || itemReview.getRating() > 5) {
                throw BusinessException.badRequest("评分必须在1-5之间");
            }
            if (itemReview.getContent() != null && itemReview.getContent().length() > MAX_CONTENT_LENGTH) {
                throw BusinessException.badRequest("评价内容不能超过" + MAX_CONTENT_LENGTH + "字");
            }

            Review review = new Review();
            review.setOrderId(request.getOrderId());
            review.setUserId(userId);
            review.setMerchantId(order.getMerchantId());
            review.setProductId(itemReview.getProductId());
            review.setRating(itemReview.getRating());
            review.setContent(itemReview.getContent());
            review.setImages(encodeImages(normalizeImages(itemReview.getImages())));
            reviewMapper.insert(review);
            reviews.add(review);
        }

        eventPublisher.publishReviewCreated(reviews);
        orderClient.markReviewedItems(request.getOrderId(),
                reviews.stream().map(Review::getProductId).filter(Objects::nonNull).collect(Collectors.toList()));
        invalidateReviewCaches(reviews);

        return reviews.stream().map(this::toReviewVO).collect(Collectors.toList());
    }

    public List<ReviewVO> getProductReviews(Long productId) {
        return cacheService.getOrLoad(productReviewsKey(productId), REVIEW_VO_LIST_TYPE,
                cacheProperties.ttl("engagement.review-list", REVIEW_LIST_TTL),
                () -> loadProductReviews(productId));
    }

    private List<ReviewVO> loadProductReviews(Long productId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProductId, productId)
                        .orderByDesc(Review::getCreateTime)
        );
        return buildReviewVOs(reviews);
    }

    public List<ReviewVO> getMerchantReviews(Long merchantId) {
        return cacheService.getOrLoad(merchantReviewsKey(merchantId), REVIEW_VO_LIST_TYPE,
                cacheProperties.ttl("engagement.review-list", REVIEW_LIST_TTL),
                () -> loadMerchantReviews(merchantId));
    }

    private List<ReviewVO> loadMerchantReviews(Long merchantId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getMerchantId, merchantId)
                        .orderByDesc(Review::getCreateTime)
        );
        return buildReviewVOs(reviews);
    }

    public List<ReviewVO> getUserReviews(Long userId) {
        return cacheService.getOrLoad(userReviewsKey(userId), REVIEW_VO_LIST_TYPE,
                cacheProperties.ttl("engagement.review-list", REVIEW_LIST_TTL),
                () -> loadUserReviews(userId));
    }

    private List<ReviewVO> loadUserReviews(Long userId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreateTime)
        );
        return buildReviewVOs(reviews);
    }

    public BigDecimal getMerchantRating(Long merchantId) {
        return cacheService.getOrLoad(merchantRatingKey(merchantId), BigDecimal.class,
                cacheProperties.ttl("engagement.rating", RATING_TTL),
                () -> loadMerchantRating(merchantId));
    }

    private BigDecimal loadMerchantRating(Long merchantId) {
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

    public List<String> uploadReviewImages(List<MultipartFile> files) {
        return uploadImages(files, "reviews");
    }

    public List<String> uploadImages(List<MultipartFile> files, String scene) {
        if (files == null || files.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的图片");
        }
        if (files.size() > MAX_IMAGE_COUNT) {
            throw BusinessException.badRequest("图片不能超过" + MAX_IMAGE_COUNT + "张");
        }

        String safeScene = normalizeUploadScene(scene);
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            long maxSize = maxImageSizeBytes > 0 ? maxImageSizeBytes : DEFAULT_MAX_IMAGE_SIZE;
            if (file.getSize() > maxSize) {
                throw BusinessException.badRequest("单张图片不能超过" + (maxSize / 1024 / 1024) + "MB");
            }
            String contentType = file.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                throw BusinessException.badRequest("只能上传图片文件");
            }

            String fileName = UUID.randomUUID() + resolveImageExtension(file.getOriginalFilename(), contentType);
            try {
                urls.add(imageStorageService.store(file, safeScene, fileName, contentType));
            } catch (IOException e) {
                throw BusinessException.badRequest("图片上传失败");
            }
        }

        if (urls.isEmpty()) {
            throw BusinessException.badRequest("请选择有效的图片");
        }
        return urls;
    }

    private String normalizeUploadScene(String scene) {
        String value = scene == null || scene.isBlank() ? "common" : scene.trim().toLowerCase();
        if (!value.matches("[a-z0-9_-]{1,32}")) {
            throw BusinessException.badRequest("图片场景不合法");
        }
        return value;
    }

    private List<ReviewVO> buildReviewVOs(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, UserClient.UserSnapshot> userMap = reviews.stream()
                .map(Review::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .map(this::getCachedUserSnapshot)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserClient.UserSnapshot::getId, Function.identity(), (left, right) -> left));

        Map<Long, MerchantCatalogClient.ProductSnapshot> productMap = reviews.stream()
                .map(Review::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .map(this::getCachedProductSnapshot)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(MerchantCatalogClient.ProductSnapshot::getId, Function.identity(), (left, right) -> left));

        return reviews.stream().map(review -> {
            ReviewVO.ReviewVOBuilder builder = ReviewVO.builder()
                    .id(review.getId())
                    .orderId(review.getOrderId())
                    .userId(review.getUserId())
                    .merchantId(review.getMerchantId())
                    .productId(review.getProductId())
                    .rating(review.getRating())
                    .content(review.getContent())
                    .images(decodeImages(review.getImages()))
                    .createTime(review.getCreateTime());

            UserClient.UserSnapshot user = userMap.get(review.getUserId());
            if (user != null) {
                builder.userName(user.getNickname());
                builder.userAvatar(user.getAvatar());
            }
            MerchantCatalogClient.ProductSnapshot product = productMap.get(review.getProductId());
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

    private UserClient.UserSnapshot getCachedUserSnapshot(Long userId) {
        return cacheService.getOrLoad(userSnapshotKey(userId), UserClient.UserSnapshot.class,
                cacheProperties.ttl("engagement.snapshot", SNAPSHOT_TTL),
                () -> userClient.getUser(userId));
    }

    private MerchantCatalogClient.ProductSnapshot getCachedProductSnapshot(Long productId) {
        return cacheService.getOrLoad(productSnapshotKey(productId), MerchantCatalogClient.ProductSnapshot.class,
                cacheProperties.ttl("engagement.snapshot", SNAPSHOT_TTL),
                () -> merchantCatalogClient.getProduct(productId));
    }

    private void invalidateReviewCaches(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        reviews.stream().map(Review::getProductId).filter(Objects::nonNull).distinct()
                .map(this::productReviewsKey).forEach(keys::add);
        reviews.stream().map(Review::getMerchantId).filter(Objects::nonNull).distinct().forEach(merchantId -> {
            keys.add(merchantReviewsKey(merchantId));
            keys.add(merchantRatingKey(merchantId));
        });
        reviews.stream().map(Review::getUserId).filter(Objects::nonNull).distinct()
                .map(this::userReviewsKey).forEach(keys::add);
        cacheService.delete(keys);
    }

    private String productReviewsKey(Long productId) {
        return "la:engagement:reviews:product:" + productId + ":v1";
    }

    private String merchantReviewsKey(Long merchantId) {
        return "la:engagement:reviews:merchant:" + merchantId + ":v1";
    }

    private String userReviewsKey(Long userId) {
        return "la:engagement:reviews:user:" + userId + ":v1";
    }

    private String merchantRatingKey(Long merchantId) {
        return "la:engagement:rating:merchant:" + merchantId + ":v1";
    }

    private String userSnapshotKey(Long userId) {
        return "la:engagement:snapshot:user:" + userId + ":v1";
    }

    private String productSnapshotKey(Long productId) {
        return "la:engagement:snapshot:product:" + productId + ":v1";
    }

    private boolean isCompletedStatus(String status) {
        return "completed".equals(status) || "已完成".equals(status);
    }

    private List<String> normalizeImages(List<String> images) {
        if (images == null) {
            return List.of();
        }
        List<String> normalized = images.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (normalized.size() > MAX_IMAGE_COUNT) {
            throw BusinessException.badRequest("评价图片不能超过" + MAX_IMAGE_COUNT + "张");
        }
        if (normalized.stream().anyMatch(item -> item.length() > MAX_IMAGE_URL_LENGTH)) {
            throw BusinessException.badRequest("评价图片地址不能超过" + MAX_IMAGE_URL_LENGTH + "字");
        }
        return normalized;
    }

    private String resolveImageExtension(String originalFilename, String contentType) {
        String name = originalFilename == null ? "" : originalFilename.trim().toLowerCase();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < name.length() - 1) {
            String extension = name.substring(dotIndex);
            if (List.of(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(extension)) {
                return extension;
            }
        }
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String encodeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            throw BusinessException.badRequest("评价图片格式不正确");
        }
    }

    private List<String> decodeImages(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(images, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
