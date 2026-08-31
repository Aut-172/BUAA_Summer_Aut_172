package com.example.demo.review.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ImageStorageService {

    String store(MultipartFile file, String scene, String fileName, String contentType) throws IOException;
}
