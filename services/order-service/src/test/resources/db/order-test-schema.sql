DROP TABLE IF EXISTS group_coupon;
DROP TABLE IF EXISTS order_compensation;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS orders;

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
    stock_reserved BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_compensation (
    id BIGINT PRIMARY KEY,
    request_id VARCHAR(100),
    order_id BIGINT,
    action VARCHAR(50) NOT NULL,
    target_service VARCHAR(50) NOT NULL,
    payload CLOB,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(500),
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

CREATE TABLE group_coupon (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT,
    code VARCHAR(20),
    status VARCHAR(20),
    expire_at TIMESTAMP,
    used_at TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO orders (
    id, order_no, user_id, merchant_id, type, total_amount, actual_amount,
    delivery_fee, discount, status, address_detail
) VALUES
(70001, 'ORD202608270000000001', 10001, 20001, 'delivery', 27.00, 27.00, 5.00, 0.00, 'pending_payment', 'No. 1 Dorm'),
(70002, 'ORD202608270000000002', 10001, 20001, 'delivery', 27.00, 27.00, 5.00, 0.00, 'pending_payment', 'No. 2 Dorm');

INSERT INTO order_item (
    id, order_id, product_id, name, price, quantity, image, spec_label, subtotal, reviewed
) VALUES
(71001, 70001, 30001, 'Braised Pork Rice', 22.00, 1, 'https://picsum.photos/seed/p1/400/300', NULL, 22.00, FALSE),
(71002, 70002, 30001, 'Braised Pork Rice', 22.00, 1, 'https://picsum.photos/seed/p1/400/300', NULL, 22.00, FALSE);
