package com.example.demo.engagement.client;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = ServiceNames.USER_SERVICE, path = "/internal")
public interface UserClient {

    @GetMapping("/users/{userId}")
    Result<UserSnapshot> getUserResult(@PathVariable Long userId);

    default UserSnapshot getUser(Long userId) {
        Result<UserSnapshot> result = getUserResult(userId);
        if (result == null) {
            throw new BusinessException(503, "用户服务暂不可用");
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    @Data
    class UserSnapshot {
        private Long id;
        private String username;
        private String nickname;
        private String avatar;
        private String status;
    }
}
