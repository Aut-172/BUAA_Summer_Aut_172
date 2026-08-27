package com.example.demo.order.client;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import com.example.demo.common.contract.settlement.CouponLockRequest;
import com.example.demo.common.contract.settlement.CouponLockResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ServiceNames.SETTLEMENT_SERVICE, path = "/internal/coupon-locks")
public interface SettlementCouponClient {

    @PostMapping
    Result<CouponLockResponse> lockResult(@Valid @RequestBody CouponLockRequest request);

    @PostMapping("/{orderId}/release")
    Result<CouponLockResponse> releaseResult(@PathVariable Long orderId);

    @PostMapping("/{orderId}/confirm")
    Result<CouponLockResponse> confirmResult(@PathVariable Long orderId);

    default CouponLockResponse lock(CouponLockRequest request) {
        return unwrap(lockResult(request), "结算服务暂不可用");
    }

    default CouponLockResponse release(Long orderId) {
        return unwrap(releaseResult(orderId), "结算服务暂不可用");
    }

    default CouponLockResponse confirm(Long orderId) {
        return unwrap(confirmResult(orderId), "结算服务暂不可用");
    }

    private CouponLockResponse unwrap(Result<CouponLockResponse> result, String unavailableMessage) {
        if (result == null) {
            throw new BusinessException(503, unavailableMessage);
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
