CREATE TABLE orders (
    id VARCHAR(64) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    lines_json JSONB NOT NULL,
    total_items INTEGER NOT NULL,
    distinct_products INTEGER NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_orders_customer ON orders(customer_id, created_at DESC);

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
