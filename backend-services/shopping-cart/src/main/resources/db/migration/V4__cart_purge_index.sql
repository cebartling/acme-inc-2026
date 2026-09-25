-- PIN-289: EXPIRED and MERGED carts are deleted once they have been final for longer than the
-- retention period. updated_at is set when a cart expires or merges; this index serves the
-- purge job's scan, which ix_carts_idle_guests (ACTIVE only) cannot.
CREATE INDEX ix_carts_final ON carts (updated_at)
    WHERE status IN ('EXPIRED', 'MERGED');
