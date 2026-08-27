package com.example.demo.user.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FavoriteMerchantVO {

    private Long favoriteId;
    private Long merchantId;
    private String name;
    private String category;
    private String description;
    private String avatar;
    private String tags;
    private BigDecimal rating;
    private Integer monthlySales;
    private BigDecimal minDeliveryFee;
    private BigDecimal deliveryFee;
    private LocalDateTime favoritedAt;
}
