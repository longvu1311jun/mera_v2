-- =====================================================
-- SQL Script: Tạo bảng order_assignments
-- =====================================================

-- 1. Tạo bảng order_assignments
CREATE TABLE IF NOT EXISTS order_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    employee_mapping_id BIGINT NOT NULL,
    lark_employee_id VARCHAR(64) NOT NULL,
    lark_employee_name VARCHAR(255),
    customer_name VARCHAR(255),
    customer_phone VARCHAR(32),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_assign_order_id (order_id),
    INDEX idx_order_assign_lark_employee_id (lark_employee_id),
    INDEX idx_order_assign_assigned_at (assigned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- NOTE: Các columns sau đã được thêm vào EmployeeMapping như transient fields
-- Nếu muốn lưu trữ vĩnh viễn trong DB, chạy:
-- ALTER TABLE employee_mappings
-- ADD COLUMN today_assignment_count INT DEFAULT 0,
-- ADD COLUMN last_assignment_date DATE;
-- =====================================================
