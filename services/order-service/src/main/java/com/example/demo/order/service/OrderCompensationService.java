package com.example.demo.order.service;

import com.example.demo.order.entity.OrderCompensationRecord;
import com.example.demo.order.mapper.OrderCompensationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCompensationService {

    private final OrderCompensationMapper orderCompensationMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String requestId, Long orderId, String action, String targetService, String payload, String status, String message) {
        OrderCompensationRecord record = new OrderCompensationRecord();
        record.setRequestId(requestId);
        record.setOrderId(orderId);
        record.setAction(action);
        record.setTargetService(targetService);
        record.setPayload(payload);
        record.setStatus(status);
        record.setMessage(message);
        orderCompensationMapper.insert(record);
    }
}
