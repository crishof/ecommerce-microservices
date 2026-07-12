CREATE TABLE stock_reservations (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP
);
CREATE INDEX idx_reservations_order ON stock_reservations(order_id);

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
