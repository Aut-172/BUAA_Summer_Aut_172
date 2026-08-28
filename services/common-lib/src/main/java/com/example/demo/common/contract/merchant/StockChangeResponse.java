package com.example.demo.common.contract.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StockChangeResponse {

    private String requestId;
    private Long merchantId;
    private Long orderId;
    private Boolean success;
    private String status;
    private String message;
    private List<Item> items = new ArrayList<>();
    private List<String> messages = new ArrayList<>();

    @Data
    public static class Item {
        private Long productId;
        private String specLabel;
        private Integer quantity;
        private Integer remainingStock;
        private Boolean success;
        private String message;
    }
}
