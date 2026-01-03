-- V3: Create event store table for event sourcing

CREATE TABLE IF NOT EXISTS event_store (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    correlation_id UUID NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for aggregate queries (rebuilding state)
CREATE INDEX IF NOT EXISTS idx_event_store_aggregate_id ON event_store(aggregate_id);

-- Index for event type queries
CREATE INDEX IF NOT EXISTS idx_event_store_event_type ON event_store(event_type);

-- Index for temporal queries
CREATE INDEX IF NOT EXISTS idx_event_store_timestamp ON event_store(timestamp);

-- Index for correlation tracing
CREATE INDEX IF NOT EXISTS idx_event_store_correlation_id ON event_store(correlation_id);

-- Composite index for aggregate + timestamp (efficient replay)
CREATE INDEX IF NOT EXISTS idx_event_store_aggregate_timestamp ON event_store(aggregate_id, timestamp);

-- Create publication for Debezium CDC
CREATE PUBLICATION identity_events_publication FOR TABLE event_store;
