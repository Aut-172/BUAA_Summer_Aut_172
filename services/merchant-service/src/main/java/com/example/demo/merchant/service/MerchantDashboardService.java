package com.example.demo.merchant.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.common.contract.merchant.MerchantDashboardStats;
import com.example.demo.merchant.client.OrderSummaryClient;
import com.example.demo.merchant.dto.MerchantDashboardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantDashboardService {

    private final OrderSummaryClient orderSummaryClient;
    private final MerchantDashboardFallbackProvider fallbackProvider;

    public MerchantDashboardVO getDashboard(Long merchantId) {
        try {
            Result<MerchantDashboardStats> result = orderSummaryClient.getMerchantDashboardResult(merchantId);
            if (result == null) {
                log.warn("Order summary dependency returned an empty response for merchant {}", merchantId);
                return fallbackProvider.orderSummaryUnavailable("empty response");
            }
            if (result.getCode() == 200) {
                MerchantDashboardStats stats = result.getData();
                if (stats == null) {
                    log.warn("Order summary dependency returned success without data for merchant {}", merchantId);
                    return fallbackProvider.orderSummaryUnavailable("empty dashboard data");
                }
                return MerchantDashboardVO.normal(stats);
            }
            if (result.getCode() >= 500 || result.getCode() == 503) {
                log.warn("Order summary dependency returned code {} for merchant {}: {}",
                        result.getCode(), merchantId, result.getMessage());
                return fallbackProvider.orderSummaryUnavailable("remote code " + result.getCode());
            }
            throw new BusinessException(result.getCode(), result.getMessage());
        } catch (BusinessException ex) {
            if (ex.getCode() >= 500 || ex.getCode() == 503) {
                log.warn("Order summary dependency returned business error {} for merchant {}: {}",
                        ex.getCode(), merchantId, ex.getMessage());
                return fallbackProvider.orderSummaryUnavailable("remote code " + ex.getCode());
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Order summary dependency failed for merchant {}", merchantId, ex);
            return fallbackProvider.orderSummaryUnavailable(ex.getClass().getSimpleName());
        }
    }
}
