-- =============================================================================
-- V13: Transactional outbox for identity's events (PIN-548, ADR-0022)
-- An event is written here in the same transaction as the change it describes, and
-- Debezium's outbox event router publishes it to the topic in the row from the
-- write-ahead log. Like order's outbox, minus the columns only a polling relay needs.
-- =============================================================================

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

    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

-- For the retention job that will purge old rows (ADR-0022)
CREATE INDEX idx_outbox_created_at ON outbox (created_at);
