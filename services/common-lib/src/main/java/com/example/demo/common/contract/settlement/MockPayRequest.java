package com.example.demo.common.contract.settlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MockPayRequest {

    @NotNull(message = "orderId不能为空")
    private Long orderId;

    @NotNull(message = "amount不能为空")
    private BigDecimal amount;

    @NotBlank(message = "payMethod不能为空")
    private String payMethod;

    private String transactionId;
}
