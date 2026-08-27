SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `user_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `merchant_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `order_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `settlement_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `fulfillment_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `engagement_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `user_db`;

DROP TABLE IF EXISTS `user_favorite_merchant`;
DROP TABLE IF EXISTS `cart`;
DROP TABLE IF EXISTS `address`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `admin`;

CREATE TABLE `admin` (
    `id` BIGINT NOT NULL,
    `username` VARCHAR(50) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL,
    `username` VARCHAR(50) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `phone` VARCHAR(20) DEFAULT NULL,
    `nickname` VARCHAR(50) DEFAULT NULL,
    `avatar` VARCHAR(500) DEFAULT NULL,
    `role` VARCHAR(20) NOT NULL DEFAULT 'consumer',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `address` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `phone` VARCHAR(20) NOT NULL,
    `detail` VARCHAR(255) NOT NULL,
    `longitude` DECIMAL(10,7) DEFAULT NULL,
    `latitude` DECIMAL(10,7) DEFAULT NULL,
    `is_default` TINYINT(1) DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_address_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `cart` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `merchant_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `price` DECIMAL(10,2) NOT NULL,
    `image` VARCHAR(500) DEFAULT NULL,
    `quantity` INT NOT NULL DEFAULT 1,
    `spec_label` VARCHAR(50) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_cart_user` (`user_id`),
    KEY `idx_cart_merchant` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_favorite_merchant` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `merchant_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_favorite_merchant` (`user_id`, `merchant_id`),
    KEY `idx_user_favorite_user` (`user_id`),
    KEY `idx_user_favorite_merchant` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `admin` (`id`, `username`, `password`) VALUES
(1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
(2, 'gl1', '$2a$10$8gvXzERfkOBMLd8FPqynkOuuWO234A3CsRT19wTmc9DbXjzvdijue');

INSERT INTO `user` (`id`, `username`, `password`, `phone`, `nickname`, `role`, `status`) VALUES
(10001, 'demo', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138001', 'Demo User', 'consumer', 'active');

INSERT INTO `user_favorite_merchant` (`id`, `user_id`, `merchant_id`) VALUES
(70001, 10001, 20001);

USE `merchant_db`;

DROP TABLE IF EXISTS `product_spec`;
DROP TABLE IF EXISTS `spec_group`;
DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `merchant`;

CREATE TABLE `merchant` (
    `id` BIGINT NOT NULL,
    `username` VARCHAR(50) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `phone` VARCHAR(20) DEFAULT NULL,
    `address` VARCHAR(255) DEFAULT NULL,
    `longitude` DECIMAL(10,7) DEFAULT NULL,
    `latitude` DECIMAL(10,7) DEFAULT NULL,
    `business_hours` VARCHAR(100) DEFAULT '09:00-22:00',
    `category` VARCHAR(50) DEFAULT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `avatar` VARCHAR(500) DEFAULT NULL,
    `tags` VARCHAR(255) DEFAULT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending',
    `rating` DECIMAL(2,1) DEFAULT 0.0,
    `monthly_sales` INT DEFAULT 0,
    `min_delivery_fee` DECIMAL(10,2) DEFAULT 0.00,
    `delivery_fee` DECIMAL(10,2) DEFAULT 5.00,
    `delivery_radius` INT DEFAULT 5,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_username` (`username`),
    UNIQUE KEY `uk_merchant_phone` (`phone`),
    KEY `idx_merchant_status` (`status`),
    KEY `idx_merchant_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `category` (
    `id` BIGINT NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `parent_id` BIGINT DEFAULT NULL,
    `sort_order` INT DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product` (
    `id` BIGINT NOT NULL,
    `merchant_id` BIGINT NOT NULL,
    `category_id` BIGINT DEFAULT NULL,
    `name` VARCHAR(100) NOT NULL,
    `image` VARCHAR(500) DEFAULT NULL,
    `price` DECIMAL(10,2) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `monthly_sales` INT DEFAULT 0,
    `stock` INT NOT NULL DEFAULT 0,
    `type` VARCHAR(20) NOT NULL DEFAULT 'delivery',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active',
    `gallery` TEXT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_merchant` (`merchant_id`),
    KEY `idx_product_category` (`category_id`),
    KEY `idx_product_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `spec_group` (
    `id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `values` TEXT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_spec_group_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product_spec` (
    `id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `label` VARCHAR(50) NOT NULL,
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `stock` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_spec_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`) VALUES
(1, 'Food', NULL, 1),
(2, 'Cafe', NULL, 2),
(3, 'Service', NULL, 3);

INSERT INTO `merchant` (`id`, `username`, `password`, `name`, `phone`, `address`, `longitude`, `latitude`, `business_hours`, `category`, `description`, `avatar`, `tags`, `status`, `rating`, `monthly_sales`, `min_delivery_fee`, `delivery_fee`, `delivery_radius`) VALUES
(20001, 'merchant1', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', 'Campus Kitchen', '13800138002', 'No. 18 College Road', 116.4600000, 39.9100000, '09:00-22:00', 'Food', 'Fast meals and rice bowls for campus delivery.', 'https://picsum.photos/seed/m1/400/300', 'fast,hot', 'active', 4.5, 1280, 20.00, 5.00, 5),
(20002, 'merchant2', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', 'Tea Corner', '13800138003', 'No. 1 Science Park', 116.3100000, 39.9800000, '10:00-22:00', 'Cafe', 'Fresh tea, coffee and desserts.', 'https://picsum.photos/seed/m2/400/300', 'tea,dessert', 'active', 4.8, 2560, 15.00, 3.00, 3);

INSERT INTO `product` (`id`, `merchant_id`, `category_id`, `name`, `image`, `price`, `description`, `monthly_sales`, `stock`, `type`, `status`, `gallery`) VALUES
(30001, 20001, 1, 'Braised Pork Rice', 'https://picsum.photos/seed/p1/400/300', 22.00, 'Classic lunch rice bowl.', 320, 100, 'delivery', 'active', '[]'),
(30002, 20001, 1, 'Kung Pao Chicken Rice', 'https://picsum.photos/seed/p2/400/300', 24.00, 'Spicy chicken with peanuts and rice.', 280, 100, 'delivery', 'active', '[]'),
(30003, 20002, 2, 'Bubble Milk Tea', 'https://picsum.photos/seed/p3/400/300', 12.00, 'Signature bubble milk tea.', 800, 200, 'delivery', 'active', '[]'),
(30004, 20002, 2, 'Tiramisu', 'https://picsum.photos/seed/p4/400/300', 28.00, 'Fresh tiramisu dessert cup.', 120, 30, 'delivery', 'active', '[]');

INSERT INTO `spec_group` (`id`, `product_id`, `name`, `values`) VALUES
(1, 30001, 'Size', '["Large(+3)","Standard"]'),
(2, 30003, 'Sugar', '["100%","70%","30%","0%"]'),
(3, 30003, 'Ice', '["Hot","Normal","Cold"]');

INSERT INTO `product_spec` (`id`, `product_id`, `label`, `price`, `stock`) VALUES
(1, 30001, 'Large', 3.00, 100),
(2, 30001, 'Standard', 0.00, 100),
(3, 30003, '100%', 0.00, 200),
(4, 30003, '70%', 0.00, 200),
(5, 30003, '30%', 0.00, 200),
(6, 30003, '0%', 0.00, 200),
(7, 30003, 'Hot', 0.00, 200),
(8, 30003, 'Normal', 0.00, 200),
(9, 30003, 'Cold', 0.00, 200);

USE `order_db`;

DROP TABLE IF EXISTS `group_coupon`;
DROP TABLE IF EXISTS `order_item`;
DROP TABLE IF EXISTS `orders`;

CREATE TABLE `orders` (
    `id` BIGINT NOT NULL,
    `order_no` VARCHAR(50) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `merchant_id` BIGINT NOT NULL,
    `rider_id` BIGINT DEFAULT NULL,
    `type` VARCHAR(20) NOT NULL DEFAULT 'delivery',
    `total_amount` DECIMAL(10,2) NOT NULL,
    `actual_amount` DECIMAL(10,2) NOT NULL,
    `delivery_fee` DECIMAL(10,2) DEFAULT 0.00,
    `discount` DECIMAL(10,2) DEFAULT 0.00,
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending_payment',
    `address_id` BIGINT DEFAULT NULL,
    `address_detail` VARCHAR(500) DEFAULT NULL,
    `buyer_remark` VARCHAR(200) DEFAULT NULL,
    `coupon_id` BIGINT DEFAULT NULL,
    `paid_at` DATETIME DEFAULT NULL,
    `completed_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_no` (`order_no`),
    KEY `idx_orders_user` (`user_id`),
    KEY `idx_orders_merchant` (`merchant_id`),
    KEY `idx_orders_rider` (`rider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `order_item` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `price` DECIMAL(10,2) NOT NULL,
    `quantity` INT NOT NULL DEFAULT 1,
    `image` VARCHAR(500) DEFAULT NULL,
    `spec_label` VARCHAR(50) DEFAULT NULL,
    `subtotal` DECIMAL(10,2) NOT NULL,
    `reviewed` TINYINT(1) DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_item_order` (`order_id`),
    KEY `idx_order_item_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `group_coupon` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `order_item_id` BIGINT DEFAULT NULL,
    `code` VARCHAR(10) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending_use',
    `expire_at` DATETIME DEFAULT NULL,
    `used_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_group_coupon_order` (`order_id`),
    KEY `idx_group_coupon_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE `settlement_db`;

DROP TABLE IF EXISTS `payment`;
DROP TABLE IF EXISTS `user_coupon`;
DROP TABLE IF EXISTS `coupon`;

CREATE TABLE `coupon` (
    `id` BIGINT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `discount` DECIMAL(10,2) NOT NULL,
    `threshold` DECIMAL(10,2) NOT NULL,
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME NOT NULL,
    `total_count` INT NOT NULL DEFAULT 0,
    `claimed_count` INT NOT NULL DEFAULT 0,
    `limit_per_user` INT NOT NULL DEFAULT 1,
    `status` VARCHAR(20) NOT NULL DEFAULT 'unreleased',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_coupon` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `coupon_id` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'unused',
    `claimed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `used_at` DATETIME DEFAULT NULL,
    `order_id` BIGINT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_coupon_user` (`user_id`),
    KEY `idx_user_coupon_coupon` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `payment` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `amount` DECIMAL(10,2) NOT NULL,
    `pay_method` VARCHAR(20) DEFAULT 'ALIPAY',
    `transaction_id` VARCHAR(100) DEFAULT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `pay_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_payment_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `coupon` (`id`, `name`, `discount`, `threshold`, `start_time`, `end_time`, `total_count`, `claimed_count`, `limit_per_user`, `status`) VALUES
(60001, 'New User 10 Off', 10.00, 30.00, '2025-01-01 00:00:00', '2027-12-31 23:59:59', 1000, 0, 1, 'released'),
(60002, 'Spend 50 Save 15', 15.00, 50.00, '2025-01-01 00:00:00', '2027-12-31 23:59:59', 500, 0, 2, 'released');

USE `fulfillment_db`;

DROP TABLE IF EXISTS `rider`;

CREATE TABLE `rider` (
    `id` BIGINT NOT NULL,
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
    UNIQUE KEY `uk_rider_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `rider` (`id`, `name`, `password`, `phone`, `status`, `service_area`) VALUES
(40001, 'rider01', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138004', 'active', 'Campus and Science Park');

USE `engagement_db`;

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
