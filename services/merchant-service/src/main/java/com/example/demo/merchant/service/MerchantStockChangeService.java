package com.example.demo.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.merchant.entity.MerchantStockChangeRecord;
import com.example.demo.merchant.mapper.MerchantStockChangeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantStockChangeService {

    private final MerchantStockChangeMapper merchantStockChangeMapper;

    public MerchantStockChangeRecord findByRequestId(String requestId) {
        return merchantStockChangeMapper.selectOne(
                new LambdaQueryWrapper<MerchantStockChangeRecord>()
                        .eq(MerchantStockChangeRecord::getRequestId, requestId)
                        .last("LIMIT 1")
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createProcessing(MerchantStockChangeRecord record) {
        merchantStockChangeMapper.insert(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProcessing(MerchantStockChangeRecord record) {
        merchantStockChangeMapper.updateById(record);
    }

    public void updateFinished(String requestId, String status, String message) {
        MerchantStockChangeRecord record = findByRequestId(requestId);
        if (record == null) {
            return;
        }
        record.setStatus(status);
        record.setMessage(message);
        merchantStockChangeMapper.updateById(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String requestId, String message) {
        MerchantStockChangeRecord record = findByRequestId(requestId);
        if (record == null) {
            return;
        }
        record.setStatus("failed");
        record.setMessage(message);
        merchantStockChangeMapper.updateById(record);
    }
}
