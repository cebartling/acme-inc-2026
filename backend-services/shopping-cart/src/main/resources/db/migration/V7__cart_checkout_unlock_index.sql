-- PIN-330: a cart whose checkout session lapsed is unlocked by a job that runs every minute.
-- This index serves its scan, CHECKOUT carts by expiry, which neither ix_carts_idle_guests
-- (ACTIVE only) nor ix_carts_final can.
CREATE INDEX ix_carts_checkout_expiry ON carts (checkout_expires_at)
    WHERE status = 'CHECKOUT';
