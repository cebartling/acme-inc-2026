-- =============================================================================
-- V7: Drop the outbox columns the polling relay used
-- Debezium reads the outbox from the write-ahead log (PIN-552), so nothing marks a row
-- published or counts retries any more. The polling index goes with them.
--
-- The connector publishes only rows written after it starts, and published_at is the only
-- record of which rows the relay never sent. So the migration stops if any are left: let the
-- old relay drain them (or publish them by hand), then run it again.
--
-- Stop old instances before this runs: an old instance would fail to insert outbox rows
-- once the columns are gone.
-- =============================================================================

DO $$
DECLARE
    unpublished BIGINT;
BEGIN
    SELECT count(*) INTO unpublished FROM outbox WHERE published_at IS NULL;
    IF unpublished > 0 THEN
        RAISE EXCEPTION 'V7 would lose % outbox row(s) the relay has not published; let the old relay drain them first', unpublished;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_outbox_unpublished_sequence;

ALTER TABLE outbox
    DROP COLUMN IF EXISTS published_at,
    DROP COLUMN IF EXISTS retry_count,
    DROP COLUMN IF EXISTS last_error;
