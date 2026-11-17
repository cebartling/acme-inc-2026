-- =============================================================================
-- V8: Index the outbox by created_at (PIN-552)
-- The retention job deletes rows WHERE created_at < cutoff. V7 dropped the only secondary
-- index, so without this each run scans the whole table. Customer's outbox has the same index.
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox (created_at);
