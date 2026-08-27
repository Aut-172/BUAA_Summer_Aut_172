package com.example.demo.common.contract.merchant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ProductQuoteRequest {

    private String requestId;

    @NotNull(message = "merchantId不能为空")
    private Long merchantId;

    @Valid
    @NotEmpty(message = "商品项不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "productId不能为空")
        private Long productId;

        private String specLabel;

        @NotNull(message = "quantity不能为空")
        @Min(value = 1, message = "quantity必须大于0")
        private Integer quantity;
    }
}
