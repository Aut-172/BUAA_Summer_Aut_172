package com.example.demo.merchant.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.merchant.dto.ProductQuoteRequest;
import com.example.demo.merchant.dto.ProductQuoteResponse;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.entity.ProductSpec;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.merchant.mapper.ProductSpecMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MerchantProductInternalService {

    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;

    public ProductQuoteResponse quote(ProductQuoteRequest request) {
        ProductQuoteResponse response = new ProductQuoteResponse();
        response.setRequestId(request.getRequestId());
        response.setMerchantId(request.getMerchantId());

        Merchant merchant = merchantMapper.selectById(request.getMerchantId());
        if (merchant == null || !"active".equals(merchant.getStatus())) {
            response.setAvailable(false);
            response.getMessages().add("商家不存在或已下线");
            return response;
        }

        BigDecimal total = BigDecimal.ZERO;
        boolean available = true;
        for (ProductQuoteRequest.Item requestItem : request.getItems()) {
            ProductQuoteResponse.Item item = quoteItem(request.getMerchantId(), requestItem);
            response.getItems().add(item);
            if (Boolean.TRUE.equals(item.getActive()) && Boolean.TRUE.equals(item.getStockEnough())) {
                total = total.add(item.getSubtotal());
            } else {
                available = false;
                response.getMessages().add(item.getMessage());
            }
        }

        response.setAvailable(available);
        response.setTotalAmount(total);
        return response;
    }

    private ProductQuoteResponse.Item quoteItem(Long merchantId, ProductQuoteRequest.Item requestItem) {
        ProductQuoteResponse.Item item = new ProductQuoteResponse.Item();
        item.setProductId(requestItem.getProductId());
        item.setSpecLabel(requestItem.getSpecLabel());
        item.setQuantity(requestItem.getQuantity());
        item.setActive(false);
        item.setStockEnough(false);
        item.setSubtotal(BigDecimal.ZERO);

        Product product = productMapper.selectById(requestItem.getProductId());
        if (product == null || !merchantId.equals(product.getMerchantId())) {
            item.setMessage("商品不存在或不属于当前商家");
            return item;
        }

        item.setMerchantId(product.getMerchantId());
        item.setName(product.getName());
        item.setImage(product.getImage());
        item.setUnitPrice(product.getPrice());
        item.setStock(product.getStock());

        if (!"active".equals(product.getStatus())) {
            item.setMessage("商品已下架");
            return item;
        }

        ProductSpec spec = findSpec(requestItem);
        if (StrUtil.isNotBlank(requestItem.getSpecLabel()) && spec == null) {
            item.setMessage("商品规格不存在");
            return item;
        }

        if (spec != null) {
            item.setUnitPrice(product.getPrice().add(nullToZero(spec.getPrice())));
            item.setStock(spec.getStock());
        }

        boolean stockEnough = item.getStock() != null && item.getStock() >= requestItem.getQuantity();
        item.setActive(true);
        item.setStockEnough(stockEnough);
        item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(requestItem.getQuantity())));
        item.setMessage(stockEnough ? "可购买" : "库存不足");
        return item;
    }

    private ProductSpec findSpec(ProductQuoteRequest.Item requestItem) {
        if (StrUtil.isBlank(requestItem.getSpecLabel())) {
            return null;
        }
        return productSpecMapper.selectOne(
                new LambdaQueryWrapper<ProductSpec>()
                        .eq(ProductSpec::getProductId, requestItem.getProductId())
                        .eq(ProductSpec::getLabel, requestItem.getSpecLabel())
                        .last("LIMIT 1")
        );
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
