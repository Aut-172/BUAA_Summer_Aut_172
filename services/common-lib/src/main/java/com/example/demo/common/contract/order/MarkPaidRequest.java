package com.example.demo.common.contract.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MarkPaidRequest {

    @NotBlank(message = "transactionId不能为空")
    private String transactionId;

    @NotBlank(message = "payMethod不能为空")
    private String payMethod;

    @NotNull(message = "amount不能为空")
    private BigDecimal amount;

    private LocalDateTime paidAt;
}
