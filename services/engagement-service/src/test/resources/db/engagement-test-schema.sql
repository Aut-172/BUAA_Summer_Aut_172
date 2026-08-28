DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS review;

CREATE TABLE review (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(500),
    images VARCHAR(2000),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE message (
    id BIGINT PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    receiver_id BIGINT NOT NULL,
    receiver_type VARCHAR(20) NOT NULL,
    order_id BIGINT,
    content VARCHAR(500) NOT NULL,
    is_read BOOLEAN,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO review (id, order_id, user_id, merchant_id, product_id, rating, content, images) VALUES
(80002, 70002, 10001, 20001, 30001, 4, 'Already reviewed', '[]');

INSERT INTO message (id, sender_id, sender_type, receiver_id, receiver_type, order_id, content, is_read) VALUES
(81001, 20001, 'merchant', 10001, 'user', 70001, 'Ready soon', FALSE);
