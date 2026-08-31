DROP TABLE IF EXISTS spec_group;
DROP TABLE IF EXISTS product_spec;
DROP TABLE IF EXISTS merchant_stock_change;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS merchant;

CREATE TABLE merchant (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50),
    password VARCHAR(255),
    name VARCHAR(100),
    phone VARCHAR(20),
    address VARCHAR(255),
    longitude DECIMAL(10, 7),
    latitude DECIMAL(10, 7),
    business_hours VARCHAR(100),
    category VARCHAR(50),
    description VARCHAR(500),
    avatar VARCHAR(500),
    tags VARCHAR(255),
    status VARCHAR(20),
    rating DECIMAL(2, 1),
    monthly_sales INT,
    min_delivery_fee DECIMAL(10, 2),
    delivery_fee DECIMAL(10, 2),
    delivery_radius INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE category (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    parent_id BIGINT,
    sort_order INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product (
    id BIGINT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    category_id BIGINT,
    name VARCHAR(100) NOT NULL,
    image VARCHAR(500),
    price DECIMAL(10, 2) NOT NULL,
    description VARCHAR(500),
    monthly_sales INT,
    stock INT,
    type VARCHAR(20),
    status VARCHAR(20),
    gallery CLOB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_spec (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    label VARCHAR(50) NOT NULL,
    price DECIMAL(10, 2),
    stock INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE merchant_stock_change (
    id BIGINT PRIMARY KEY,
    request_id VARCHAR(100) NOT NULL,
    merchant_id BIGINT NOT NULL,
    order_id BIGINT,
    action VARCHAR(20) NOT NULL,
    payload CLOB,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (request_id)
);

CREATE TABLE spec_group (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    "values" CLOB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO merchant (
    id, username, password, name, phone, address, longitude, latitude, business_hours,
    category, description, avatar, tags, status, rating, monthly_sales,
    min_delivery_fee, delivery_fee, delivery_radius
) VALUES
(20001, 'merchant1', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', 'Campus Kitchen', '13800138002', 'No. 18 College Road', 116.3100000, 39.9800000,
 '09:00-22:00', 'Food', 'Classic lunch meals.', '/oss/life-assistant/demo/merchants/campus-kitchen.png',
 'rice,lunch,hot', 'active', 4.5, 1280, 20.00, 5.00, 5),
(20002, 'merchant2', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', 'Frozen Shop', '13800138003', 'No. 19 College Road', 116.3200000, 39.9900000,
 '09:00-18:00', 'Food', 'Inactive merchant.', '/oss/life-assistant/demo/merchants/tea-corner.png',
 'noodle', 'inactive', 4.9, 3000, 20.00, 5.00, 5),
(20003, 'frozen-merchant', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', 'Frozen Merchant', '13800138009', 'No. 20 College Road', 116.3300000, 39.9700000,
 '09:00-18:00', 'Food', 'Frozen merchant.', '/oss/life-assistant/demo/merchants/campus-kitchen.png',
 'frozen', 'frozen', 4.0, 10, 20.00, 5.00, 5);

INSERT INTO category (id, name, parent_id, sort_order) VALUES
(1, 'Food', NULL, 1),
(2, 'Drinks', NULL, 2);

INSERT INTO product (
    id, merchant_id, category_id, name, image, price, description, monthly_sales, stock, type, status, gallery
) VALUES
(30001, 20001, 1, 'Braised Pork Rice', '/oss/life-assistant/demo/products/braised-pork-rice.png', 22.00, 'Classic lunch rice bowl.', 320, 100, 'delivery', 'active', '[]'),
(30002, 20001, 1, 'Tomato Noodles', '/oss/life-assistant/demo/products/kung-pao-chicken-rice.png', 18.00, 'Warm noodle bowl.', 80, 0, 'delivery', 'active', '[]'),
(30003, 20002, 1, 'Hidden Rice', '/oss/life-assistant/demo/products/bubble-milk-tea.png', 19.00, 'Should not be visible.', 10, 50, 'delivery', 'active', '[]');

INSERT INTO product_spec (id, product_id, label, price, stock) VALUES
(1, 30001, 'Large', 3.00, 50),
(2, 30001, 'Small', 0.00, 100);

INSERT INTO spec_group (id, product_id, name, "values") VALUES
(10, 30001, 'Size', '["Large(+3元)","Small"]');
