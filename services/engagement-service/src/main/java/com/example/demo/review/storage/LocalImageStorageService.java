package com.example.demo.review.storage;

import com.example.demo.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@ConditionalOnProperty(name = "app.oss.enabled", havingValue = "false", matchIfMissing = true)
public class LocalImageStorageService implements ImageStorageService {

    @Value("${app.upload.review-dir:uploads/reviews}")
    private String reviewUploadDir;

    @Value("${app.upload.root-dir:uploads}")
    private String uploadRootDir;

    @Override
    public String store(MultipartFile file, String scene, String fileName, String contentType) throws IOException {
        Path uploadDir = resolveUploadDir(scene);
        Files.createDirectories(uploadDir);

        Path target = uploadDir.resolve(fileName).normalize();
        if (!target.startsWith(uploadDir)) {
            throw BusinessException.badRequest("图片文件名不合法");
        }
        Files.copy(file.getInputStream(), target);
        return "/uploads/" + scene + "/" + fileName;
    }

    private Path resolveUploadDir(String scene) {
        if ("reviews".equals(scene)) {
            return Paths.get(reviewUploadDir).toAbsolutePath().normalize();
        }
        Path root = Paths.get(uploadRootDir).toAbsolutePath().normalize();
        Path uploadDir = root.resolve(scene).normalize();
        if (!uploadDir.startsWith(root)) {
            throw BusinessException.badRequest("图片目录不合法");
        }
        return uploadDir;
    }
}
