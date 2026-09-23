-- US-0004-08: a cart belongs to a guest session or to a signed-in user (the JWT subject),
-- and a guest cart is marked MERGED once its lines move into the user's cart.

-- Nothing has ever written customer_id, so renaming it to the user ID it will hold is safe.
ALTER TABLE carts RENAME COLUMN customer_id TO user_id;

ALTER TABLE carts
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE', 'MERGED'));

-- A user's cart has no session.
ALTER TABLE carts ALTER COLUMN session_id DROP NOT NULL;
ALTER TABLE carts
    ADD CONSTRAINT ck_carts_one_owner CHECK ((session_id IS NULL) <> (user_id IS NULL));

-- One ACTIVE cart per session and per user. A session whose cart was MERGED can start a
-- fresh guest cart after sign-out, so uniqueness applies to ACTIVE carts only.
ALTER TABLE carts DROP CONSTRAINT carts_session_id_key;
CREATE UNIQUE INDEX uq_carts_active_session ON carts (session_id) WHERE status = 'ACTIVE' AND session_id IS NOT NULL;
CREATE UNIQUE INDEX uq_carts_active_user ON carts (user_id) WHERE status = 'ACTIVE' AND user_id IS NOT NULL;
