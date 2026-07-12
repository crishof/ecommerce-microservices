CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_notifications_order ON notifications(order_id);
