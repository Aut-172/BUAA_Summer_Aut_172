package com.example.demo.payment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    private BigDecimal amount;
    private String payMethod;
    private String transactionId;
    private String status;
    private LocalDateTime payTime;
}
