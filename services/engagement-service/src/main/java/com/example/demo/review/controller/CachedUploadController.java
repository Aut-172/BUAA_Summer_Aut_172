package com.example.demo.review.controller;

import com.example.demo.common.BusinessException;
import com.example.demo.common.cache.CacheProperties;
import com.example.demo.common.cache.RedisJsonCacheService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class CachedUploadController {

    private static final Duration IMAGE_METADATA_TTL = Duration.ofHours(1);
    private static final Duration IMAGE_BLOB_TTL = Duration.ofMinutes(10);

    private final RedisJsonCacheService cacheService;
    private final CacheProperties cacheProperties;

    @Value("${app.upload.review-dir:uploads/reviews}")
    private String reviewUploadDir;

    @Value("${app.upload.root-dir:uploads}")
    private String uploadRootDir;

    @GetMapping("/uploads/{scene}/{fileName:.+}")
    public ResponseEntity<Resource> getUploadedImage(@PathVariable String scene,
                                                     @PathVariable String fileName,
                                                     HttpServletRequest request) throws IOException {
        String safeScene = normalizeScene(scene);
        String safeFileName = normalizeFileName(fileName);
        Path imagePath = resolveImagePath(safeScene, safeFileName);
        if (!Files.isRegularFile(imagePath)) {
            return ResponseEntity.notFound().build();
        }

        ImageMetadata metadata = loadMetadata(safeScene, safeFileName, imagePath);
        if (metadata.getEtag().equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(headers(metadata))
                    .build();
        }

        Resource resource = loadResource(safeScene, safeFileName, imagePath, metadata);
        return ResponseEntity.ok()
                .headers(headers(metadata))
                .body(resource);
    }

    private ImageMetadata loadMetadata(String scene, String fileName, Path imagePath) throws IOException {
        String key = imageMetadataKey(scene, fileName);
        if (cacheProperties.getImage().isMetadataEnabled()) {
            ImageMetadata cached = cacheService.get(key, ImageMetadata.class).orElse(null);
            if (cached != null) {
                return cached;
            }
        }
        ImageMetadata metadata = readMetadata(imagePath);
        if (cacheProperties.getImage().isMetadataEnabled()) {
            cacheService.put(key, metadata, cacheProperties.ttl("image.metadata", IMAGE_METADATA_TTL));
        }
        return metadata;
    }

    private Resource loadResource(String scene, String fileName, Path imagePath, ImageMetadata metadata) throws IOException {
        if (!cacheProperties.getImage().isBlobEnabled()
                || metadata.getSize() > cacheProperties.getImage().getMaxBlobSizeBytes()) {
            return new FileSystemResource(imagePath);
        }

        String key = imageBlobKey(scene, fileName, metadata);
        CachedImageBlob cached = cacheService.get(key, CachedImageBlob.class).orElse(null);
        if (cached != null && cached.getContent() != null) {
            return new ByteArrayResource(cached.getContent());
        }

        byte[] content = Files.readAllBytes(imagePath);
        cacheService.put(key, new CachedImageBlob(content), cacheProperties.ttl("image.blob", IMAGE_BLOB_TTL));
        return new ByteArrayResource(content);
    }

    private ImageMetadata readMetadata(Path imagePath) throws IOException {
        long size = Files.size(imagePath);
        long lastModified = Files.getLastModifiedTime(imagePath).toMillis();
        String contentType = Files.probeContentType(imagePath);
        if (contentType == null || !contentType.startsWith("image/")) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return new ImageMetadata(size, lastModified, contentType, "\"" + size + "-" + lastModified + "\"");
    }

    private HttpHeaders headers(ImageMetadata metadata) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(metadata.getSize());
        headers.setContentType(parseMediaType(metadata.getContentType()));
        headers.setETag(metadata.getEtag());
        headers.setLastModified(metadata.getLastModified());
        headers.setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic().immutable());
        return headers;
    }

    private MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private Path resolveImagePath(String scene, String fileName) {
        Path baseDir;
        if ("reviews".equals(scene)) {
            baseDir = Paths.get(reviewUploadDir).toAbsolutePath().normalize();
        } else {
            Path root = Paths.get(uploadRootDir).toAbsolutePath().normalize();
            baseDir = root.resolve(scene).normalize();
            if (!baseDir.startsWith(root)) {
                throw BusinessException.badRequest("图片目录不合法");
            }
        }
        Path target = baseDir.resolve(fileName).normalize();
        if (!target.startsWith(baseDir)) {
            throw BusinessException.badRequest("图片文件名不合法");
        }
        return target;
    }

    private String normalizeScene(String scene) {
        String value = scene == null || scene.isBlank() ? "common" : scene.trim().toLowerCase();
        if (!value.matches("[a-z0-9_-]{1,32}")) {
            throw BusinessException.badRequest("图片场景不合法");
        }
        return value;
    }

    private String normalizeFileName(String fileName) {
        String value = fileName == null ? "" : fileName.trim();
        if (value.isEmpty() || value.contains("/") || value.contains("\\") || value.contains("..")) {
            throw BusinessException.badRequest("图片文件名不合法");
        }
        return value;
    }

    private String imageMetadataKey(String scene, String fileName) {
        return "la:engagement:image:meta:" + scene + ":" + fileName + ":v1";
    }

    private String imageBlobKey(String scene, String fileName, ImageMetadata metadata) {
        return "la:engagement:image:blob:" + scene + ":" + fileName + ":" + metadata.getSize()
                + ":" + metadata.getLastModified() + ":v1";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ImageMetadata {
        private long size;
        private long lastModified;
        private String contentType;
        private String etag;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CachedImageBlob {
        private byte[] content;
    }
}
