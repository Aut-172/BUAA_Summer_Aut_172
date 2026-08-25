package com.example.demo.recommend.controller;

import com.example.demo.common.Result;
import com.example.demo.recommend.dto.RecommendVO;
import com.example.demo.recommend.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐控制器
 */
@Tag(name = "推荐服务")
@RestController
@RequestMapping("/api")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @Operation(summary = "获取推荐商家列表")
    @GetMapping("/recommend")
    public Result<List<RecommendVO>> recommend(
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng) {
        List<RecommendVO> results = recommendService.getRecommendations(lat, lng);
        return Result.success(results);
    }
}
