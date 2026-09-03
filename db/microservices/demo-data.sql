SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `user_db`;

DELETE FROM `user_favorite_merchant` WHERE `id` BETWEEN 70001 AND 70010;
DELETE FROM `cart` WHERE `id` BETWEEN 12001 AND 12010;
DELETE FROM `address` WHERE `id` BETWEEN 11001 AND 11010;
DELETE FROM `admin` WHERE `id` IN (1, 2);
DELETE FROM `user` WHERE `id` IN (10001, 10002, 10003, 10004);


INSERT INTO `admin` (`id`, `username`, `password`) VALUES
(1, 'admin', '$2a$10$HvAnImnOgs9dErjESgWLwuccdHPAWMxqhYxKxfSG1KNyuL6y9.jhe'),
(2, 'gl1', '$2a$10$8gvXzERfkOBMLd8FPqynkOuuWO234A3CsRT19wTmc9DbXjzvdijue');

INSERT INTO `user` (`id`, `username`, `password`, `phone`, `nickname`, `avatar`, `role`, `status`) VALUES
(10001, 'demo', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138001', 'Demo User', NULL, 'consumer', 'active'),
(10002, 'student02', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138021', 'Student Two', NULL, 'consumer', 'active'),
(10003, 'frozen01', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138031', 'Frozen User', NULL, 'consumer', 'frozen');

INSERT INTO `address` (`id`, `user_id`, `name`, `phone`, `detail`, `longitude`, `latitude`, `is_default`) VALUES
(11001, 10001, 'Demo', '13800138001', '北航沙河校区东区 3 号楼 205', 116.3472000, 40.0701000, 1),
(11002, 10001, 'Demo', '13800138001', '昌平区回龙观地铁站附近', 116.3379000, 40.0718000, 0),
(11003, 10002, 'Student', '13800138021', '北航学院路校区宿舍楼', 116.3416000, 39.9999000, 1);

INSERT INTO `cart` (`id`, `user_id`, `merchant_id`, `product_id`, `name`, `price`, `image`, `quantity`, `spec_label`) VALUES
(12001, 10001, 20001, 30001, 'Braised Pork Rice', 22.00, '/oss/life-assistant/demo/products/braised-pork-rice.png', 2, 'Large'),
(12002, 10001, 20002, 30003, 'Bubble Milk Tea', 12.00, '/oss/life-assistant/demo/products/bubble-milk-tea.png', 1, '70% sugar / Normal ice'),
(12003, 10002, 20002, 30005, 'Lemon Tea', 13.00, '/oss/life-assistant/demo/products/lemon-tea.png', 1, NULL);

INSERT INTO `user_favorite_merchant` (`id`, `user_id`, `merchant_id`) VALUES
(70001, 10001, 20001),
(70002, 10001, 20002),
(70003, 10002, 20002);

USE `merchant_db`;

DELETE FROM `merchant_stock_change` WHERE `id` BETWEEN 1 AND 20;
DELETE FROM `product_spec` WHERE `id` BETWEEN 1 AND 20;
DELETE FROM `spec_group` WHERE `id` BETWEEN 1 AND 20;
DELETE FROM `product` WHERE `id` BETWEEN 30001 AND 30020;
DELETE FROM `merchant` WHERE `id` BETWEEN 20001 AND 20020;
DELETE FROM `category` WHERE `id` BETWEEN 1 AND 20;

INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`) VALUES
(1, 'Food', NULL, 1),
(2, 'Drinks', NULL, 2),
(3, 'Service', NULL, 3);

INSERT INTO `merchant` (`id`, `username`, `password`, `name`, `phone`, `address`, `longitude`, `latitude`, `business_hours`, `category`, `description`, `avatar`, `tags`, `status`, `rating`, `monthly_sales`, `min_delivery_fee`, `delivery_fee`, `delivery_radius`) VALUES
(20001, 'merchant1', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', 'Campus Kitchen', '13800138002', 'No. 18 College Road', 116.4600000, 39.9100000, '09:00-22:00', 'Food', 'Fast meals and rice bowls for campus delivery.', '/oss/life-assistant/demo/merchants/campus-kitchen.png', 'fast,hot', 'active', 4.5, 1280, 20.00, 5.00, 5),
(20002, 'merchant2', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', 'Tea Corner', '13800138003', 'No. 1 Science Park', 116.3100000, 39.9800000, '10:00-22:00', 'Drinks', 'Fresh tea, coffee and desserts.', '/oss/life-assistant/demo/merchants/tea-corner.png', 'tea,dessert', 'active', 4.8, 2560, 15.00, 3.00, 3),
(20003, 'merchant3', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', 'Service Hub', '13800138004', 'No. 8 Campus Street', 116.3300000, 39.9600000, '09:00-18:00', 'Service', 'Closed merchant for visibility and filtering demo.', '/oss/life-assistant/demo/merchants/service-hub.png', 'service,closed', 'closed', 4.2, 32, 0.00, 0.00, 2);

INSERT INTO `product` (`id`, `merchant_id`, `category_id`, `name`, `image`, `price`, `description`, `monthly_sales`, `stock`, `type`, `status`, `gallery`) VALUES
(30001, 20001, 1, 'Braised Pork Rice', '/oss/life-assistant/demo/products/braised-pork-rice.png', 22.00, 'Classic lunch rice bowl.', 320, 100, 'delivery', 'active', '[]'),
(30002, 20001, 1, 'Kung Pao Chicken Rice', '/oss/life-assistant/demo/products/kung-pao-chicken-rice.png', 24.00, 'Spicy chicken with peanuts and rice.', 280, 100, 'delivery', 'active', '[]'),
(30003, 20002, 2, 'Bubble Milk Tea', '/oss/life-assistant/demo/products/bubble-milk-tea.png', 12.00, 'Signature bubble milk tea.', 800, 200, 'delivery', 'active', '[]'),
(30004, 20002, 2, 'Tiramisu', '/oss/life-assistant/demo/products/tiramisu.png', 28.00, 'Fresh tiramisu dessert cup.', 120, 30, 'delivery', 'active', '[]'),
(30005, 20002, 2, 'Lemon Tea', '/oss/life-assistant/demo/products/lemon-tea.png', 13.00, 'Fresh lemon tea.', 180, 120, 'delivery', 'active', '[]'),
(30006, 20001, 1, 'Spicy Noodles', '/oss/life-assistant/demo/products/spicy-noodles.png', 18.00, 'Hot noodles for lunch and dinner.', 90, 60, 'delivery', 'active', '[]'),
(30007, 20003, 3, 'Pickup Service', '/oss/life-assistant/demo/products/pickup-service.png', 0.00, 'Closed merchant placeholder product.', 0, 0, 'service', 'inactive', '[]');

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

DELETE FROM `group_coupon` WHERE `id` BETWEEN 1 AND 50;
DELETE FROM `order_compensation` WHERE `id` BETWEEN 1 AND 50;
DELETE FROM `order_item` WHERE `id` BETWEEN 70001 AND 70100;
DELETE FROM `orders` WHERE `id` BETWEEN 70001 AND 70100;

CREATE TABLE IF NOT EXISTS `orders` (
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
    `stock_reserved` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_no` (`order_no`),
    KEY `idx_orders_user` (`user_id`),
    KEY `idx_orders_merchant` (`merchant_id`),
    KEY `idx_orders_rider` (`rider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `order_item` (
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

CREATE TABLE IF NOT EXISTS `order_compensation` (
    `id` BIGINT NOT NULL,
    `request_id` VARCHAR(100) DEFAULT NULL,
    `order_id` BIGINT DEFAULT NULL,
    `action` VARCHAR(50) NOT NULL,
    `target_service` VARCHAR(50) NOT NULL,
    `payload` TEXT DEFAULT NULL,
    `status` VARCHAR(20) NOT NULL,
    `message` VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_compensation_request` (`request_id`),
    KEY `idx_order_compensation_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `group_coupon` (
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

INSERT INTO `orders` (`id`, `order_no`, `user_id`, `merchant_id`, `rider_id`, `type`, `total_amount`, `actual_amount`, `delivery_fee`, `discount`, `status`, `address_id`, `address_detail`, `buyer_remark`, `coupon_id`, `paid_at`, `completed_at`, `stock_reserved`, `create_time`, `update_time`) VALUES
(70001, 'NO202609030001', 10001, 20001, 40001, 'delivery', 49.00, 49.00, 5.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', '少辣', NULL, '2026-09-03 10:16:00', '2026-09-03 11:05:00', 1, '2026-09-03 10:12:00', '2026-09-03 11:05:00'),
(70002, 'NO202609030002', 10001, 20002, NULL, 'delivery', 58.00, 43.00, 3.00, 15.00, 'pending_payment', 11001, '北航沙河校区东区 3 号楼 205', '加冰少糖', 60002, NULL, NULL, 1, '2026-09-03 09:40:00', '2026-09-03 09:40:00'),
(70003, 'NO202609030003', 10002, 20001, 40001, 'delivery', 228.00, 228.00, 5.00, 0.00, 'delivering', 11003, '北航学院路校区宿舍楼', '配送时电话联系', NULL, '2026-09-02 12:02:00', NULL, 1, '2026-09-02 11:58:00', '2026-09-02 12:40:00'),
(70004, 'NO202609030004', 10001, 20001, 40001, 'delivery', 176.00, 176.00, 5.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-09-01 18:05:00', '2026-09-01 18:55:00', 1, '2026-09-01 17:48:00', '2026-09-01 18:55:00'),
(70005, 'NO202609030005', 10002, 20003, NULL, 'service', 0.00, 0.00, 0.00, 0.00, 'canceled', 11003, '北航学院路校区宿舍楼', '服务类演示单', NULL, NULL, NULL, 0, '2026-09-01 16:10:00', '2026-09-01 16:10:00'),
(70006, 'NO202608310001', 10001, 20001, 40001, 'delivery', 104.00, 104.00, 5.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-31 12:10:00', '2026-08-31 12:55:00', 1, '2026-08-31 11:55:00', '2026-08-31 12:55:00'),
(70007, 'NO202608300001', 10002, 20001, 40001, 'delivery', 146.00, 146.00, 5.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-30 13:10:00', '2026-08-30 13:50:00', 1, '2026-08-30 12:58:00', '2026-08-30 13:50:00'),
(70008, 'NO202608290001', 10001, 20001, 40001, 'delivery', 198.00, 198.00, 5.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-29 19:10:00', '2026-08-29 19:58:00', 1, '2026-08-29 18:59:00', '2026-08-29 19:58:00'),
(70009, 'NO202608280001', 10002, 20001, 40001, 'delivery', 174.00, 174.00, 5.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-28 11:45:00', '2026-08-28 12:28:00', 1, '2026-08-28 11:35:00', '2026-08-28 12:28:00'),
(70010, 'NO202608270001', 10001, 20001, 40001, 'delivery', 126.00, 126.00, 5.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-27 12:30:00', '2026-08-27 13:10:00', 1, '2026-08-27 12:20:00', '2026-08-27 13:10:00'),
(70011, 'NO202608260001', 10002, 20001, 40001, 'delivery', 150.00, 150.00, 5.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-26 13:20:00', '2026-08-26 14:02:00', 1, '2026-08-26 13:10:00', '2026-08-26 14:02:00'),
(70012, 'NO202608250001', 10001, 20001, 40001, 'delivery', 92.00, 92.00, 5.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-25 11:15:00', '2026-08-25 11:58:00', 1, '2026-08-25 11:05:00', '2026-08-25 11:58:00'),
(70013, 'NO202608240001', 10002, 20001, 40001, 'delivery', 118.00, 118.00, 5.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-24 12:25:00', '2026-08-24 13:05:00', 1, '2026-08-24 12:14:00', '2026-08-24 13:05:00'),
(70014, 'NO202608230001', 10001, 20001, 40001, 'delivery', 96.00, 96.00, 5.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-23 12:05:00', '2026-08-23 12:42:00', 1, '2026-08-23 11:55:00', '2026-08-23 12:42:00'),
(70015, 'NO202608220001', 10002, 20001, 40001, 'delivery', 74.00, 74.00, 5.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-22 18:10:00', '2026-08-22 18:48:00', 1, '2026-08-22 17:58:00', '2026-08-22 18:48:00'),
(70016, 'NO202608210001', 10001, 20001, 40001, 'delivery', 48.00, 48.00, 5.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-21 11:18:00', '2026-08-21 11:45:00', 1, '2026-08-21 11:08:00', '2026-08-21 11:45:00'),
(70017, 'NO202609030101', 10001, 20002, NULL, 'delivery', 13.00, 13.00, 3.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-09-03 09:30:00', '2026-09-03 10:00:00', 1, '2026-09-03 09:20:00', '2026-09-03 10:00:00'),
(70018, 'NO202609020101', 10002, 20002, NULL, 'delivery', 24.00, 24.00, 3.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-09-02 10:30:00', '2026-09-02 11:00:00', 1, '2026-09-02 10:20:00', '2026-09-02 11:00:00'),
(70019, 'NO202609010101', 10001, 20002, NULL, 'delivery', 41.00, 41.00, 3.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-09-01 14:30:00', '2026-09-01 15:02:00', 1, '2026-09-01 14:18:00', '2026-09-01 15:02:00'),
(70020, 'NO202608310101', 10002, 20002, NULL, 'delivery', 55.00, 55.00, 3.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-31 11:30:00', '2026-08-31 12:00:00', 1, '2026-08-31 11:15:00', '2026-08-31 12:00:00'),
(70021, 'NO202608300101', 10001, 20002, NULL, 'delivery', 36.00, 36.00, 3.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-30 16:25:00', '2026-08-30 16:55:00', 1, '2026-08-30 16:12:00', '2026-08-30 16:55:00'),
(70022, 'NO202608290101', 10002, 20002, NULL, 'delivery', 72.00, 72.00, 3.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-29 12:10:00', '2026-08-29 12:45:00', 1, '2026-08-29 12:00:00', '2026-08-29 12:45:00'),
(70023, 'NO202608280101', 10001, 20002, NULL, 'delivery', 52.00, 52.00, 3.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-28 17:10:00', '2026-08-28 17:42:00', 1, '2026-08-28 16:58:00', '2026-08-28 17:42:00'),
(70024, 'NO202608270101', 10002, 20002, NULL, 'delivery', 84.00, 84.00, 3.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-27 18:00:00', '2026-08-27 18:40:00', 1, '2026-08-27 17:48:00', '2026-08-27 18:40:00'),
(70025, 'NO202608260101', 10001, 20002, NULL, 'delivery', 108.00, 108.00, 3.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-26 15:10:00', '2026-08-26 15:50:00', 1, '2026-08-26 15:00:00', '2026-08-26 15:50:00'),
(70026, 'NO202608250101', 10002, 20002, NULL, 'delivery', 76.00, 76.00, 3.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-25 12:05:00', '2026-08-25 12:36:00', 1, '2026-08-25 11:54:00', '2026-08-25 12:36:00'),
(70027, 'NO202608240101', 10001, 20002, NULL, 'delivery', 63.00, 63.00, 3.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-24 10:15:00', '2026-08-24 10:46:00', 1, '2026-08-24 10:06:00', '2026-08-24 10:46:00'),
(70028, 'NO202608230101', 10002, 20002, NULL, 'delivery', 94.00, 94.00, 3.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-23 13:25:00', '2026-08-23 14:05:00', 1, '2026-08-23 13:12:00', '2026-08-23 14:05:00'),
(70029, 'NO202608220101', 10001, 20002, NULL, 'delivery', 122.00, 122.00, 3.00, 0.00, 'completed', 11001, '北航沙河校区东区 3 号楼 205', NULL, NULL, '2026-08-22 19:00:00', '2026-08-22 19:38:00', 1, '2026-08-22 18:48:00', '2026-08-22 19:38:00'),
(70030, 'NO202608210101', 10002, 20002, NULL, 'delivery', 13.00, 13.00, 3.00, 0.00, 'completed', 11003, '北航学院路校区宿舍楼', NULL, NULL, '2026-08-21 09:55:00', '2026-08-21 10:20:00', 1, '2026-08-21 09:45:00', '2026-08-21 10:20:00');

INSERT INTO `order_item` (`id`, `order_id`, `product_id`, `name`, `price`, `quantity`, `image`, `spec_label`, `subtotal`, `reviewed`) VALUES
(71001, 70001, 30001, 'Braised Pork Rice', 49.00, 1, '/oss/life-assistant/demo/products/braised-pork-rice.png', 'Large', 49.00, 1),
(71002, 70003, 30002, 'Kung Pao Chicken Rice', 228.00, 1, '/oss/life-assistant/demo/products/kung-pao-chicken-rice.png', NULL, 228.00, 0),
(71003, 70004, 30001, 'Braised Pork Rice', 176.00, 1, '/oss/life-assistant/demo/products/braised-pork-rice.png', NULL, 176.00, 1),
(71004, 70005, 30007, 'Pickup Service', 0.00, 1, '/oss/life-assistant/demo/products/pickup-service.png', NULL, 0.00, 0),
(71005, 70006, 30006, 'Spicy Noodles', 104.00, 1, '/oss/life-assistant/demo/products/spicy-noodles.png', NULL, 104.00, 0),
(71006, 70007, 30001, 'Braised Pork Rice', 146.00, 1, '/oss/life-assistant/demo/products/braised-pork-rice.png', NULL, 146.00, 0),
(71007, 70008, 30002, 'Kung Pao Chicken Rice', 198.00, 1, '/oss/life-assistant/demo/products/kung-pao-chicken-rice.png', NULL, 198.00, 0),
(71008, 70009, 30001, 'Braised Pork Rice', 174.00, 1, '/oss/life-assistant/demo/products/braised-pork-rice.png', NULL, 174.00, 0),
(71009, 70010, 30006, 'Spicy Noodles', 126.00, 1, '/oss/life-assistant/demo/products/spicy-noodles.png', NULL, 126.00, 0),
(71010, 70011, 30001, 'Braised Pork Rice', 150.00, 1, '/oss/life-assistant/demo/products/braised-pork-rice.png', NULL, 150.00, 0),
(71011, 70012, 30002, 'Kung Pao Chicken Rice', 92.00, 1, '/oss/life-assistant/demo/products/kung-pao-chicken-rice.png', NULL, 92.00, 0),
(71012, 70013, 30001, 'Braised Pork Rice', 118.00, 1, '/oss/life-assistant/demo/products/braised-pork-rice.png', NULL, 118.00, 0),
(71013, 70014, 30006, 'Spicy Noodles', 96.00, 1, '/oss/life-assistant/demo/products/spicy-noodles.png', NULL, 96.00, 0),
(71014, 70015, 30001, 'Braised Pork Rice', 74.00, 1, '/oss/life-assistant/demo/products/braised-pork-rice.png', NULL, 74.00, 0),
(71015, 70016, 30002, 'Kung Pao Chicken Rice', 48.00, 1, '/oss/life-assistant/demo/products/kung-pao-chicken-rice.png', NULL, 48.00, 0),
(71016, 70017, 30003, 'Bubble Milk Tea', 13.00, 1, '/oss/life-assistant/demo/products/bubble-milk-tea.png', '70% sugar / Normal ice', 13.00, 1),
(71017, 70018, 30005, 'Lemon Tea', 24.00, 1, '/oss/life-assistant/demo/products/lemon-tea.png', NULL, 24.00, 0),
(71018, 70019, 30004, 'Tiramisu', 41.00, 1, '/oss/life-assistant/demo/products/tiramisu.png', NULL, 41.00, 0),
(71019, 70020, 30003, 'Bubble Milk Tea', 55.00, 1, '/oss/life-assistant/demo/products/bubble-milk-tea.png', NULL, 55.00, 0),
(71020, 70021, 30004, 'Tiramisu', 36.00, 1, '/oss/life-assistant/demo/products/tiramisu.png', NULL, 36.00, 0),
(71021, 70022, 30005, 'Lemon Tea', 72.00, 1, '/oss/life-assistant/demo/products/lemon-tea.png', NULL, 72.00, 0),
(71022, 70023, 30003, 'Bubble Milk Tea', 52.00, 1, '/oss/life-assistant/demo/products/bubble-milk-tea.png', NULL, 52.00, 0),
(71023, 70024, 30004, 'Tiramisu', 84.00, 1, '/oss/life-assistant/demo/products/tiramisu.png', NULL, 84.00, 0),
(71024, 70025, 30005, 'Lemon Tea', 108.00, 1, '/oss/life-assistant/demo/products/lemon-tea.png', NULL, 108.00, 0),
(71025, 70026, 30003, 'Bubble Milk Tea', 76.00, 1, '/oss/life-assistant/demo/products/bubble-milk-tea.png', NULL, 76.00, 0),
(71026, 70027, 30004, 'Tiramisu', 63.00, 1, '/oss/life-assistant/demo/products/tiramisu.png', NULL, 63.00, 0),
(71027, 70028, 30005, 'Lemon Tea', 94.00, 1, '/oss/life-assistant/demo/products/lemon-tea.png', NULL, 94.00, 0),
(71028, 70029, 30003, 'Bubble Milk Tea', 122.00, 1, '/oss/life-assistant/demo/products/bubble-milk-tea.png', NULL, 122.00, 0),
(71029, 70030, 30005, 'Lemon Tea', 13.00, 1, '/oss/life-assistant/demo/products/lemon-tea.png', NULL, 13.00, 0);

USE `settlement_db`;

DELETE FROM `user_coupon` WHERE `id` BETWEEN 61001 AND 61020;
DELETE FROM `payment` WHERE `id` BETWEEN 62001 AND 62050;
DELETE FROM `coupon` WHERE `id` BETWEEN 60001 AND 60010;

INSERT INTO `coupon` (`id`, `name`, `discount`, `threshold`, `start_time`, `end_time`, `total_count`, `claimed_count`, `limit_per_user`, `status`) VALUES
(60001, 'New User 10 Off', 10.00, 30.00, '2025-01-01 00:00:00', '2027-12-31 23:59:59', 1000, 2, 1, 'released'),
(60002, 'Spend 50 Save 15', 15.00, 50.00, '2025-01-01 00:00:00', '2027-12-31 23:59:59', 500, 3, 2, 'released'),
(60003, 'Weekend 8 Off', 8.00, 40.00, '2025-01-01 00:00:00', '2027-12-31 23:59:59', 500, 0, 1, 'released'),
(60004, 'Expired 12 Off', 12.00, 60.00, '2024-01-01 00:00:00', '2024-12-31 23:59:59', 100, 100, 1, 'expired');

INSERT INTO `user_coupon` (`id`, `user_id`, `coupon_id`, `status`, `claimed_at`, `used_at`, `order_id`) VALUES
(61001, 10001, 60001, 'unused', '2026-09-03 09:00:00', NULL, NULL),
(61002, 10001, 60002, 'used', '2026-09-03 09:10:00', '2026-09-03 09:40:00', 70002),
(61003, 10002, 60003, 'unused', '2026-09-02 18:00:00', NULL, NULL);

INSERT INTO `payment` (`id`, `order_id`, `amount`, `pay_method`, `transaction_id`, `status`, `pay_time`) VALUES
(62001, 70001, 49.00, 'ALIPAY', 'TXN202609030001', 'SUCCESS', '2026-09-03 10:16:00'),
(62002, 70003, 228.00, 'ALIPAY', 'TXN202609020003', 'SUCCESS', '2026-09-02 12:02:00'),
(62003, 70004, 176.00, 'WECHAT', 'TXN202609010004', 'SUCCESS', '2026-09-01 18:05:00'),
(62004, 70006, 104.00, 'ALIPAY', 'TXN202608310006', 'SUCCESS', '2026-08-31 12:10:00'),
(62005, 70007, 146.00, 'ALIPAY', 'TXN202608300007', 'SUCCESS', '2026-08-30 13:10:00'),
(62006, 70008, 198.00, 'WECHAT', 'TXN202608290008', 'SUCCESS', '2026-08-29 19:10:00'),
(62007, 70009, 174.00, 'ALIPAY', 'TXN202608280009', 'SUCCESS', '2026-08-28 11:45:00'),
(62008, 70010, 126.00, 'ALIPAY', 'TXN202608270010', 'SUCCESS', '2026-08-27 12:30:00'),
(62009, 70011, 150.00, 'WECHAT', 'TXN202608260011', 'SUCCESS', '2026-08-26 13:20:00'),
(62010, 70012, 92.00, 'ALIPAY', 'TXN202608250012', 'SUCCESS', '2026-08-25 11:15:00'),
(62011, 70013, 118.00, 'ALIPAY', 'TXN202608240013', 'SUCCESS', '2026-08-24 12:25:00'),
(62012, 70014, 96.00, 'WECHAT', 'TXN202608230014', 'SUCCESS', '2026-08-23 12:05:00'),
(62013, 70015, 74.00, 'ALIPAY', 'TXN202608220015', 'SUCCESS', '2026-08-22 18:10:00'),
(62014, 70016, 48.00, 'ALIPAY', 'TXN202608210016', 'SUCCESS', '2026-08-21 11:18:00'),
(62015, 70017, 13.00, 'ALIPAY', 'TXN202609030101', 'SUCCESS', '2026-09-03 09:30:00'),
(62016, 70018, 24.00, 'ALIPAY', 'TXN202609020101', 'SUCCESS', '2026-09-02 10:30:00'),
(62017, 70019, 41.00, 'WECHAT', 'TXN202609010101', 'SUCCESS', '2026-09-01 14:30:00'),
(62018, 70020, 55.00, 'ALIPAY', 'TXN202608310101', 'SUCCESS', '2026-08-31 11:30:00'),
(62019, 70021, 36.00, 'ALIPAY', 'TXN202608300101', 'SUCCESS', '2026-08-30 16:25:00'),
(62020, 70022, 72.00, 'WECHAT', 'TXN202608290101', 'SUCCESS', '2026-08-29 12:10:00'),
(62021, 70023, 52.00, 'ALIPAY', 'TXN202608280101', 'SUCCESS', '2026-08-28 17:10:00'),
(62022, 70024, 84.00, 'ALIPAY', 'TXN202608270101', 'SUCCESS', '2026-08-27 18:00:00'),
(62023, 70025, 108.00, 'WECHAT', 'TXN202608260101', 'SUCCESS', '2026-08-26 15:10:00'),
(62024, 70026, 76.00, 'ALIPAY', 'TXN202608250101', 'SUCCESS', '2026-08-25 12:05:00'),
(62025, 70027, 63.00, 'ALIPAY', 'TXN202608240101', 'SUCCESS', '2026-08-24 10:15:00'),
(62026, 70028, 94.00, 'WECHAT', 'TXN202608230101', 'SUCCESS', '2026-08-23 13:25:00'),
(62027, 70029, 122.00, 'ALIPAY', 'TXN202608220101', 'SUCCESS', '2026-08-22 19:00:00'),
(62028, 70030, 13.00, 'ALIPAY', 'TXN202608210101', 'SUCCESS', '2026-08-21 09:55:00');

USE `fulfillment_db`;

DELETE FROM `rider` WHERE `id` BETWEEN 40001 AND 40010;

INSERT INTO `rider` (`id`, `username`, `name`, `password`, `phone`, `id_card`, `status`, `audit_opinion`, `service_area`) VALUES
(40001, 'rider01', 'rider01', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138004', '110101199901010011', 'active', NULL, 'Campus and Science Park'),
(40002, 'rider02', 'rider02', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138005', '110101199901010022', 'pending', '资料已提交，待审核', 'Campus and Science Park');

USE `engagement_db`;

DELETE FROM `message` WHERE `id` BETWEEN 90001 AND 90020;
DELETE FROM `review` WHERE `id` BETWEEN 80001 AND 80020;

INSERT INTO `review` (`id`, `order_id`, `user_id`, `merchant_id`, `product_id`, `rating`, `content`, `images`) VALUES
(80001, 70001, 10001, 20001, 30001, 5, '送达快，米饭口感稳定。', NULL),
(80002, 70004, 10001, 20001, 30001, 4, '整体不错，份量稍大。', NULL),
(80003, 70017, 10001, 20002, 30003, 5, '甜度可选多，出杯快。', NULL);

INSERT INTO `message` (`id`, `sender_id`, `sender_type`, `receiver_id`, `receiver_type`, `order_id`, `content`, `is_read`) VALUES
(90001, 10001, 'consumer', 20001, 'merchant', 70001, '请尽快出餐。', 1),
(90002, 20001, 'merchant', 10001, 'consumer', 70001, '已经在打包了。', 0),
(90003, 40001, 'rider', 10002, 'consumer', 70003, '我已经到楼下了。', 0),
(90004, 10001, 'consumer', 20002, 'merchant', 70019, '这杯少糖少冰。', 1);

SET FOREIGN_KEY_CHECKS = 1;
