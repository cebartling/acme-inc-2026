-- =============================================================================
-- V5: Create processed_events table
-- Idempotency tracking for consumed Kafka events
-- =============================================================================

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for cleanup queries (removing old processed events)
CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON processed_events(processed_at);

COMMENT ON TABLE processed_events IS 'Tracking table for idempotent event processing';
COMMENT ON COLUMN processed_events.event_id IS 'ID of the processed event (from Kafka message)';
COMMENT ON COLUMN processed_events.event_type IS 'Type of the processed event';
COMMENT ON COLUMN processed_events.processed_at IS 'When the event was processed';
