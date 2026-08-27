package com.example.demo.user.client;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.ServiceNames;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@FeignClient(name = ServiceNames.MERCHANT_SERVICE, path = "/internal")
public interface MerchantCatalogClient {

    @PostMapping("/products/quote")
    Result<ProductQuoteResponse> getProductQuoteResult(@RequestBody ProductQuoteRequest request);

    @GetMapping("/merchants/{merchantId}")
    Result<MerchantSnapshot> getMerchantResult(@PathVariable Long merchantId);

    @GetMapping("/products/{productId}")
    Result<ProductSnapshot> getProductResult(@PathVariable Long productId);

    default ProductQuote getProductQuote(Long merchantId, Long productId, String specLabel, Integer quantity) {
        ProductQuoteRequest.Item item = new ProductQuoteRequest.Item();
        item.setProductId(productId);
        item.setSpecLabel(specLabel);
        item.setQuantity(quantity != null && quantity > 0 ? quantity : 1);

        ProductQuoteRequest request = new ProductQuoteRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setMerchantId(merchantId);
        request.setItems(List.of(item));

        ProductQuoteResponse response = unwrap(getProductQuoteResult(request), "商品服务暂不可用");
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            throw BusinessException.notFound("商品不存在");
        }
        ProductQuoteResponse.Item quoted = response.getItems().get(0);
        ProductQuote quote = new ProductQuote();
        quote.setProductId(quoted.getProductId());
        quote.setMerchantId(quoted.getMerchantId());
        quote.setName(quoted.getName());
        quote.setPrice(quoted.getUnitPrice());
        quote.setImage(quoted.getImage());
        quote.setSpecLabel(quoted.getSpecLabel());
        quote.setStatus(Boolean.TRUE.equals(quoted.getActive()) ? "active" : "inactive");
        quote.setAvailable(Boolean.TRUE.equals(response.getAvailable())
                && Boolean.TRUE.equals(quoted.getActive())
                && Boolean.TRUE.equals(quoted.getStockEnough()));
        quote.setMessage(quoted.getMessage());
        return quote;
    }

    default MerchantSnapshot getMerchant(Long merchantId) {
        return unwrap(getMerchantResult(merchantId), "商家服务暂不可用");
    }

    default ProductSnapshot getProduct(Long productId) {
        return unwrap(getProductResult(productId), "商家服务暂不可用");
    }

    private <T> T unwrap(Result<T> result, String unavailableMessage) {
        if (result == null) {
            throw new BusinessException(503, unavailableMessage);
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    @Data
    class ProductQuoteRequest {
        private String requestId;
        private Long merchantId;
        private List<Item> items = new ArrayList<>();

        @Data
        public static class Item {
            private Long productId;
            private String specLabel;
            private Integer quantity;
        }
    }

    @Data
    class ProductQuoteResponse {
        private String requestId;
        private Long merchantId;
        private Boolean available;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private List<Item> items = new ArrayList<>();
        private List<String> messages = new ArrayList<>();

        @Data
        public static class Item {
            private Long productId;
            private Long merchantId;
            private String name;
            private String image;
            private String specLabel;
            private BigDecimal unitPrice;
            private Integer quantity;
            private Integer stock;
            private BigDecimal subtotal;
            private Boolean active;
            private Boolean stockEnough;
            private String message;
        }
    }

    @Data
    class ProductQuote {
        private Long productId;
        private Long merchantId;
        private String name;
        private BigDecimal price;
        private String image;
        private String specLabel;
        private String status;
        private Boolean available;
        private String message;
    }

    @Data
    class ProductSnapshot {
        private Long id;
        private Long merchantId;
        private String name;
        private String image;
        private BigDecimal price;
        private String status;
    }

    @Data
    class MerchantSnapshot {
        private Long id;
        private String name;
        private String category;
        private String description;
        private String avatar;
        private String tags;
        private String status;
        private BigDecimal rating;
        private Integer monthlySales;
        private BigDecimal minDeliveryFee;
        private BigDecimal deliveryFee;
    }
}
