-- Transactional outbox for order events (PIN-368, ADR-0022), like the customer service's V6.
-- An event is written here in the same transaction as the order change, and OutboxRelay
-- sends it to Kafka afterwards. The orders table that PIN-366 adds is V3.
CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    -- NULL until the relay has sent it
    published_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,

    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

-- The relay's poll: unsent messages, oldest first
CREATE INDEX idx_outbox_unpublished_created_at ON outbox (created_at)
    WHERE published_at IS NULL;
