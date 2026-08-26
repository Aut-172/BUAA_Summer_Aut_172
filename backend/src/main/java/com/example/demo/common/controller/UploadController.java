package com.example.demo.common.controller;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private static final int MAX_IMAGE_COUNT = 6;
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;
    private static final List<String> ALLOWED_SCENES = List.of("avatars", "products", "reviews");

    @Value("${app.upload.root-dir:uploads}")
    private String uploadRootDir;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<String>> uploadImages(@RequestParam("files") List<MultipartFile> files,
                                             @RequestParam(defaultValue = "common") String scene) {
        if (files == null || files.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的图片");
        }
        if (files.size() > MAX_IMAGE_COUNT) {
            throw BusinessException.badRequest("图片不能超过" + MAX_IMAGE_COUNT + "张");
        }

        String safeScene = ALLOWED_SCENES.contains(scene) ? scene : "common";
        Path uploadDir = Paths.get(uploadRootDir, safeScene).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw BusinessException.badRequest("图片目录创建失败");
        }

        List<String> urls = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> saveImage(file, uploadDir, safeScene))
                .toList();

        if (urls.isEmpty()) {
            throw BusinessException.badRequest("请选择有效的图片");
        }
        return Result.success(urls);
    }

    private String saveImage(MultipartFile file, Path uploadDir, String scene) {
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw BusinessException.badRequest("单张图片不能超过5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw BusinessException.badRequest("只能上传图片文件");
        }

        String fileName = UUID.randomUUID() + resolveImageExtension(file.getOriginalFilename(), contentType);
        Path target = uploadDir.resolve(fileName).normalize();
        if (!target.startsWith(uploadDir)) {
            throw BusinessException.badRequest("图片文件名不合法");
        }

        try {
            Files.copy(file.getInputStream(), target);
        } catch (IOException e) {
            throw BusinessException.badRequest("图片上传失败");
        }
        return "/uploads/" + scene + "/" + fileName;
    }

    private String resolveImageExtension(String originalFilename, String contentType) {
        String name = originalFilename == null ? "" : originalFilename.trim().toLowerCase();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < name.length() - 1) {
            String extension = name.substring(dotIndex);
            if (List.of(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(extension)) {
                return extension;
            }
        }

        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
