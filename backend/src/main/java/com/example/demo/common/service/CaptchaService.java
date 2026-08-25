package com.example.demo.common.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.example.demo.common.dto.CaptchaVO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务
 * 生成图形验证码，验证码文本存入 Redis（或本地内存兜底），返回 Base64 图片
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    /** 验证码字符集（排除易混淆字符 0O1lI） */
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";

    /** 验证码宽度 */
    private static final int WIDTH = 130;

    /** 验证码高度 */
    private static final int HEIGHT = 48;

    /** 验证码字符数 */
    private static final int CODE_LENGTH = 4;

    /** 干扰线数量 */
    private static final int LINE_COUNT = 30;

    /** 验证码 Redis 前缀 */
    private static final String CAPTCHA_PREFIX = "captcha:";

    /** 验证码有效期（分钟） */
    private static final long EXPIRE_MINUTES = 5;

    /** 本地内存兜底缓存（Redis 不可用时使用） */
    private final ConcurrentHashMap<String, CacheEntry> localCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private boolean redisAvailable = true;

    @PostConstruct
    public void init() {
        if (redisTemplate == null) {
            redisAvailable = false;
            log.warn("RedisTemplate 未注入，验证码将使用本地内存缓存");
        } else {
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                log.info("Redis 连接正常，验证码使用 Redis 缓存");
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis 不可用，验证码将使用本地内存缓存: {}", e.getMessage());
            }
        }
    }

    /**
     * 生成验证码
     */
    public CaptchaVO generate() {
        // 生成随机验证码文本
        String code = RandomUtil.randomString(CHARS, CODE_LENGTH);

        // 生成唯一 key
        String key = IdUtil.fastSimpleUUID();

        if (redisAvailable) {
            try {
                // 存入 Redis
                redisTemplate.opsForValue().set(CAPTCHA_PREFIX + key, code, EXPIRE_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Redis 写入失败，切换到本地缓存: {}", e.getMessage());
                redisAvailable = false;
                localCache.put(key, new CacheEntry(code, System.currentTimeMillis() + EXPIRE_MINUTES * 60 * 1000));
            }
        } else {
            // 本地内存缓存
            localCache.put(key, new CacheEntry(code, System.currentTimeMillis() + EXPIRE_MINUTES * 60 * 1000));
        }

        // 生成图片 Base64
        String imageBase64 = generateImage(code);

        return new CaptchaVO(key, imageBase64);
    }

    /**
     * 校验验证码（校验后立即删除，防止重复使用）
     */
    public boolean verify(String key, String code) {
        if (key == null || code == null) {
            return false;
        }

        if (redisAvailable) {
            try {
                String redisKey = CAPTCHA_PREFIX + key;
                String storedCode = redisTemplate.opsForValue().get(redisKey);
                if (storedCode == null) {
                    return false;
                }
                // 校验后立即删除（一次性使用）
                redisTemplate.delete(redisKey);
                return storedCode.equalsIgnoreCase(code.trim());
            } catch (Exception e) {
                log.warn("Redis 读取失败，切换到本地缓存: {}", e.getMessage());
                redisAvailable = false;
            }
        }

        // 本地内存缓存兜底
        CacheEntry entry = localCache.remove(key);
        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() > entry.expireAt) {
            return false; // 已过期
        }
        return entry.code.equalsIgnoreCase(code.trim());
    }

    /**
     * 本地缓存条目
     */
    private static class CacheEntry {
        final String code;
        final long expireAt;

        CacheEntry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    /**
     * 生成验证码图片并返回 Base64
     */
    private String generateImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            // 抗锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 背景
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 干扰线
            g.setColor(Color.LIGHT_GRAY);
            for (int i = 0; i < LINE_COUNT; i++) {
                int x1 = RandomUtil.randomInt(0, WIDTH);
                int y1 = RandomUtil.randomInt(0, HEIGHT);
                int x2 = RandomUtil.randomInt(0, WIDTH);
                int y2 = RandomUtil.randomInt(0, HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }

            // 绘制验证码字符
            int fontSize = 28;
            g.setFont(new Font("Arial", Font.BOLD | Font.ITALIC, fontSize));
            char[] chars = code.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                // 随机颜色
                g.setColor(new Color(
                        RandomUtil.randomInt(30, 180),
                        RandomUtil.randomInt(30, 180),
                        RandomUtil.randomInt(30, 180)
                ));
                // 随机旋转（-15° ~ 15°）
                double angle = RandomUtil.randomDouble(-Math.PI / 12, Math.PI / 12);
                g.rotate(angle, 20 + i * 30, HEIGHT / 2.0);
                g.drawString(String.valueOf(chars[i]), 15 + i * 30, 34);
                g.rotate(-angle, 20 + i * 30, HEIGHT / 2.0);
            }

            // 随机噪点
            g.setColor(Color.GRAY);
            for (int i = 0; i < 50; i++) {
                int x = RandomUtil.randomInt(0, WIDTH);
                int y = RandomUtil.randomInt(0, HEIGHT);
                g.fillRect(x, y, 1, 1);
            }

            // 输出为 PNG Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] bytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);

        } catch (Exception e) {
            throw new RuntimeException("验证码图片生成失败", e);
        } finally {
            g.dispose();
        }
    }
}
