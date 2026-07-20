-- ============================================================
-- V12: Bảng precompute "Số thả nổi" — problem_customer_facts.
-- Facts theo từng khách (đếm đơn, số ghi chú, mốc gần nhất, có đơn active/đã nhận...)
-- tính sẵn định kỳ bởi ProblemCustomerFactsService để trang chỉ còn đọc 1 SELECT phẳng,
-- thay cho query nhiều JOIN nặng (từng treo 90s / max_statement_time).
--
-- LƯU Ý: project KHÔNG chạy Flyway. Bảng được tạo runtime tại
-- ProblemCustomerFactsService.ensureSchema() (@ApplicationReadyEvent). File này chỉ để ghi lại.
-- ============================================================

CREATE TABLE IF NOT EXISTS problem_customer_facts (
    customer_id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    name                 VARCHAR(255),
    phone                VARCHAR(32),
    inserted_at          DATETIME,
    order_count          INT      NOT NULL DEFAULT 0,
    succeed_order_count  INT      NOT NULL DEFAULT 0,
    active_order_count   INT      NOT NULL DEFAULT 0,
    note_count           INT      NOT NULL DEFAULT 0,
    last_note_at         DATETIME,
    has_received         TINYINT  NOT NULL DEFAULT 0,
    last_received_at     DATETIME,
    last_order_at        DATETIME,
    updated_at           DATETIME,
    INDEX idx_pcf_note (note_count),
    INDEX idx_pcf_inserted (inserted_at),
    INDEX idx_pcf_received (has_received, last_received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
