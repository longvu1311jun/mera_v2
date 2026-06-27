-- ================================================
-- Run these SQL statements on your MySQL database
-- =============================================

-- Create pos_profiles table
CREATE TABLE IF NOT EXISTS pos_profiles (
    id VARCHAR(64) PRIMARY KEY,
    shop_id BIGINT,
    name VARCHAR(255),
    created_at DATETIME,
    updated_at DATETIME
);

-- Create pos_shop table (if not exists) for FK validation
CREATE TABLE IF NOT EXISTS pos_shop (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    created_at DATETIME
);

-- Insert default shop
INSERT IGNORE INTO pos_shop (id, name, created_at) VALUES (1546758, 'Default Shop', NOW());
