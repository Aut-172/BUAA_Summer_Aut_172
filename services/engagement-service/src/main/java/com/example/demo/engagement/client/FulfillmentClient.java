package com.example.demo.engagement.client;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = ServiceNames.FULFILLMENT_SERVICE, path = "/internal")
public interface FulfillmentClient {

    @GetMapping("/riders/{riderId}")
    Result<RiderSnapshot> getRiderResult(@PathVariable Long riderId);

    default RiderSnapshot getRider(Long riderId) {
        Result<RiderSnapshot> result = getRiderResult(riderId);
        if (result == null) {
            throw new BusinessException(503, "配送履约服务暂不可用");
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    @Data
    class RiderSnapshot {
        private Long id;
        private String name;
        private String phone;
        private String status;
    }
}
