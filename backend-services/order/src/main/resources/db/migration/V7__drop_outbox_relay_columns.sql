-- =============================================================================
-- V7: Drop the outbox columns the polling relay used
-- Debezium reads the outbox from the write-ahead log (PIN-552), so nothing marks a row
-- published or counts retries any more. The polling index goes with them.
--
-- Stop old instances before this runs: an old instance would fail to insert outbox rows
-- once the columns are gone.
-- =============================================================================

DROP INDEX IF EXISTS idx_outbox_unpublished_sequence;

ALTER TABLE outbox
    DROP COLUMN IF EXISTS published_at,
    DROP COLUMN IF EXISTS retry_count,
    DROP COLUMN IF EXISTS last_error;
