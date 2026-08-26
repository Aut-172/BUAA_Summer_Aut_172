package com.example.demo.review.controller;

import com.example.demo.common.Result;
import com.example.demo.review.dto.ReviewRequest;
import com.example.demo.review.dto.ReviewVO;
import com.example.demo.review.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评价控制器
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    /**
     * 提交评价
     * POST /api/reviews
     */
    @PostMapping("/reviews")
    public Result<List<ReviewVO>> submitReview(HttpServletRequest request,
                                                @RequestBody ReviewRequest body) {
        return Result.success(reviewService.submitReview(getUserId(request), body));
    }

    /**
     * 上传评价图片
     * POST /api/reviews/images
     */
    @PostMapping(value = "/reviews/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<String>> uploadReviewImages(@RequestParam("files") List<MultipartFile> files) {
        return Result.success(reviewService.uploadReviewImages(files));
    }

    /**
     * 获取商品评价列表
     * GET /api/products/{id}/reviews
     */
    @GetMapping("/products/{id}/reviews")
    public Result<List<ReviewVO>> getProductReviews(@PathVariable Long id) {
        return Result.success(reviewService.getProductReviews(id));
    }

    /**
     * 获取商家评价列表
     * GET /api/merchants/{id}/reviews
     */
    @GetMapping("/merchants/{id}/reviews")
    public Result<List<ReviewVO>> getMerchantReviews(@PathVariable Long id) {
        return Result.success(reviewService.getMerchantReviews(id));
    }

    /**
     * 获取商家综合评分
     * GET /api/merchants/{id}/rating
     */
    @GetMapping("/merchants/{id}/rating")
    public Result<BigDecimal> getMerchantRating(@PathVariable Long id) {
        return Result.success(reviewService.getMerchantRating(id));
    }

    /**
     * 获取当前用户的评价列表
     * GET /api/user/reviews
     */
    @GetMapping("/user/reviews")
    public Result<List<ReviewVO>> getUserReviews(HttpServletRequest request) {
        return Result.success(reviewService.getUserReviews(getUserId(request)));
    }
}
