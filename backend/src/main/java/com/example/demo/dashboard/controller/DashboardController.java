package com.example.demo.dashboard.controller;

import com.example.demo.common.Result;
import com.example.demo.dashboard.dto.DashboardVO;
import com.example.demo.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 仪表盘控制器
 */
@Tag(name = "仪表盘")
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "获取仪表盘数据")
    @GetMapping("/dashboard")
    public Result<DashboardVO> getDashboard(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        Long merchantId = (Long) request.getAttribute("merchantId");
        Long riderId = (Long) request.getAttribute("riderId");

        DashboardVO.ConsumerData consumerData = null;
        DashboardVO.MerchantData merchantData = null;
        DashboardVO.RiderData riderData = null;

        // 根据角色返回对应的数据
        if ("consumer".equals(role) && userId != null) {
            consumerData = dashboardService.getConsumerData(userId);
        }
        if ("merchant".equals(role) && merchantId != null) {
            merchantData = dashboardService.getMerchantData(merchantId);
        }
        if ("rider".equals(role) && riderId != null) {
            riderData = dashboardService.getRiderData(riderId);
        }

        DashboardVO dashboard = DashboardVO.builder()
                .consumer(consumerData)
                .merchant(merchantData)
                .rider(riderData)
                .build();

        return Result.success(dashboard);
    }
}
