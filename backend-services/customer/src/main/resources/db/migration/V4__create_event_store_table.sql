-- =============================================================================
-- V4: Create event_store table
-- Event sourcing storage for customer domain events
-- =============================================================================

CREATE TABLE IF NOT EXISTS event_store (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_event_store_aggregate_id ON event_store(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_event_store_event_type ON event_store(event_type);
CREATE INDEX IF NOT EXISTS idx_event_store_timestamp ON event_store(timestamp);
CREATE INDEX IF NOT EXISTS idx_event_store_correlation_id ON event_store(correlation_id);
CREATE INDEX IF NOT EXISTS idx_event_store_causation_id ON event_store(causation_id);

-- Composite index for efficient event replay by aggregate
CREATE INDEX IF NOT EXISTS idx_event_store_aggregate_timestamp
    ON event_store(aggregate_id, timestamp);

-- Create publication for Debezium CDC
CREATE PUBLICATION customer_events_publication FOR TABLE event_store;

COMMENT ON TABLE event_store IS 'Append-only event store for customer domain events';
COMMENT ON COLUMN event_store.event_id IS 'Unique identifier for this event';
COMMENT ON COLUMN event_store.event_type IS 'Type of event (e.g., CustomerRegistered)';
COMMENT ON COLUMN event_store.event_version IS 'Schema version for event evolution';
COMMENT ON COLUMN event_store.aggregate_id IS 'ID of the aggregate this event belongs to';
COMMENT ON COLUMN event_store.correlation_id IS 'ID for tracing related events across services';
COMMENT ON COLUMN event_store.causation_id IS 'ID of the event that caused this event';
COMMENT ON COLUMN event_store.payload IS 'Event data as JSONB';
