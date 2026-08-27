package com.example.demo.rider.controller;

import com.example.demo.auth.entity.Rider;
import com.example.demo.common.Result;
import com.example.demo.rider.service.RiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/riders")
@RequiredArgsConstructor
public class RiderInternalController {

    private final RiderService riderService;

    @GetMapping("/{riderId}")
    public Result<Rider> getRider(@PathVariable Long riderId) {
        return Result.success(riderService.getProfile(riderId));
    }
}
