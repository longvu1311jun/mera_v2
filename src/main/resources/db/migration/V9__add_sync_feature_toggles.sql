-- V9__add_sync_feature_toggles.sql
-- Default toggles cho scheduler điểm danh và phân chia khách hàng

INSERT IGNORE INTO order_assignment_config (config_key, config_value, description) VALUES
    ('attendance_sync_enabled', 'true', 'Bật/tắt scheduler đồng bộ điểm danh Lark (true/false)'),
    ('order_assignment_enabled', 'true', 'Bật/tắt chức năng phân chia khách hàng (true/false)');