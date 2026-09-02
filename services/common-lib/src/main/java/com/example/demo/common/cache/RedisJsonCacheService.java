package com.example.demo.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
public class RedisJsonCacheService {

    private final CacheProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public RedisJsonCacheService(CacheProperties properties,
                                 ObjectMapper objectMapper,
                                 ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public <T> T getOrLoad(String key, TypeReference<T> typeReference, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = get(key, typeReference);
        if (cached.isPresent()) {
            return cached.get();
        }
        T value = loader.get();
        put(key, value, ttl);
        return value;
    }

    public <T> T getOrLoad(String key, Class<T> valueType, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = get(key, valueType);
        if (cached.isPresent()) {
            return cached.get();
        }
        T value = loader.get();
        put(key, value, ttl);
        return value;
    }

    public <T> Optional<T> get(String key, TypeReference<T> typeReference) {
        if (!available()) {
            return Optional.empty();
        }
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(payload, typeReference));
        } catch (Exception e) {
            log.warn("读取 Redis 缓存失败，key={}", key, e);
            return Optional.empty();
        }
    }

    public <T> Optional<T> get(String key, Class<T> valueType) {
        if (!available()) {
            return Optional.empty();
        }
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(payload, valueType));
        } catch (Exception e) {
            log.warn("读取 Redis 缓存失败，key={}", key, e);
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (!available() || value == null) {
            return;
        }
        try {
            Duration actualTtl = withJitter(ttl == null ? properties.getDefaultTtl() : ttl);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), actualTtl);
        } catch (Exception e) {
            log.warn("写入 Redis 缓存失败，key={}", key, e);
        }
    }

    public void delete(String key) {
        if (!available()) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("删除 Redis 缓存失败，key={}", key, e);
        }
    }

    public void delete(Collection<String> keys) {
        if (!available() || keys == null || keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("批量删除 Redis 缓存失败，keys={}", keys, e);
        }
    }

    public void deleteByPattern(String pattern) {
        if (!available() || !StringUtils.hasText(pattern)) {
            return;
        }
        try {
            Set<String> keys = scanKeys(pattern);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("按模式删除 Redis 缓存失败，pattern={}", pattern, e);
        }
    }

    public boolean available() {
        return properties.isEnabled() && redisTemplate != null;
    }

    private Duration withJitter(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return properties.getDefaultTtl();
        }
        double ratio = properties.getTtlJitterRatio();
        if (ratio <= 0) {
            return ttl;
        }
        long baseMillis = ttl.toMillis();
        long maxOffset = Math.max(1L, Math.round(baseMillis * ratio));
        long offset = ThreadLocalRandom.current().nextLong(-maxOffset, maxOffset + 1);
        return Duration.ofMillis(Math.max(1000L, baseMillis + offset));
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
