-- PIN-330: a CHECKOUT cart always has its checkout session and expiry. The unlock job's scan
-- reads both as non-null, so one row without them would fail every run, and a CHECKOUT row
-- with no expiry would never be found and stay locked for good. Every other status may still
-- leave them null; unlocking and leaving checkout clear both.
ALTER TABLE carts
    ADD CONSTRAINT ck_carts_checkout_session CHECK (
        status <> 'CHECKOUT' OR (checkout_session_id IS NOT NULL AND checkout_expires_at IS NOT NULL)
    );
