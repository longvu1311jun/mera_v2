-- Index for order_status_histories to speed up LT queries
CREATE INDEX IF NOT EXISTS idx_osh_order_status_date 
ON order_status_histories(order_id, new_status, updated_at);

-- Composite index on orders for creator_id and status
CREATE INDEX IF NOT EXISTS idx_orders_creator_status_lt 
ON orders(creator_id, status, lt_type, lt_count_snapshot);
