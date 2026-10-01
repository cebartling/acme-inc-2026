-- PIN-329: starting checkout locks the cart (Epic 009, Active -> Checkout). A CHECKOUT cart
-- is still its owner's current cart, but refuses every change until checkout ends.

ALTER TABLE carts DROP CONSTRAINT ck_carts_status;
ALTER TABLE carts
    ADD CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE', 'MERGED', 'EXPIRED', 'CHECKOUT'));

-- The checkout session a CHECKOUT cart is locked for, and when it lapses. Null otherwise.
ALTER TABLE carts ADD COLUMN checkout_session_id UUID;
ALTER TABLE carts ADD COLUMN checkout_expires_at TIMESTAMPTZ;

-- One current cart per session and per user: a locked cart still counts, so adding to a
-- cart during checkout cannot slip a second cart in beside it.
DROP INDEX uq_carts_active_session;
DROP INDEX uq_carts_active_user;
CREATE UNIQUE INDEX uq_carts_active_session ON carts (session_id)
    WHERE status IN ('ACTIVE', 'CHECKOUT') AND session_id IS NOT NULL;
CREATE UNIQUE INDEX uq_carts_active_user ON carts (user_id)
    WHERE status IN ('ACTIVE', 'CHECKOUT') AND user_id IS NOT NULL;
