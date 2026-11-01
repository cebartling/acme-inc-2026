-- CancelAbandonedOrdersUseCase (PIN-427) looks for old PENDING orders on every run. Few orders
-- are PENDING at any time, and idx_orders_user_status_created can't help without a user_id.
CREATE INDEX idx_orders_pending_created ON orders (created_at) WHERE status = 'PENDING';
