package com.example.demo.search.controller;

import com.example.demo.common.Result;
import com.example.demo.search.dto.SearchResultVO;
import com.example.demo.search.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 搜索控制器
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

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
