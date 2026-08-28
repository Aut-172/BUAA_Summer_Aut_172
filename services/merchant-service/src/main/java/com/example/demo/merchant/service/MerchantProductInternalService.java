package com.example.demo.merchant.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.common.contract.merchant.ProductQuoteRequest;
import com.example.demo.common.contract.merchant.ProductQuoteResponse;
import com.example.demo.common.contract.merchant.StockChangeRequest;
import com.example.demo.common.contract.merchant.StockChangeResponse;
import com.example.demo.merchant.entity.MerchantStockChangeRecord;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.entity.ProductSpec;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.merchant.mapper.ProductSpecMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantProductInternalService {

    private static final String STATUS_ACTIVE = "active";

    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;
    private final MerchantStockChangeService merchantStockChangeService;

    public ProductQuoteResponse quote(ProductQuoteRequest request) {
        ProductQuoteResponse response = new ProductQuoteResponse();
        response.setRequestId(request.getRequestId());
        response.setMerchantId(request.getMerchantId());

        Merchant merchant = merchantMapper.selectById(request.getMerchantId());
        if (merchant == null || !STATUS_ACTIVE.equals(merchant.getStatus())) {
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

    @Transactional
    public StockChangeResponse reserve(StockChangeRequest request) {
        return changeStock(request, true);
    }

    @Transactional
    public StockChangeResponse release(StockChangeRequest request) {
        return changeStock(request, false);
    }

    public StockChangeResponse getChangeStatus(String requestId) {
        MerchantStockChangeRecord record = merchantStockChangeService.findByRequestId(requestId);
        if (record == null) {
            throw BusinessException.notFound("库存变更记录不存在");
        }
        return buildStatusResponse(record);
    }

    private StockChangeResponse changeStock(StockChangeRequest request, boolean reserve) {
        String requestId = normalizeRequestId(request);
        MerchantStockChangeRecord existing = merchantStockChangeService.findByRequestId(requestId);
        if (existing != null) {
            if (reserve && "reserve".equals(existing.getAction()) && "reserved".equals(existing.getStatus())) {
                return buildCachedResponse(existing, reserve);
            }
            if (!reserve && "release".equals(existing.getAction()) && "released".equals(existing.getStatus())) {
                return buildCachedResponse(existing, reserve);
            }
            if (!reserve && "reserve".equals(existing.getAction()) && "reserved".equals(existing.getStatus())) {
                existing.setAction("release");
                existing.setStatus("processing");
                existing.setMessage(null);
                merchantStockChangeService.updateProcessing(existing);
            } else if (isFinished(existing.getStatus())) {
                return buildStatusResponse(existing);
            } else {
                return buildStatusResponse(existing);
            }
        }

        MerchantStockChangeRecord record = existing;
        if (record == null) {
            record = new MerchantStockChangeRecord();
            record.setRequestId(requestId);
            record.setMerchantId(request.getMerchantId());
            record.setOrderId(request.getOrderId());
            record.setPayload(describeRequest(request));
            record.setStatus("processing");
            record.setAction(reserve ? "reserve" : "release");
            try {
                merchantStockChangeService.createProcessing(record);
            } catch (RuntimeException ex) {
                MerchantStockChangeRecord cached = merchantStockChangeService.findByRequestId(requestId);
                if (cached != null) {
                    return buildStatusResponse(cached);
                }
                throw ex;
            }
        }

        try {
            StockChangeResponse response = new StockChangeResponse();
            response.setRequestId(requestId);
            response.setMerchantId(request.getMerchantId());
            response.setOrderId(request.getOrderId());

            Merchant merchant = merchantMapper.selectById(request.getMerchantId());
            if (merchant == null) {
                throw BusinessException.notFound("商家不存在");
            }
            if (reserve && !STATUS_ACTIVE.equals(merchant.getStatus())) {
                throw BusinessException.badRequest("商家不存在或已下线");
            }

            for (StockChangeRequest.Item requestItem : request.getItems()) {
                response.getItems().add(changeStockItem(request.getMerchantId(), requestItem, reserve));
            }
            response.setSuccess(true);
            response.setStatus(reserve ? "reserved" : "released");
            response.setMessage(reserve ? "库存预留成功" : "库存释放成功");

            merchantStockChangeService.updateFinished(record.getRequestId(), response.getStatus(), response.getMessage());
            return response;
        } catch (RuntimeException ex) {
            merchantStockChangeService.markFailed(record.getRequestId(), ex.getMessage());
            throw ex;
        }
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

        ProductSpec spec = findSpec(requestItem.getProductId(), requestItem.getSpecLabel());
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

    private StockChangeResponse.Item changeStockItem(Long merchantId, StockChangeRequest.Item requestItem, boolean reserve) {
        StockChangeResponse.Item item = new StockChangeResponse.Item();
        item.setProductId(requestItem.getProductId());
        item.setSpecLabel(requestItem.getSpecLabel());
        item.setQuantity(requestItem.getQuantity());

        Product product = productMapper.selectById(requestItem.getProductId());
        if (product == null || !merchantId.equals(product.getMerchantId())) {
            throw BusinessException.notFound("商品不存在或不属于当前商家");
        }
        if (reserve && !STATUS_ACTIVE.equals(product.getStatus())) {
            throw BusinessException.badRequest("商品已下架");
        }

        ProductSpec spec = findSpec(requestItem.getProductId(), requestItem.getSpecLabel());
        if (StrUtil.isNotBlank(requestItem.getSpecLabel()) && spec == null) {
            throw BusinessException.badRequest("商品规格不存在");
        }

        if (spec != null) {
            adjustSpecStock(spec.getId(), requestItem.getQuantity(), reserve);
            ProductSpec updatedSpec = productSpecMapper.selectById(spec.getId());
            item.setRemainingStock(updatedSpec == null ? null : updatedSpec.getStock());
        } else {
            adjustProductStock(product.getId(), requestItem.getQuantity(), reserve);
            Product updatedProduct = productMapper.selectById(product.getId());
            item.setRemainingStock(updatedProduct == null ? null : updatedProduct.getStock());
        }

        item.setSuccess(true);
        item.setMessage(reserve ? "库存预留成功" : "库存释放成功");
        return item;
    }

    private void adjustProductStock(Long productId, Integer quantity, boolean reserve) {
        int updated = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .ge(reserve, Product::getStock, quantity)
                .setSql("stock = stock " + (reserve ? "-" : "+") + " " + quantity));
        if (updated == 0) {
            throw BusinessException.badRequest(reserve ? "库存不足" : "库存释放失败");
        }
    }

    private void adjustSpecStock(Long specId, Integer quantity, boolean reserve) {
        int updated = productSpecMapper.update(null, new LambdaUpdateWrapper<ProductSpec>()
                .eq(ProductSpec::getId, specId)
                .ge(reserve, ProductSpec::getStock, quantity)
                .setSql("stock = stock " + (reserve ? "-" : "+") + " " + quantity));
        if (updated == 0) {
            throw BusinessException.badRequest(reserve ? "库存不足" : "库存释放失败");
        }
    }

    private ProductSpec findSpec(Long productId, String specLabel) {
        if (StrUtil.isBlank(specLabel)) {
            return null;
        }
        return productSpecMapper.selectOne(
                new LambdaQueryWrapper<ProductSpec>()
                        .eq(ProductSpec::getProductId, productId)
                        .eq(ProductSpec::getLabel, specLabel)
                        .last("LIMIT 1")
        );
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeRequestId(StockChangeRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            request.setRequestId(UUID.randomUUID().toString());
        }
        return request.getRequestId();
    }

    private boolean isFinished(String status) {
        return "reserved".equals(status) || "released".equals(status);
    }

    private StockChangeResponse buildCachedResponse(MerchantStockChangeRecord record, boolean reserve) {
        StockChangeResponse response = new StockChangeResponse();
        response.setRequestId(record.getRequestId());
        response.setMerchantId(record.getMerchantId());
        response.setOrderId(record.getOrderId());
        response.setSuccess(true);
        response.setStatus(record.getStatus());
        response.setMessage(record.getMessage() == null ? (reserve ? "库存预留成功" : "库存释放成功") : record.getMessage());
        return response;
    }

    private StockChangeResponse buildStatusResponse(MerchantStockChangeRecord record) {
        StockChangeResponse response = new StockChangeResponse();
        response.setRequestId(record.getRequestId());
        response.setMerchantId(record.getMerchantId());
        response.setOrderId(record.getOrderId());
        response.setStatus(record.getStatus());
        response.setSuccess("reserved".equals(record.getStatus()) || "released".equals(record.getStatus()));
        if (record.getMessage() != null) {
            response.setMessage(record.getMessage());
        } else if ("processing".equals(record.getStatus())) {
            response.setMessage("库存变更处理中");
        } else if ("reserved".equals(record.getStatus())) {
            response.setMessage("库存预留成功");
        } else if ("released".equals(record.getStatus())) {
            response.setMessage("库存释放成功");
        } else if ("failed".equals(record.getStatus())) {
            response.setMessage("库存变更失败");
        }
        return response;
    }

    private String describeRequest(StockChangeRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("merchantId=").append(request.getMerchantId());
        builder.append(", orderId=").append(request.getOrderId());
        builder.append(", items=");
        builder.append(request.getItems().stream()
                .map(item -> item.getProductId() + "x" + item.getQuantity() + (item.getSpecLabel() == null ? "" : "(" + item.getSpecLabel() + ")"))
                .collect(java.util.stream.Collectors.joining(",")));
        return builder.toString();
    }
}
