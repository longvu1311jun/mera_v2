-- ============================================================
-- MIGRATION V14: Fix lt_type column type
-- Đổi orders.lt_type từ VARCHAR(255) sang TINYINT(1) BOOLEAN
-- ============================================================

-- Bước 1: Clear tất cả data về 0
UPDATE orders SET lt_type = 0 WHERE lt_type IS NOT NULL;

-- Bước 2: Đổi kiểu cột
ALTER TABLE orders
    MODIFY COLUMN lt_type TINYINT(1) DEFAULT 0
    COMMENT '0 = LT lẻ (không đủ combo), 1 = LT chẵn (đủ combo, được +1 lt_count)';
