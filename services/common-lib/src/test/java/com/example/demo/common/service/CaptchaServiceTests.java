package com.example.demo.common.service;

import com.example.demo.common.dto.CaptchaVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CaptchaServiceTests {

    @Test
    void localFallbackGeneratesPngCaptchaAndVerifiesCaseInsensitivelyOnce() {
        CaptchaService captchaService = new CaptchaService();
        ReflectionTestUtils.setField(captchaService, "redisAvailable", false);

        CaptchaVO captcha = captchaService.generate();
        Map<?, ?> cache = (Map<?, ?>) ReflectionTestUtils.getField(captchaService, "localCache");
        Object entry = cache.get(captcha.getKey());
        String code = (String) ReflectionTestUtils.getField(entry, "code");

        assertThat(captcha.getKey()).isNotBlank();
        assertThat(captcha.getImage()).startsWith("data:image/png;base64,");
        assertThat(captchaService.verify(captcha.getKey(), code.toLowerCase())).isTrue();
        assertThat(captchaService.verify(captcha.getKey(), code)).isFalse();
    }

    @Test
    void verifyRejectsBlankOrMissingCaptcha() {
        CaptchaService captchaService = new CaptchaService();
        ReflectionTestUtils.setField(captchaService, "redisAvailable", false);

        assertThat(captchaService.verify(null, "abcd")).isFalse();
        assertThat(captchaService.verify("missing", null)).isFalse();
        assertThat(captchaService.verify("missing", "abcd")).isFalse();
    }
}
