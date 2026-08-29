DROP TABLE IF EXISTS rider;

CREATE TABLE rider (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    id_card VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    audit_opinion VARCHAR(200),
    service_area VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO rider (id, username, name, password, phone, id_card, status, service_area) VALUES
(40001, 'rider01', 'rider01', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138004', '110101199001010011', 'active', 'Campus'),
(40002, 'frozen-rider', 'frozen-rider', '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm', '13800138005', '110101199001010012', 'frozen', 'Campus');
