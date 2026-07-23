-- ============================================================
-- V11__restructure_lt_columns.sql
-- 1. customers: DROP lt_lark, lt_real -> ADD lt_count (INT)
-- 2. orders: DROP lt_max, CHANGE lt_type VARCHAR -> BOOLEAN
-- 3. DROP combo1_2 table
-- ============================================================

-- 1. customers: xóa lt_lark, lt_real, thêm lt_count
ALTER TABLE customers
    DROP COLUMN IF EXISTS lt_lark,
    DROP COLUMN IF EXISTS lt_real,
    ADD COLUMN IF NOT EXISTS lt_count INT DEFAULT 0;

-- 2. orders: xóa lt_max, đổi lt_type VARCHAR -> BOOLEAN
ALTER TABLE orders
    DROP COLUMN IF EXISTS lt_max,
    MODIFY COLUMN lt_type TINYINT(1) DEFAULT 0 AFTER updated_at;

-- 3. Xóa bảng combo1_2 (không còn dùng)
DROP TABLE IF EXISTS combo1_2;
