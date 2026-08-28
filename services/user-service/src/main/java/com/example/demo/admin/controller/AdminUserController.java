package com.example.demo.admin.controller;

import com.example.demo.admin.dto.AdminUserVO;
import com.example.demo.admin.service.AdminUserService;
import com.example.demo.common.Result;
import com.example.demo.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public PageResult<AdminUserVO> listUsers(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status) {
        return adminUserService.listUsers(page, pageSize, keyword, status);
    }

    @DeleteMapping("/{id}")
    public Result<AdminUserVO> freezeUser(@PathVariable Long id) {
        return Result.success(adminUserService.freezeUser(id));
    }

    @PutMapping("/{id}/unfreeze")
    public Result<AdminUserVO> unfreezeUser(@PathVariable Long id) {
        return Result.success(adminUserService.unfreezeUser(id));
    }
}
