-- ============================================================
-- V13: Nâng cấp pipeline "Số thả nổi" sang incremental + phân trang server-side.
--  - problem_customer_facts: thêm cột nhóm D (refused, refused_uploaded_at) + index.
--  - problem_customer_facts_state: watermark cho cập nhật incremental.
--  - customer_notes.updated_at: index để quét ghi chú vừa đổi.
--
-- LƯU Ý: project KHÔNG chạy Flyway. Bảng/cột/index được tạo runtime tại
-- ProblemCustomerFactsService.ensureSchema() và PhoneIndexInitializer. File này chỉ để ghi lại.
-- ============================================================

ALTER TABLE problem_customer_facts
    ADD COLUMN IF NOT EXISTS refused TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS refused_uploaded_at DATETIME,
    ADD INDEX IF NOT EXISTS idx_pcf_refused (refused);

CREATE TABLE IF NOT EXISTS problem_customer_facts_state (
    id                   TINYINT  NOT NULL PRIMARY KEY,
    last_incremental_at  DATETIME,
    last_full_at         DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO problem_customer_facts_state (id) VALUES (1);

ALTER TABLE customer_notes
    ADD INDEX IF NOT EXISTS idx_cn_updated (updated_at);
