package com.example.demo.review.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSchemaMigrator implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!tableExists("review") || columnExists("review", "images")) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE review ADD COLUMN images TEXT DEFAULT NULL");
        log.info("Added missing column review.images");
    }

    private boolean tableExists(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            return exists(metaData.getTables(catalog, null, tableName, null))
                    || exists(metaData.getTables(catalog, null, tableName.toUpperCase(), null))
                    || exists(metaData.getTables(null, null, tableName, null))
                    || exists(metaData.getTables(null, null, tableName.toUpperCase(), null));
        }
    }

    private boolean columnExists(String tableName, String columnName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            return exists(metaData.getColumns(catalog, null, tableName, columnName))
                    || exists(metaData.getColumns(catalog, null, tableName.toUpperCase(), columnName.toUpperCase()))
                    || exists(metaData.getColumns(null, null, tableName, columnName))
                    || exists(metaData.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase()));
        }
    }

    private boolean exists(ResultSet resultSet) throws Exception {
        try (resultSet) {
            return resultSet.next();
        }
    }
}
