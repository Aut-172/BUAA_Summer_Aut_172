package com.example.demo.common.controller;

import com.example.demo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口
 * 用于检测服务是否正常运行
 */
@Tag(name = "系统健康检查")
@RestController
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private Environment env;

    @GetMapping("/api/health")
    @Operation(summary = "服务健康检查")
    public ResponseEntity<Result<Map<String, Object>>> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("status", "UP");
        info.put("application", env.getProperty("spring.application.name"));
        info.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        info.put("javaVersion", Runtime.version().toString());
        boolean databaseUp = true;

        // 检查数据库连接
        try (Connection conn = dataSource.getConnection()) {
            info.put("database", conn.getMetaData().getDatabaseProductName()
                    + " " + conn.getMetaData().getDatabaseProductVersion());
            info.put("databaseStatus", "UP");
        } catch (Exception e) {
            databaseUp = false;
            info.put("status", "DOWN");
            info.put("databaseStatus", "DOWN");
            info.put("databaseError", e.getMessage());
        }

        // 检查 Redis 连接
        if (redisTemplate != null) {
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                info.put("redisStatus", "UP");
            } catch (Exception e) {
                info.put("redisStatus", "DOWN");
                info.put("redisError", e.getMessage());
            }
        } else {
            info.put("redisStatus", "NOT_CONFIGURED");
        }

        HttpStatus httpStatus = databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(Result.success(info));
    }
}
