package com.example.demo;

import com.example.demo.common.BusinessException;
import com.example.demo.engagement.client.MerchantCatalogClient;
import com.example.demo.engagement.client.OrderClient;
import com.example.demo.engagement.client.UserClient;
import com.example.demo.engagement.event.EngagementEventPublisher;
import com.example.demo.review.mapper.ReviewMapper;
import com.example.demo.review.service.ReviewService;
import com.example.demo.review.storage.LocalImageStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ReviewServiceUnitTests {

    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private OrderClient orderClient;
    @Mock
    private UserClient userClient;
    @Mock
    private MerchantCatalogClient merchantCatalogClient;
    @Mock
    private EngagementEventPublisher eventPublisher;

    @TempDir
    private Path tempDir;

    @Test
    void uploadImagesRejectsEmptyFileList() {
        ReviewService reviewService = reviewService();

        assertThatThrownBy(() -> reviewService.uploadImages(List.of(), "chat"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo("请选择要上传的图片");
                });
    }

    @Test
    void uploadImagesRejectsUnsafeSceneName() {
        ReviewService reviewService = reviewService();
        MockMultipartFile image = new MockMultipartFile("files", "meal.png", "image/png", "image".getBytes());

        assertThatThrownBy(() -> reviewService.uploadImages(List.of(image), "../secret"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo("图片场景不合法");
                });
    }

    @Test
    void uploadImagesStoresImageUnderRequestedScene() {
        ReviewService reviewService = reviewService();
        MockMultipartFile image = new MockMultipartFile("files", "meal.png", "image/png", "image".getBytes());

        List<String> urls = reviewService.uploadImages(List.of(image), "chat");

        assertThat(urls).hasSize(1);
        assertThat(urls.get(0)).startsWith("/uploads/chat/").endsWith(".png");
        assertThat(tempDir.resolve("chat")).isDirectory();
    }

    private ReviewService reviewService() {
        ReviewService reviewService = new ReviewService(
                reviewMapper,
                orderClient,
                userClient,
                merchantCatalogClient,
                eventPublisher,
                new ObjectMapper(),
                localImageStorageService()
        );
        ReflectionTestUtils.setField(reviewService, "maxImageSizeBytes", 1024L);
        return reviewService;
    }

    private LocalImageStorageService localImageStorageService() {
        LocalImageStorageService storageService = new LocalImageStorageService();
        ReflectionTestUtils.setField(storageService, "uploadRootDir", tempDir.toString());
        ReflectionTestUtils.setField(storageService, "reviewUploadDir", tempDir.resolve("reviews").toString());
        return storageService;
    }
}
