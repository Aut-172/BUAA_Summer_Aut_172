package com.example.demo.review.storage;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

@Service
@ConditionalOnProperty(name = "app.oss.enabled", havingValue = "true")
public class OssImageStorageService implements ImageStorageService {

    private final OSS ossClient;
    private final String bucketName;
    private final String publicBaseUrl;
    private final String uploadPrefix;

    public OssImageStorageService(
            @Value("${app.oss.endpoint}") String endpoint,
            @Value("${app.oss.bucket}") String bucketName,
            @Value("${app.oss.access-key-id}") String accessKeyId,
            @Value("${app.oss.access-key-secret}") String accessKeySecret,
            @Value("${app.oss.public-base-url:}") String publicBaseUrl,
            @Value("${app.oss.upload-prefix:life-assistant}") String uploadPrefix,
            @Value("${app.oss.cname-enabled:false}") boolean cnameEnabled
    ) {
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSupportCname(cnameEnabled);
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret, configuration);
        this.bucketName = bucketName;
        this.publicBaseUrl = normalizeBaseUrl(StringUtils.hasText(publicBaseUrl)
                ? publicBaseUrl
                : defaultPublicBaseUrl(endpoint, bucketName));
        this.uploadPrefix = normalizePrefix(uploadPrefix);
    }

    @Override
    public String store(MultipartFile file, String scene, String fileName, String contentType) throws IOException {
        String objectKey = objectKey(scene, fileName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(contentType);
        metadata.setCacheControl("public, max-age=31536000");

        ossClient.putObject(bucketName, objectKey, file.getInputStream(), metadata);
        return publicBaseUrl + "/" + objectKey;
    }

    @PreDestroy
    public void close() {
        ossClient.shutdown();
    }

    private String objectKey(String scene, String fileName) {
        String scenePath = scene.replace('_', '-');
        if (uploadPrefix.isBlank()) {
            return scenePath + "/" + fileName;
        }
        return uploadPrefix + "/" + scenePath + "/" + fileName;
    }

    private String normalizeBaseUrl(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizePrefix(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String defaultPublicBaseUrl(String endpoint, String bucketName) {
        URI uri = URI.create(endpoint.startsWith("http") ? endpoint : "https://" + endpoint);
        String scheme = StringUtils.hasText(uri.getScheme()) ? uri.getScheme() : "https";
        return scheme + "://" + bucketName + "." + uri.getHost();
    }
}
