-- Shopping carts (US-0004-06). A guest cart is keyed by the session ID from the
-- acme_session_id cookie; customer_id stays NULL until cart merge (US-0004-08).
CREATE TABLE carts (
    id          UUID PRIMARY KEY,
    session_id  VARCHAR(64) NOT NULL UNIQUE,
    customer_id UUID,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

-- One line per variant: adding a variant already in the cart increments quantity.
-- product_snapshot freezes name, SKU, image and attributes at the time of add.
CREATE TABLE cart_items (
    id               UUID PRIMARY KEY,
    cart_id          UUID           NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    variant_id       UUID           NOT NULL,
    quantity         INT            NOT NULL CHECK (quantity > 0),
    unit_price       NUMERIC(10, 2) NOT NULL,
    product_snapshot JSONB          NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_cart_items_cart_variant UNIQUE (cart_id, variant_id)
);
