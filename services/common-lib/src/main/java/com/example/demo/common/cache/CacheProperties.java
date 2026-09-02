package com.example.demo.common.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    private boolean enabled = true;

    private Duration defaultTtl = Duration.ofMinutes(10);

    private double ttlJitterRatio = 0.1;

    private Image image = new Image();

    private Map<String, Duration> ttl = new HashMap<>();

    public Duration ttl(String name, Duration fallback) {
        return ttl.getOrDefault(name, fallback == null ? defaultTtl : fallback);
    }

    @Data
    public static class Image {
        private boolean metadataEnabled = true;
        private boolean blobEnabled = true;
        private long maxBlobSizeBytes = 512 * 1024L;
    }
}
