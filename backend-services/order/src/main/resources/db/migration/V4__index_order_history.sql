-- A customer's order history (PIN-367): OrderRepository.findSummaries filters by user and
-- status and sorts newest first, and its count query filters the same way. This index
-- serves both, in the query's own sort order.
CREATE INDEX idx_orders_user_status_created ON orders (user_id, status, created_at DESC, id DESC);
