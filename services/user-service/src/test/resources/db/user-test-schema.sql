DROP TABLE IF EXISTS user_favorite_merchant;
DROP TABLE IF EXISTS cart;
DROP TABLE IF EXISTS address;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS admin;

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

CREATE TABLE admin (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE address (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(50),
    phone VARCHAR(20),
    detail VARCHAR(500),
    longitude DECIMAL(10, 7),
    latitude DECIMAL(10, 7),
    is_default BOOLEAN,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cart (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(100),
    price DECIMAL(10, 2),
    image VARCHAR(500),
    quantity INT,
    spec_label VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_favorite_merchant (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO user (id, username, password, phone, nickname, avatar, role, status) VALUES
(10001, 'demo', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138001', 'Demo User', '/avatar.png', 'consumer', 'active'),
(10002, 'other', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138099', 'Other User', NULL, 'consumer', 'active');

INSERT INTO admin (id, username, password) VALUES
(1, 'admin', '$2a$10$HvAnImnOgs9dErjESgWLwuccdHPAWMxqhYxKxfSG1KNyuL6y9.jhe');

INSERT INTO address (id, user_id, name, phone, detail, is_default) VALUES
(50001, 10001, 'Demo User', '13800138001', 'Old default address', TRUE),
(50002, 10001, 'Demo User', '13800138001', 'Backup address', FALSE);

INSERT INTO cart (id, user_id, merchant_id, product_id, name, price, image, quantity, spec_label) VALUES
(51001, 10001, 20001, 30001, 'Braised Pork Rice', 22.00, '/p1.jpg', 1, NULL);

INSERT INTO user_favorite_merchant (id, user_id, merchant_id) VALUES
(52001, 10001, 20001);
