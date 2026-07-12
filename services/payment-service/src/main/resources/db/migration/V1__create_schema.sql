CREATE TABLE payments (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_id VARCHAR(100),
    failure_reason VARCHAR(500),
    processed_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_payments_order ON payments(order_id);

CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    payload_json JSONB NOT NULL,
    correlation_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(published_at, created_at)
    WHERE published_at IS NULL;
