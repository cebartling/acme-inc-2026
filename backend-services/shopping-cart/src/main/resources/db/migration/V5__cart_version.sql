-- PIN-278: optimistic locking. Only the aggregate root is versioned: every change to a cart
-- or its lines goes through Cart and updates the carts row (updated_at, last_active_at), so
-- the version moves on every change. A change saved from a stale copy of the cart is then
-- rejected instead of silently overwriting, or reviving, what another request committed.
ALTER TABLE carts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
