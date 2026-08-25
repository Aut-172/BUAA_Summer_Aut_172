package com.example.demo.search.controller;

import com.example.demo.common.Result;
import com.example.demo.search.dto.SearchResultVO;
import com.example.demo.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 搜索控制器
 */
@Tag(name = "搜索服务")
@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "复合搜索")
    @GetMapping("/search")
    public Result<List<SearchResultVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "rating") String sort,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng) {
        List<SearchResultVO> results = searchService.search(keyword, category, sort, lat, lng);
        return Result.success(results);
    }
}
