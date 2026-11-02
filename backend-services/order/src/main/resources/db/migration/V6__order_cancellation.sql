-- Who cancelled an order, and why, once a customer can cancel a submitted one (PIN-429, ADR-0063).
-- Everything cancelled so far was a failed placement. The columns stay nullable, and nothing
-- ties them to status, so an instance still running the old code can cancel during a deploy.
ALTER TABLE orders
    ADD COLUMN cancelled_by VARCHAR(20),
    ADD COLUMN cancel_reason VARCHAR(500);

UPDATE orders SET cancelled_by = 'PLACEMENT' WHERE status = 'CANCELLED';

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_cancelled_by CHECK (cancelled_by IN ('CUSTOMER', 'PLACEMENT'));
