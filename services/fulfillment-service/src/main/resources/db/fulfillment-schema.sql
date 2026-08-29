CREATE DATABASE IF NOT EXISTS `fulfillment_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fulfillment_db`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `rider`;

CREATE TABLE `rider` (
    `id` BIGINT NOT NULL,
    `username` VARCHAR(50) NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `phone` VARCHAR(20) NOT NULL,
    `id_card` VARCHAR(20) DEFAULT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending',
    `audit_opinion` VARCHAR(255) DEFAULT NULL,
    `service_area` VARCHAR(100) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rider_username` (`username`),
    UNIQUE KEY `uk_rider_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `rider` (`id`, `username`, `name`, `password`, `phone`, `status`, `service_area`) VALUES
(40001, 'rider01', 'rider01', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138004', 'active', 'Campus and Science Park');

SET FOREIGN_KEY_CHECKS = 1;
