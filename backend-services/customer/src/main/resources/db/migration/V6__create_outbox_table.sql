-- =============================================================================
-- V6: Create outbox table
-- Transactional Outbox pattern for reliable event publishing
-- =============================================================================

CREATE TABLE IF NOT EXISTS outbox (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    
    -- Ensure event_id uniqueness to prevent duplicate publishing
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

-- Index for polling unpublished messages
CREATE INDEX IF NOT EXISTS idx_outbox_published_at ON outbox(published_at)
    WHERE published_at IS NULL;

-- Composite index for efficient polling with ORDER BY
CREATE INDEX IF NOT EXISTS idx_outbox_created_published ON outbox(created_at, published_at)
    WHERE published_at IS NULL;

-- Index for monitoring and debugging
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox(created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate_id ON outbox(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type ON outbox(event_type);

COMMENT ON TABLE outbox IS 'Transactional outbox for reliable event publishing to Kafka';
COMMENT ON COLUMN outbox.id IS 'Unique identifier for this outbox entry';
COMMENT ON COLUMN outbox.aggregate_id IS 'ID of the aggregate that produced this event';
COMMENT ON COLUMN outbox.event_type IS 'Type of event (e.g., CustomerActivated)';
COMMENT ON COLUMN outbox.event_id IS 'The unique ID of the domain event';
COMMENT ON COLUMN outbox.topic IS 'Kafka topic to publish to';
COMMENT ON COLUMN outbox.message_key IS 'Kafka message key for partitioning';
COMMENT ON COLUMN outbox.payload IS 'Event data as JSON';
COMMENT ON COLUMN outbox.published_at IS 'Timestamp when successfully published (NULL if not yet published)';
COMMENT ON COLUMN outbox.retry_count IS 'Number of publishing attempts';
COMMENT ON COLUMN outbox.last_error IS 'Last error message if publishing failed';
