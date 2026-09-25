-- PIN-287: idle guest carts expire (Epic 009, Active -> Expired). Rows are kept for
-- analytics; an EXPIRED cart, like a MERGED one, no longer resolves for its session.

ALTER TABLE carts DROP CONSTRAINT ck_carts_status;
ALTER TABLE carts
    ADD CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE', 'MERGED', 'EXPIRED'));

-- When the owner last used the cart. Writes set it; a guest viewing the cart refreshes it
-- at most once a day. Existing carts start from their last change.
ALTER TABLE carts ADD COLUMN last_active_at TIMESTAMPTZ;
UPDATE carts SET last_active_at = updated_at;
ALTER TABLE carts ALTER COLUMN last_active_at SET NOT NULL;

-- The expiry job's scan: ACTIVE guest carts, oldest activity first.
CREATE INDEX ix_carts_idle_guests ON carts (last_active_at)
    WHERE status = 'ACTIVE' AND session_id IS NOT NULL;
