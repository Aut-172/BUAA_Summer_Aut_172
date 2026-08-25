DROP TABLE IF EXISTS user_coupon;
DROP TABLE IF EXISTS coupon;
DROP TABLE IF EXISTS payment;
DROP TABLE IF EXISTS cart;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS product_spec;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS merchant;
DROP TABLE IF EXISTS rider;
DROP TABLE IF EXISTS admin;

CREATE TABLE admin (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    nickname VARCHAR(50),
    avatar VARCHAR(500),
    role VARCHAR(20),
    status VARCHAR(20),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rider (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50),
    password VARCHAR(255),
    phone VARCHAR(20),
    id_card VARCHAR(20),
    status VARCHAR(20),
    audit_opinion VARCHAR(255),
    service_area VARCHAR(100),
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

CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    rider_id BIGINT,
    type VARCHAR(20) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    actual_amount DECIMAL(10, 2) NOT NULL,
    delivery_fee DECIMAL(10, 2),
    discount DECIMAL(10, 2),
    status VARCHAR(20) NOT NULL,
    address_id BIGINT,
    address_detail VARCHAR(500),
    buyer_remark VARCHAR(200),
    coupon_id BIGINT,
    paid_at TIMESTAMP,
    completed_at TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payment (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    pay_method VARCHAR(20),
    transaction_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    pay_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cart (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    image VARCHAR(500),
    quantity INT NOT NULL,
    spec_label VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_item (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL,
    image VARCHAR(500),
    spec_label VARCHAR(50),
    subtotal DECIMAL(10, 2) NOT NULL,
    reviewed BOOLEAN,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coupon (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    discount DECIMAL(10, 2) NOT NULL,
    threshold DECIMAL(10, 2) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    total_count INT NOT NULL,
    claimed_count INT,
    limit_per_user INT,
    status VARCHAR(20) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_coupon (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    claimed_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    order_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO admin (id, username, password) VALUES
(1, 'admin', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm');

INSERT INTO user (id, username, password, phone, nickname, role, status) VALUES
(10001, 'demo', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138001', 'Demo User', 'consumer', 'active');

INSERT INTO merchant (
    id, username, password, name, phone, address, status, rating, monthly_sales,
    min_delivery_fee, delivery_fee, delivery_radius
) VALUES
(20001, 'merchant1', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', 'Campus Kitchen', '13800138002', 'No. 18 College Road', 'active', 4.5, 1280, 20.00, 5.00, 5);

INSERT INTO rider (id, name, password, phone, status, service_area) VALUES
(40001, 'rider01', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138004', 'active', 'Campus');

INSERT INTO category (id, name, parent_id, sort_order) VALUES
(1, 'Food', NULL, 1);

INSERT INTO product (
    id, merchant_id, category_id, name, image, price, description, monthly_sales, stock, type, status, gallery
) VALUES
(30001, 20001, 1, 'Braised Pork Rice', 'https://picsum.photos/seed/p1/400/300', 22.00, 'Classic lunch rice bowl.', 320, 100, 'delivery', 'active', '[]');

INSERT INTO product_spec (
    id, product_id, label, price, stock
) VALUES
(1, 30001, 'Large', 3.00, 100);

INSERT INTO coupon (
    id, name, discount, threshold, start_time, end_time, total_count, claimed_count, limit_per_user, status
) VALUES
(60001, 'New User 10 Off', 10.00, 30.00, TIMESTAMP '2025-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 1000, 0, 1, 'released');
