package com.example.demo.order.client;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ServiceNames.USER_SERVICE, path = "/internal/users")
public interface UserClient {

    @DeleteMapping("/{userId}/cart")
    Result<Void> clearCartByMerchantResult(@PathVariable Long userId, @RequestParam Long merchantId);

    default void clearCartByMerchant(Long userId, Long merchantId) {
        Result<Void> result = clearCartByMerchantResult(userId, merchantId);
        if (result == null) {
            throw new BusinessException(503, "用户服务暂不可用");
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
    }
}
