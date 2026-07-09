-- V10__create_optimized_customer.sql
-- Bảng lưu trữ khách hàng đã được tối ưu (rời danh sách Số thả nổi vì đã lên đơn)
-- và bảng snapshot thành viên danh sách để job đối soát phát hiện khách rời đi.

CREATE TABLE IF NOT EXISTS problem_customer_tracking (
    customer_id            VARCHAR(64)  NOT NULL PRIMARY KEY,
    name                   VARCHAR(255),
    phone                  VARCHAR(32),
    note_count             INT          NOT NULL DEFAULT 0,
    order_count            INT          NOT NULL DEFAULT 0,
    succeed_order_count    INT          NOT NULL DEFAULT 0,
    reason                 VARCHAR(500),
    customer_created_text  VARCHAR(32),
    first_seen_at          DATETIME,
    last_seen_at           DATETIME,
    INDEX idx_pct_last_seen (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS optimized_customer (
    id                     BIGINT       AUTO_INCREMENT PRIMARY KEY,
    customer_id            VARCHAR(64)  NOT NULL UNIQUE,
    name                   VARCHAR(255),
    phone                  VARCHAR(32),
    note_count             INT          NOT NULL DEFAULT 0,
    reason                 VARCHAR(500),
    customer_created_text  VARCHAR(32),
    order_count_before     INT          NOT NULL DEFAULT 0,
    succeed_before         INT          NOT NULL DEFAULT 0,
    order_count_after      INT          NOT NULL DEFAULT 0,
    succeed_after          INT          NOT NULL DEFAULT 0,
    first_seen_at          DATETIME,
    optimized_at           DATETIME,
    INDEX idx_oc_optimized_at (optimized_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
