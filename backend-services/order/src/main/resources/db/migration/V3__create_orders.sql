-- Orders placed from a checkout session (PIN-366, journey 0005 step 5).
-- An order is saved PENDING, becomes SUBMITTED once the cart is converted, or CANCELLED
-- when the cart refuses for good.
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    -- ACME-YYYYMMDD-NNNNNN (journey AC-5.4)
    order_number VARCHAR(20) NOT NULL,
    user_id UUID NOT NULL,
    checkout_session_id UUID NOT NULL,
    cart_id UUID NOT NULL,
    -- The caller's Idempotency-Key header (AC-5.8)
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    subtotal NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    -- The cart's error code when a CANCELLED order was refused, replayed to a retry
    cancel_code VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT uq_orders_user_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'SUBMITTED', 'CANCELLED'))
);

-- The cart's lines, copied when the order is placed
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    variant_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL,
    line_total NUMERIC(10, 2) NOT NULL,
    product_snapshot JSONB NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

-- One counter per day for order numbers, like the customer service's
-- customer_number_sequences
CREATE TABLE order_number_sequences (
    order_date VARCHAR(8) PRIMARY KEY,  -- YYYYMMDD
    current_value INTEGER NOT NULL DEFAULT 0
);

-- Atomically increments and returns the next sequence value for a day
CREATE FUNCTION next_order_number(p_order_date VARCHAR(8))
RETURNS INTEGER AS $$
DECLARE
    v_next_value INTEGER;
BEGIN
    INSERT INTO order_number_sequences (order_date, current_value)
    VALUES (p_order_date, 1)
    ON CONFLICT (order_date)
    DO UPDATE SET current_value = order_number_sequences.current_value + 1
    RETURNING current_value INTO v_next_value;

    RETURN v_next_value;
END;
$$ LANGUAGE plpgsql;
