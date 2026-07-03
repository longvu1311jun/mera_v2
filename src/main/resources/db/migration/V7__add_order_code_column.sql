-- Add order_code column to order_assignments table
ALTER TABLE order_assignments ADD COLUMN IF NOT EXISTS order_code VARCHAR(64);

-- Create index for faster lookup
CREATE INDEX IF NOT EXISTS idx_order_assign_order_code ON order_assignments(order_code);
