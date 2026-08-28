package com.example.demo.common.controller;

import com.example.demo.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
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
 * Shared health endpoint for each microservice.
 */
@RestController
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment env;

    @GetMapping("/api/health")
    public ResponseEntity<Result<Map<String, Object>>> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("status", "UP");
        info.put("application", env.getProperty("spring.application.name"));
        String version = env.getProperty("APP_VERSION");
        if (version == null || version.isBlank()) {
            Package pkg = HealthController.class.getPackage();
            version = pkg == null ? null : pkg.getImplementationVersion();
        }
        info.put("version", version == null || version.isBlank() ? "dev" : version);
        info.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        info.put("javaVersion", Runtime.version().toString());
        boolean databaseUp = true;

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

        HttpStatus httpStatus = databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(Result.success(info));
    }
}
