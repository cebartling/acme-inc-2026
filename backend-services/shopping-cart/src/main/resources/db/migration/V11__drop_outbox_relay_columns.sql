-- =============================================================================
-- V11: Drop the outbox columns the polling relay used
-- Debezium reads the outbox from the write-ahead log (PIN-551), so nothing marks a row
-- published or counts retries any more. The partial index on published_at goes with it.
--
-- Rows are not purged here: the outbox keeps every row until a purge job exists (PIN-395).
-- =============================================================================

DROP INDEX IF EXISTS idx_outbox_unpublished_sequence;

ALTER TABLE outbox
    DROP COLUMN IF EXISTS published_at,
    DROP COLUMN IF EXISTS retry_count,
    DROP COLUMN IF EXISTS last_error;
