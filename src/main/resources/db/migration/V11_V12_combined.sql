-- ============================================================
-- MIGRATION COMBINED: V11 + V12
-- Chạy trên MariaDB 10.5+
-- ============================================================

-- ============================================================
-- PHẦN V11: Restructure LT columns
-- 1. customers: DROP lt_lark, lt_real -> ADD lt_count (INT)
-- 2. orders: DROP lt_max, CHANGE lt_type VARCHAR -> BOOLEAN
-- 3. DROP combo1_2 table
-- ============================================================

-- customers: xóa lt_lark, lt_real, thêm lt_count
ALTER TABLE customers
    DROP COLUMN IF EXISTS lt_lark,
    DROP COLUMN IF EXISTS lt_real,
    ADD COLUMN IF NOT EXISTS lt_count INT DEFAULT 0;

-- orders: xóa lt_max, đổi lt_type VARCHAR -> BOOLEAN
ALTER TABLE orders
    DROP COLUMN IF EXISTS lt_max,
    MODIFY COLUMN lt_type TINYINT(1) DEFAULT 0 AFTER updated_at;

-- Xóa bảng combo1_2 (không còn dùng)
DROP TABLE IF EXISTS combo1_2;


-- ============================================================
-- PHẦN V12: Thêm lt_count_snapshot
-- 1. Thêm cột lt_count_snapshot vào orders
-- 2. Thêm index cho báo cáo LT theo tháng
-- ============================================================

-- Thêm cột lt_count_snapshot vào bảng orders
-- Lưu giá trị customers.lt_count tại thời điểm đơn được tạo
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS lt_count_snapshot INT DEFAULT NULL
    COMMENT 'lt_count của customer tại thời điểm đơn được tạo. Dùng cho báo cáo LT theo tháng.';

-- Index cho query báo cáo: filter theo creator + snapshot + inserted_at + status
ALTER TABLE orders
    ADD INDEX IF NOT EXISTS idx_orders_lt_snap_creator (creator_id, lt_count_snapshot, inserted_at, status);
