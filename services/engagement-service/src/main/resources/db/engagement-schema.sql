CREATE DATABASE IF NOT EXISTS `engagement_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `engagement_db`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `message`;
DROP TABLE IF EXISTS `review`;

CREATE TABLE `review` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `merchant_id` BIGINT NOT NULL,
    `product_id` BIGINT DEFAULT NULL,
    `rating` TINYINT NOT NULL DEFAULT 5,
    `content` VARCHAR(500) DEFAULT NULL,
    `images` TEXT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_review_order` (`order_id`),
    KEY `idx_review_user` (`user_id`),
    KEY `idx_review_merchant` (`merchant_id`),
    KEY `idx_review_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `message` (
    `id` BIGINT NOT NULL,
    `sender_id` BIGINT NOT NULL,
    `sender_type` VARCHAR(20) NOT NULL,
    `receiver_id` BIGINT NOT NULL,
    `receiver_type` VARCHAR(20) NOT NULL,
    `order_id` BIGINT DEFAULT NULL,
    `content` VARCHAR(500) NOT NULL,
    `is_read` TINYINT(1) DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_message_sender` (`sender_id`, `sender_type`),
    KEY `idx_message_receiver` (`receiver_id`, `receiver_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
