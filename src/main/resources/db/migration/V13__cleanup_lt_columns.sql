-- ============================================================
-- MIGRATION V13: Cleanup unused LT columns
-- Xóa các cột không còn dùng trong logic LT mới
-- ============================================================

-- customers: xóa lt_tay, lt_adjustment (không còn cần thiết)
ALTER TABLE customers
    DROP COLUMN IF EXISTS lt_tay,
    DROP COLUMN IF EXISTS lt_adjustment;

-- orders: xóa lt_max (không còn dùng)
ALTER TABLE orders
    DROP COLUMN IF EXISTS lt_max;
