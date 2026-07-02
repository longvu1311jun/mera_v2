-- V6__create_order_assignment_config.sql
-- Bảng cấu hình phân công đơn hàng

CREATE TABLE IF NOT EXISTS order_assignment_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(1000) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dữ liệu mặc định
INSERT IGNORE INTO order_assignment_config (config_key, config_value, description) VALUES
    ('on_time_threshold', '08:00', 'Giờ điểm danh tối thiểu để được coi là đúng giờ (HH:mm)'),
    ('avg_calculation_include_late', 'false', 'Tính trung bình có bao gồm nhóm đi muộn không (true/false)'),
    ('include_late_in_priority', 'false', 'Cho phép nhóm muộn được nhận ưu tiên nếu ít khách (true/false)'),
    ('streak_enabled', 'false', 'Bật tính năng streak (ngày đi đúng giờ liên tiếp) (true/false)'),
    ('streak_threshold', '2', 'Ngưỡng streak tối thiểu để được siêu ưu tiên (số ngày)'),
    ('auto_assign_enabled', 'true', 'Bật tự động phân công khi có khách hàng mới (true/false)'),
    ('max_assignments_per_employee', '10', 'Số khách tối đa mỗi nhân viên có thể nhận trong ngày');
