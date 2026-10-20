-- PIN-331: placing an order converts the cart (journey 0005 step 5). CONVERTED is final, like
-- EXPIRED and MERGED, and no longer the owner's current cart. It keeps its checkout session ID
-- and records the order it became, so a repeated convert for the same order can be answered.

ALTER TABLE carts DROP CONSTRAINT ck_carts_status;
ALTER TABLE carts
    ADD CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE', 'MERGED', 'EXPIRED', 'CHECKOUT', 'CONVERTED'));

-- The order a CONVERTED cart became. Null otherwise.
ALTER TABLE carts ADD COLUMN order_id UUID;
ALTER TABLE carts
    ADD CONSTRAINT ck_carts_converted_order CHECK (
        status <> 'CONVERTED' OR (order_id IS NOT NULL AND checkout_session_id IS NOT NULL)
    );
-- An order is placed from one cart, so no two carts convert into the same order.
CREATE UNIQUE INDEX uq_carts_order ON carts (order_id);

-- The purge job deletes CONVERTED carts after retention too (PIN-289), so its index covers them.
DROP INDEX ix_carts_final;
CREATE INDEX ix_carts_final ON carts (updated_at)
    WHERE status IN ('EXPIRED', 'MERGED', 'CONVERTED');
