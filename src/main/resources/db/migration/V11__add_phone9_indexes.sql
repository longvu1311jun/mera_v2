-- ============================================================
-- V11: Cột "9 số cuối SĐT" (phone9) đã đánh index để trang "Số thả nổi"
-- khớp đơn theo SĐT bằng index thay vì RIGHT(REGEXP_REPLACE(...)) full-scan
-- (nguyên nhân query treo 90s / max_statement_time exceeded).
--
-- LƯU Ý: project KHÔNG chạy Flyway (xem PhoneIndexInitializer chạy runtime).
-- File này chỉ để ghi lại thay đổi schema cho nhất quán; cột được tạo tự động
-- lúc khởi động ứng dụng. Cột VIRTUAL nên ADD COLUMN tức thì (không rebuild bảng),
-- giá trị được vật chất hoá trong index.
-- ============================================================

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS phone9 CHAR(9)
        GENERATED ALWAYS AS (
            CASE WHEN customer_phone IS NOT NULL
                  AND LENGTH(REGEXP_REPLACE(customer_phone, '[^0-9]', '')) >= 9
                 THEN RIGHT(REGEXP_REPLACE(customer_phone, '[^0-9]', ''), 9)
            END
        ) VIRTUAL;

ALTER TABLE orders
    ADD INDEX IF NOT EXISTS idx_orders_phone9 (phone9);

ALTER TABLE customer_phone_numbers
    ADD COLUMN IF NOT EXISTS phone9 CHAR(9)
        GENERATED ALWAYS AS (
            CASE WHEN LENGTH(phone_number) >= 9 THEN RIGHT(phone_number, 9) END
        ) VIRTUAL;

ALTER TABLE customer_phone_numbers
    ADD INDEX IF NOT EXISTS idx_cpn_phone9 (phone9);
