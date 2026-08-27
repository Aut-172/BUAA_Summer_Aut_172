DROP TABLE IF EXISTS payment;
DROP TABLE IF EXISTS user_coupon;
DROP TABLE IF EXISTS coupon;

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

INSERT INTO coupon (
    id, name, discount, threshold, start_time, end_time, total_count, claimed_count, limit_per_user, status
) VALUES
(60001, 'New User 10 Off', 10.00, 30.00, TIMESTAMP '2025-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 1000, 0, 1, 'released'),
(60002, 'High Threshold', 20.00, 200.00, TIMESTAMP '2025-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 1000, 0, 1, 'released');

INSERT INTO user_coupon (
    id, user_id, coupon_id, status, claimed_at, order_id
) VALUES
(61001, 10001, 60001, 'unused', TIMESTAMP '2026-08-27 10:00:00', NULL),
(61002, 10001, 60002, 'unused', TIMESTAMP '2026-08-27 10:00:00', NULL),
(61003, 10001, 60001, 'locked', TIMESTAMP '2026-08-27 10:00:00', 70003);
