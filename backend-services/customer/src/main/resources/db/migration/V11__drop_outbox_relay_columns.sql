-- =============================================================================
-- V11: Drop the outbox columns the polling relay used
-- Debezium reads the outbox from the write-ahead log (PIN-547), so nothing marks a row
-- published or counts retries any more. The two partial indexes on published_at go with it.
--
-- Rows are not purged here: the outbox keeps every row until a purge job exists (PIN-395).
-- =============================================================================

DROP INDEX IF EXISTS idx_outbox_published_at;
DROP INDEX IF EXISTS idx_outbox_created_published;

ALTER TABLE outbox
    DROP COLUMN IF EXISTS published_at,
    DROP COLUMN IF EXISTS retry_count,
    DROP COLUMN IF EXISTS last_error;
