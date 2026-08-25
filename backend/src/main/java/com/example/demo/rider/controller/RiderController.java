package com.example.demo.rider.controller;

import com.example.demo.auth.entity.Rider;
import com.example.demo.common.Result;
import com.example.demo.rider.dto.RiderProfileUpdateRequest;
import com.example.demo.rider.dto.RiderTaskUpdateRequest;
import com.example.demo.rider.dto.RiderTaskVO;
import com.example.demo.rider.service.RiderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 骑手控制器
 */
@RestController
@RequestMapping("/api/rider")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;

    private Long getRiderId(HttpServletRequest request) {
        return (Long) request.getAttribute("riderId");
    }

    /**
     * 获取骑手任务列表
     * GET /api/rider/tasks
     */
    @GetMapping("/tasks")
    public Result<RiderTaskVO> getTasks(HttpServletRequest request) {
        return Result.success(riderService.getTasks(getRiderId(request)));
    }

    @GetMapping("/profile")
    public Result<Rider> getProfile(HttpServletRequest request) {
        return Result.success(riderService.getProfile(getRiderId(request)));
    }

    @PutMapping("/profile")
    public Result<Rider> updateProfile(HttpServletRequest request,
                                       @RequestBody RiderProfileUpdateRequest body) {
        return Result.success(riderService.updateProfile(getRiderId(request), body));
    }

    /**
     * 更新骑手任务
     * PUT /api/rider/tasks/{id}
     */
    @PutMapping("/tasks/{id}")
    public Result<RiderTaskVO.TaskItem> updateTask(HttpServletRequest request,
                                                    @PathVariable Long id,
                                                    @RequestBody RiderTaskUpdateRequest body) {
        return Result.success(riderService.updateTask(getRiderId(request), id, body));
    }
}
