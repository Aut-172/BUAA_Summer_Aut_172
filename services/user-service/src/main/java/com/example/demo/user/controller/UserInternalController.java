package com.example.demo.user.controller;

import com.example.demo.auth.entity.User;
import com.example.demo.common.Result;
import com.example.demo.user.dto.AddressDTO;
import com.example.demo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public Result<User> getUser(@PathVariable Long userId) {
        return Result.success(userService.getUserSnapshot(userId));
    }

    @GetMapping("/{userId}/addresses/{addressId}")
    public Result<AddressDTO> getAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        return Result.success(userService.getAddress(userId, addressId));
    }

    @DeleteMapping("/{userId}/cart")
    public Result<Void> clearCartByMerchant(@PathVariable Long userId, @RequestParam Long merchantId) {
        userService.clearCartByMerchant(userId, merchantId);
        return Result.success();
    }
}
