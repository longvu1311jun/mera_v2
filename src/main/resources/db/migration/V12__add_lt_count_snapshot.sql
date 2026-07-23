-- V12: Thêm cột lt_count_snapshot vào bảng orders
-- Lưu giá trị customers.lt_count tại thời điểm đơn được tạo/xử lý
-- Dùng cho báo cáo LT theo từng tháng (thay vì dùng c.lt_count running total)

ALTER TABLE orders
  ADD COLUMN lt_count_snapshot INT DEFAULT NULL
  COMMENT 'lt_count của customer tại thời điểm đơn này được tạo. Dùng cho báo cáo LT theo tháng.';

-- Index cho query báo cáo: filter theo creator + snapshot + inserted_at + status
ALTER TABLE orders
  ADD INDEX idx_orders_lt_snap_creator (creator_id, lt_count_snapshot, inserted_at, status);
