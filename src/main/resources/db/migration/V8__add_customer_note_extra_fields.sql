-- =====================================================
-- V8: Bổ sung fields cho customer_notes + bảng edit_history
-- =====================================================

-- Thêm fields còn thiếu vào customer_notes (nếu chưa có)
ALTER TABLE customer_notes
    ADD COLUMN images JSON NULL COMMENT 'Danh sách ảnh đính kèm (POS API trả về array)',
    ADD COLUMN links JSON NULL COMMENT 'Danh sách link đính kèm (POS API trả về array)',
    ADD COLUMN created_by_pancake_id VARCHAR(64) NULL COMMENT 'pancake_id của created_by',
    ADD COLUMN created_by_token VARCHAR(255) NULL COMMENT 'token_for_business của created_by';

-- Bảng lưu lịch sử chỉnh sửa note
CREATE TABLE IF NOT EXISTS customer_note_edit_history (
    id VARCHAR(64) PRIMARY KEY,
    note_id VARCHAR(64) NOT NULL COMMENT 'FK -> customer_notes.id',
    created_at BIGINT NOT NULL COMMENT 'Timestamp epoch millis từ POS',
    message TEXT NOT NULL,
    images JSON NULL,
    created_by_id VARCHAR(64) NULL COMMENT 'uid của created_by',
    created_by_name VARCHAR(255) NULL COMMENT 'fb_name của created_by',
    created_by_pancake_id VARCHAR(64) NULL,
    created_by_token VARCHAR(255) NULL,
    INDEX idx_note_edit_history_note_id (note_id),
    INDEX idx_note_edit_history_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;