package com.example.demo.payment.client;

import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import com.example.demo.common.contract.order.MarkPaidRequest;
import com.example.demo.common.contract.order.OrderInternalResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ServiceNames.ORDER_SERVICE, path = "/internal/orders")
public interface OrderClient {

    @PostMapping("/{orderId}/mark-paid")
    Result<OrderInternalResponse> markPaid(@PathVariable Long orderId,
                                           @Valid @RequestBody MarkPaidRequest request);
}
