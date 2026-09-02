package com.example.demo.merchant.service;

import com.example.demo.common.cache.RedisJsonCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantCatalogCacheService {

    private final RedisJsonCacheService cacheService;

    public void invalidatePublicCatalog() {
        cacheService.deleteByPattern("la:merchant:catalog:*");
        cacheService.deleteByPattern("la:merchant:search:*");
        cacheService.deleteByPattern("la:merchant:recommend:*");
    }
}
