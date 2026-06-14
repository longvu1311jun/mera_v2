-- ============================================================
-- DATABASE SCHEMA - Pancake Webhook + API Order Data
-- MariaDB 10.5+
-- Encoding: utf8mb4_unicode_ci
-- Updated: 2026-05-09
-- ============================================================

CREATE DATABASE IF NOT EXISTS pancake_orders
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE pancake_orders;

-- ============================================================
-- 1. USERS
-- Nhân viên: seller, care, marketer, creator, editor...
-- ============================================================
CREATE TABLE users (
                       id           CHAR(36)     NOT NULL,
                       name         VARCHAR(255) NOT NULL,
                       email        VARCHAR(255)     NULL,
                       fb_id        VARCHAR(100)     NULL,
                       avatar_url   TEXT             NULL,
                       phone_number VARCHAR(20)      NULL,
                       created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                       PRIMARY KEY (id),
                       UNIQUE KEY uq_email (email),
                       KEY idx_fb_id (fb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Nhân viên hệ thống: seller, care, marketer, creator...';


-- ============================================================
-- 2. PAGES
-- Fanpage Facebook
-- ============================================================
CREATE TABLE pages (
                       id         VARCHAR(50)  NOT NULL  COMMENT 'Facebook page ID',
                       name       VARCHAR(255) NOT NULL,
                       username   VARCHAR(100)     NULL,
                       created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                       PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Fanpage Facebook';


-- ============================================================
-- 3. WAREHOUSES
-- Kho hàng / phòng khám
-- ============================================================
CREATE TABLE warehouses (
                            id           CHAR(36)     NOT NULL,
                            name         VARCHAR(255) NOT NULL,
                            phone_number VARCHAR(20)  NOT NULL,
                            address      VARCHAR(500)     NULL  COMMENT 'Địa chỉ rút gọn',
                            full_address VARCHAR(500) NOT NULL  COMMENT 'Địa chỉ đầy đủ',
                            province_id  VARCHAR(20)      NULL,
                            district_id  VARCHAR(20)      NULL,
                            commune_id   VARCHAR(20)      NULL,
                            postcode     VARCHAR(20)      NULL,
                            created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                            PRIMARY KEY (id),
                            KEY idx_province (province_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Kho hàng / phòng khám';


-- ============================================================
-- 4. CUSTOMERS
-- Khách hàng
-- ============================================================
CREATE TABLE customers (
                           id                   CHAR(36)     NOT NULL  COMMENT 'customers.id trong Pancake',
                           pancake_customer_id  CHAR(36)         NULL  COMMENT 'customers.customer_id (CRM ID)',
                           fb_id                VARCHAR(100)     NULL,
                           shop_id              BIGINT       NOT NULL,
                           name                 VARCHAR(255) NOT NULL,
                           username             VARCHAR(100)     NULL,
                           gender               VARCHAR(10)      NULL  COMMENT 'male | female | NULL',
                           date_of_birth        DATE             NULL,
                           referral_code        VARCHAR(50)      NULL,
                           assigned_user_id     CHAR(36)         NULL,
                           reward_point         INT          NOT NULL DEFAULT 0,
                           current_debts        BIGINT       NOT NULL DEFAULT 0,
                           order_count          INT          NOT NULL DEFAULT 0,
                           succeed_order_count  INT          NOT NULL DEFAULT 0,
                           returned_order_count INT          NOT NULL DEFAULT 0,
                           is_block             TINYINT(1)   NOT NULL DEFAULT 0,
                           inserted_at          DATETIME         NULL,
                           updated_at           DATETIME         NULL,
                           synced_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Lần cuối đồng bộ từ webhook/API',

                           PRIMARY KEY (id),
                           KEY idx_pancake_cid  (pancake_customer_id),
                           KEY idx_fb_id        (fb_id),
                           KEY idx_shop         (shop_id),
                           KEY idx_assigned     (assigned_user_id),
                           CONSTRAINT fk_customer_user FOREIGN KEY (assigned_user_id)
                               REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Khách hàng';


-- ============================================================
-- 5. CUSTOMER_PHONE_NUMBERS
-- Số điện thoại khách (1 khách nhiều SĐT)
-- ============================================================
CREATE TABLE customer_phone_numbers (
                                        id           INT         NOT NULL AUTO_INCREMENT,
                                        customer_id  CHAR(36)    NOT NULL,
                                        phone_number VARCHAR(20) NOT NULL,

                                        PRIMARY KEY (id),
                                        UNIQUE KEY uq_customer_phone (customer_id, phone_number),
                                        KEY idx_phone (phone_number),
                                        CONSTRAINT fk_cpn_customer FOREIGN KEY (customer_id)
                                            REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Số điện thoại khách hàng';


-- ============================================================
-- 6. CUSTOMER_ADDRESSES
-- Địa chỉ khách hàng (live — có thể thay đổi)
-- ============================================================
CREATE TABLE customer_addresses (
                                    id           CHAR(36)     NOT NULL,
                                    customer_id  CHAR(36)     NOT NULL,
                                    full_name    VARCHAR(255) NOT NULL,
                                    phone_number VARCHAR(20)  NOT NULL,
                                    address      VARCHAR(500)     NULL,
                                    full_address VARCHAR(500) NOT NULL,
                                    province_id  VARCHAR(20)      NULL,
                                    district_id  VARCHAR(20)      NULL,
                                    commune_id   VARCHAR(20)      NULL,
                                    country_code VARCHAR(10)      NULL,
                                    post_code    VARCHAR(20)      NULL,

                                    PRIMARY KEY (id),
                                    KEY idx_customer (customer_id),
                                    CONSTRAINT fk_ca_customer FOREIGN KEY (customer_id)
                                        REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Địa chỉ hiện tại của khách hàng (có thể thay đổi)';


-- ============================================================
-- 7. CUSTOMER_NOTES
-- Ghi chú về khách hàng
-- ============================================================
CREATE TABLE customer_notes (
                                id            CHAR(36)     NOT NULL,
                                customer_id   CHAR(36)     NOT NULL,
                                order_id      VARCHAR(50)      NULL  COMMENT 'Reference mềm, không FK vì order có thể bị xóa',
                                message       TEXT         NOT NULL,
                                created_by_id CHAR(36)         NULL  COMMENT 'FK mềm sang users',
                                created_at    BIGINT           NULL  COMMENT 'Milliseconds timestamp từ Pancake',
                                updated_at    BIGINT           NULL,
                                removed_at    BIGINT           NULL  COMMENT 'NULL = chưa xóa',
                                removed_by_id CHAR(36)         NULL,

                                PRIMARY KEY (id),
                                KEY idx_customer (customer_id),
                                KEY idx_order    (order_id),
                                CONSTRAINT fk_cn_customer FOREIGN KEY (customer_id)
                                    REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Ghi chú về khách hàng';


-- ============================================================
-- 8. PRODUCTS
-- Sản phẩm
-- ============================================================
CREATE TABLE products (
                          id         CHAR(36)     NOT NULL,
                          shop_id    BIGINT       NOT NULL,
                          display_id VARCHAR(50)  NOT NULL  COMMENT 'Mã sản phẩm hiển thị, VD: 35ERE',
                          name       VARCHAR(255) NOT NULL,
                          created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                          PRIMARY KEY (id),
                          KEY idx_shop       (shop_id),
                          KEY idx_display_id (display_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Sản phẩm';


-- ============================================================
-- 9. VARIATIONS
-- Biến thể sản phẩm
-- ============================================================
CREATE TABLE variations (
                            id                    CHAR(36)      NOT NULL,
                            product_id            CHAR(36)      NOT NULL,
                            name                  VARCHAR(255)  NOT NULL,
                            display_id            VARCHAR(50)       NULL,
                            barcode               VARCHAR(100)      NULL,
                            retail_price          BIGINT        NOT NULL DEFAULT 0,
                            retail_price_original BIGINT            NULL,
                            tax_rate              DECIMAL(5,2)      NULL  COMMENT 'VD: 0.05 hoặc 0.08',
                            weight                DECIMAL(10,2)     NULL  DEFAULT 0,
                            measure_id            BIGINT            NULL,
                            measure_exchange_val  INT               NULL  DEFAULT 1,
                            avg_price             DECIMAL(15,2)     NULL  DEFAULT 0,
                            last_imported_price   BIGINT            NULL  DEFAULT 0,
                            created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                            PRIMARY KEY (id),
                            KEY idx_product (product_id),
                            KEY idx_barcode (barcode),
                            CONSTRAINT fk_var_product FOREIGN KEY (product_id)
                                REFERENCES products(id) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Biến thể sản phẩm';


-- ============================================================
-- 10. ORDERS
-- Đơn hàng — bảng trung tâm
-- ============================================================
CREATE TABLE orders (
    -- Định danh
                        id                             BIGINT        NOT NULL  COMMENT 'Order ID từ Pancake, VD: 180157088225568',
                        system_id                      INT               NULL  COMMENT 'ID nội bộ nhỏ hơn, VD: 525884',
                        shop_id                        BIGINT        NOT NULL,
                        page_id                        VARCHAR(50)   NOT NULL,
                        account                        VARCHAR(50)       NULL,
                        account_name                   VARCHAR(255)      NULL,

    -- Khách hàng
                        customer_id                    CHAR(36)          NULL,
                        bill_full_name                 VARCHAR(255)      NULL,
                        bill_phone_number              VARCHAR(20)   NOT NULL,
                        bill_email                     VARCHAR(255)      NULL,

    -- Kho & vận chuyển
                        warehouse_id                   CHAR(36)          NULL,
                        shipping_fee                   BIGINT        NOT NULL DEFAULT 0,
                        is_free_shipping               TINYINT(1)    NOT NULL DEFAULT 0,

    -- Nhân viên
                        assigning_seller_id            CHAR(36)          NULL,
                        assigning_care_id              CHAR(36)          NULL,
                        marketer_id                    CHAR(36)          NULL,
                        creator_id                     CHAR(36)          NULL,
                        last_editor_id                 CHAR(36)          NULL,

    -- Trạng thái
                        status                         TINYINT       NOT NULL DEFAULT 0,
                        status_name                    VARCHAR(50)       NULL  COMMENT 'VD: submitted, shipped, returned...',
                        sub_status                     VARCHAR(50)       NULL,
                        partner_status                 VARCHAR(50)       NULL  COMMENT 'VD: on_the_way, request_received, delivered...',

    -- Khóa đơn
                        is_locked                      TINYINT(1)    NOT NULL DEFAULT 0,
                        is_lock_order_by_system        TINYINT(1)    NOT NULL DEFAULT 0,

    -- Đối tác vận chuyển (tham chiếu nhanh, chi tiết ở bảng order_partners)
                        shop_partner_id                INT               NULL,
                        partner_account_username       VARCHAR(100)      NULL,
                        partner_account_name           VARCHAR(100)      NULL,

    -- Tài chính
                        total_price                    BIGINT        NOT NULL DEFAULT 0  COMMENT 'Giá trước thuế/phí',
                        total_price_after_sub_discount BIGINT        NOT NULL DEFAULT 0  COMMENT 'Giá thực thu (= COD)',
                        total_discount                 BIGINT        NOT NULL DEFAULT 0,
                        surcharge                      BIGINT        NOT NULL DEFAULT 0,
                        tax                            BIGINT        NOT NULL DEFAULT 0,
                        cod                            BIGINT            NULL            COMMENT 'NULL nếu không phải COD',
                        prepaid                        BIGINT        NOT NULL DEFAULT 0,
                        money_to_collect               BIGINT        NOT NULL DEFAULT 0,
                        transfer_money                 BIGINT        NOT NULL DEFAULT 0,
                        cash                           BIGINT        NOT NULL DEFAULT 0,
                        amount_owed_to_customer        BIGINT        NOT NULL DEFAULT 0,
                        exchange_payment               BIGINT        NOT NULL DEFAULT 0,
                        fee_marketplace                BIGINT        NOT NULL DEFAULT 0,
                        partner_fee                    BIGINT        NOT NULL DEFAULT 0,
                        charged_by_momo                BIGINT        NOT NULL DEFAULT 0,
                        charged_by_card                BIGINT        NOT NULL DEFAULT 0,
                        charged_by_qrpay               BIGINT        NOT NULL DEFAULT 0,
                        return_fee                     TINYINT(1)    NOT NULL DEFAULT 0,
                        is_calculation_tax             TINYINT(1)    NOT NULL DEFAULT 0,

    -- Số lượng
                        total_quantity                 INT           NOT NULL DEFAULT 0,
                        items_length                   INT           NOT NULL DEFAULT 0,

    -- Nguồn đơn hàng
                        ads_source                     VARCHAR(100)      NULL,
                        order_sources                  INT               NULL,
                        order_sources_name             VARCHAR(100)      NULL,
                        post_id                        VARCHAR(100)      NULL,
                        ad_id                          VARCHAR(100)      NULL,
                        conversation_id                VARCHAR(100)      NULL,
                        p_utm_source                   VARCHAR(255)      NULL,
                        p_utm_medium                   VARCHAR(255)      NULL,
                        p_utm_campaign                 VARCHAR(255)      NULL,
                        p_utm_content                  VARCHAR(255)      NULL,
                        p_utm_term                     VARCHAR(255)      NULL,
                        p_utm_id                       VARCHAR(255)      NULL,

    -- Flags
                        is_exchange_order              TINYINT(1)    NOT NULL DEFAULT 0,
                        is_live_shopping               TINYINT(1)    NOT NULL DEFAULT 0,
                        is_livestream                  TINYINT(1)    NOT NULL DEFAULT 0,
                        is_smc                         TINYINT(1)    NOT NULL DEFAULT 0,
                        duplicated_ip                  TINYINT(1)    NOT NULL DEFAULT 0,
                        duplicated_phone               TINYINT(1)    NOT NULL DEFAULT 0,
                        received_at_shop               TINYINT(1)    NOT NULL DEFAULT 0,
                        customer_pay_fee               TINYINT(1)    NOT NULL DEFAULT 0,

    -- Ghi chú
                        note                           TEXT              NULL,
                        note_print                     TEXT              NULL,

    -- Thời gian
                        time_assign_seller             DATETIME          NULL,
                        time_assign_care               DATETIME          NULL,
                        time_send_partner              DATETIME          NULL,
                        estimate_delivery_date         DATETIME          NULL,
                        inserted_at                    DATETIME      NOT NULL,
                        updated_at                     DATETIME      NOT NULL,

    -- Misc
                        order_currency                 VARCHAR(10)   NOT NULL DEFAULT 'VND',
                        tracking_link                  TEXT              NULL,
                        order_link                     TEXT              NULL,
                        event_type                     VARCHAR(50)       NULL  COMMENT 'Loại webhook event: update | create',

                        PRIMARY KEY (id),
                        UNIQUE KEY uq_system_id  (system_id),
                        KEY idx_customer         (customer_id),
                        KEY idx_status           (status),
                        KEY idx_partner_status   (partner_status),
                        KEY idx_page             (page_id),
                        KEY idx_shop             (shop_id),
                        KEY idx_seller           (assigning_seller_id),
                        KEY idx_care             (assigning_care_id),
                        KEY idx_inserted_at      (inserted_at),
                        KEY idx_updated_at       (updated_at),
                        KEY idx_bill_phone       (bill_phone_number),

                        CONSTRAINT fk_order_page      FOREIGN KEY (page_id)             REFERENCES pages(id)      ON UPDATE CASCADE,
                        CONSTRAINT fk_order_customer  FOREIGN KEY (customer_id)         REFERENCES customers(id)  ON UPDATE CASCADE ON DELETE SET NULL,
                        CONSTRAINT fk_order_warehouse FOREIGN KEY (warehouse_id)        REFERENCES warehouses(id) ON UPDATE CASCADE ON DELETE SET NULL,
                        CONSTRAINT fk_order_seller    FOREIGN KEY (assigning_seller_id) REFERENCES users(id)      ON UPDATE CASCADE ON DELETE SET NULL,
                        CONSTRAINT fk_order_care      FOREIGN KEY (assigning_care_id)   REFERENCES users(id)      ON UPDATE CASCADE ON DELETE SET NULL,
                        CONSTRAINT fk_order_marketer  FOREIGN KEY (marketer_id)         REFERENCES users(id)      ON UPDATE CASCADE ON DELETE SET NULL,
                        CONSTRAINT fk_order_creator   FOREIGN KEY (creator_id)          REFERENCES users(id)      ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Đơn hàng — bảng trung tâm';


-- ============================================================
-- 11. ORDER_TAGS
-- Tags gắn vào đơn (thay đổi theo thời gian)
-- ============================================================
CREATE TABLE order_tags (
                            id       INT          NOT NULL AUTO_INCREMENT,
                            order_id BIGINT       NOT NULL,
                            tag_id   INT          NOT NULL  COMMENT 'ID tag từ Pancake',
                            tag_name VARCHAR(100) NOT NULL,

                            PRIMARY KEY (id),
                            UNIQUE KEY uq_order_tag (order_id, tag_id),
                            KEY idx_tag (tag_id),
                            CONSTRAINT fk_ot_order FOREIGN KEY (order_id)
                                REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tags gắn vào đơn hàng';


-- ============================================================
-- 12. ORDER_ITEMS
-- Sản phẩm trong đơn hàng
-- ============================================================
CREATE TABLE order_items (
                             id                     BIGINT        NOT NULL  COMMENT 'Item ID từ Pancake, VD: 10942387683',
                             order_id               BIGINT        NOT NULL,
                             product_id             CHAR(36)          NULL,
                             variation_id           CHAR(36)          NULL,
                             assigning_seller_id    CHAR(36)          NULL,
                             measure_group_id       BIGINT            NULL,

                             quantity               INT           NOT NULL DEFAULT 1,
                             return_quantity        INT           NOT NULL DEFAULT 0,
                             returned_count         INT           NOT NULL DEFAULT 0,
                             returning_quantity     INT           NOT NULL DEFAULT 0,
                             added_to_cart_quantity INT           NOT NULL DEFAULT 0,
                             exchange_count         INT           NOT NULL DEFAULT 0,

    -- Giá tại thời điểm đặt (quan trọng — không dùng giá từ bảng variations)
                             retail_price_snapshot  BIGINT        NOT NULL  COMMENT 'Giá bán tại thời điểm đặt hàng',
                             discount_each_product  BIGINT        NOT NULL DEFAULT 0,
                             total_discount         BIGINT        NOT NULL DEFAULT 0,
                             same_price_discount    BIGINT        NOT NULL DEFAULT 0,
                             is_discount_percent    TINYINT(1)    NOT NULL DEFAULT 0,

                             is_bonus_product       TINYINT(1)    NOT NULL DEFAULT 0,
                             is_composite           TINYINT(1)    NOT NULL DEFAULT 0,
                             is_wholesale           TINYINT(1)    NOT NULL DEFAULT 0,
                             one_time_product       TINYINT(1)    NOT NULL DEFAULT 0,

                             note                   TEXT              NULL,
                             note_product           TEXT              NULL,

                             PRIMARY KEY (id),
                             KEY idx_order     (order_id),
                             KEY idx_variation (variation_id),
                             KEY idx_product   (product_id),
                             CONSTRAINT fk_oi_order     FOREIGN KEY (order_id)             REFERENCES orders(id)     ON DELETE CASCADE,
                             CONSTRAINT fk_oi_variation FOREIGN KEY (variation_id)         REFERENCES variations(id) ON UPDATE CASCADE ON DELETE SET NULL,
                             CONSTRAINT fk_oi_seller    FOREIGN KEY (assigning_seller_id)  REFERENCES users(id)      ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Sản phẩm trong đơn hàng';


-- ============================================================
-- 13. ORDER_SHIPPING_SNAPSHOTS
-- Snapshot địa chỉ giao hàng tại thời điểm đặt
-- Bất biến sau khi INSERT — không UPDATE
-- ============================================================
CREATE TABLE order_shipping_snapshots (
                                          id            INT          NOT NULL AUTO_INCREMENT,
                                          order_id      BIGINT       NOT NULL,
                                          full_name     VARCHAR(255) NOT NULL,
                                          phone_number  VARCHAR(20)  NOT NULL,
                                          address       VARCHAR(500)     NULL,
                                          full_address  VARCHAR(500) NOT NULL,
                                          province_id   VARCHAR(20)      NULL,
                                          province_name VARCHAR(100)     NULL,
                                          district_id   VARCHAR(20)      NULL,
                                          district_name VARCHAR(100)     NULL,
                                          commune_id    VARCHAR(20)      NULL,
                                          commune_name  VARCHAR(100)     NULL,
                                          country_code  VARCHAR(10)      NULL,
                                          created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                          PRIMARY KEY (id),
                                          UNIQUE KEY uq_order  (order_id),
                                          KEY idx_province     (province_id),
                                          CONSTRAINT fk_oss_order FOREIGN KEY (order_id)
                                              REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Snapshot địa chỉ giao hàng — bất biến sau INSERT';


-- ============================================================
-- 14. ORDER_WAREHOUSE_SNAPSHOTS
-- Snapshot thông tin kho tại thời điểm đặt
-- Bất biến sau khi INSERT — không UPDATE
-- ============================================================
CREATE TABLE order_warehouse_snapshots (
                                           id           INT          NOT NULL AUTO_INCREMENT,
                                           order_id     BIGINT       NOT NULL,
                                           warehouse_id CHAR(36)         NULL  COMMENT 'Reference mềm — kho có thể bị xóa',
                                           name         VARCHAR(255) NOT NULL,
                                           phone_number VARCHAR(20)      NULL,
                                           full_address VARCHAR(500) NOT NULL,
                                           province_id  VARCHAR(20)      NULL,
                                           district_id  VARCHAR(20)      NULL,
                                           commune_id   VARCHAR(20)      NULL,
                                           created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                           PRIMARY KEY (id),
                                           UNIQUE KEY uq_order (order_id),
                                           CONSTRAINT fk_ows_order FOREIGN KEY (order_id)
                                               REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Snapshot thông tin kho — bất biến sau INSERT';


-- ============================================================
-- 15. ORDER_ITEM_VARIATION_SNAPSHOTS
-- Snapshot thông tin sản phẩm tại thời điểm đặt
-- Bất biến sau khi INSERT — không UPDATE
-- ============================================================
CREATE TABLE order_item_variation_snapshots (
                                                id            INT           NOT NULL AUTO_INCREMENT,
                                                order_item_id BIGINT        NOT NULL,
                                                order_id      BIGINT        NOT NULL,
                                                name          VARCHAR(255)  NOT NULL,
                                                display_id    VARCHAR(50)       NULL,
                                                barcode       VARCHAR(100)      NULL,
                                                retail_price  BIGINT        NOT NULL,
                                                tax_rate      DECIMAL(5,2)      NULL,
                                                weight        DECIMAL(10,2)     NULL,
                                                measure_id    BIGINT            NULL,
                                                measure_name  VARCHAR(100)      NULL  COMMENT 'VD: Hộp, Viên...',
                                                created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                                PRIMARY KEY (id),
                                                UNIQUE KEY uq_order_item (order_item_id),
                                                KEY idx_order            (order_id),
                                                CONSTRAINT fk_oivs_item  FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE,
                                                CONSTRAINT fk_oivs_order FOREIGN KEY (order_id)      REFERENCES orders(id)      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Snapshot thông tin sản phẩm tại thời điểm đặt — bất biến sau INSERT';


-- ============================================================
-- 16. ORDER_STATUS_HISTORY
-- Lịch sử thay đổi trạng thái đơn
-- ============================================================
CREATE TABLE order_status_history (
                                      id          INT          NOT NULL AUTO_INCREMENT,
                                      order_id    BIGINT       NOT NULL,
                                      old_status  TINYINT          NULL  COMMENT 'NULL = lần tạo đơn đầu tiên',
                                      status      TINYINT      NOT NULL,
                                      editor_id   CHAR(36)         NULL  COMMENT 'NULL = hệ thống tự đổi',
                                      editor_name VARCHAR(255)     NULL  COMMENT 'Lưu tên phòng editor bị xóa',
                                      updated_at  DATETIME     NOT NULL,

                                      PRIMARY KEY (id),
                                      KEY idx_order      (order_id),
                                      KEY idx_editor     (editor_id),
                                      KEY idx_updated_at (updated_at),
                                      CONSTRAINT fk_osh_order  FOREIGN KEY (order_id)  REFERENCES orders(id) ON DELETE CASCADE,
                                      CONSTRAINT fk_osh_editor FOREIGN KEY (editor_id) REFERENCES users(id)  ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Lịch sử thay đổi trạng thái đơn hàng';


-- ============================================================
-- 17. ORDER_HISTORIES
-- Audit log toàn bộ thay đổi của đơn
-- ============================================================
CREATE TABLE order_histories (
                                 id             INT          NOT NULL AUTO_INCREMENT,
                                 order_id       BIGINT       NOT NULL,
                                 editor_id      CHAR(36)         NULL  COMMENT 'NULL = hệ thống tự thay đổi',
                                 editor_name    VARCHAR(255)     NULL  COMMENT 'Lưu tên phòng editor bị xóa',
                                 changed_fields LONGTEXT         NULL  COMMENT 'JSON diff: {field: {old, new}}',
                                 updated_at     DATETIME     NOT NULL,

                                 PRIMARY KEY (id),
                                 KEY idx_order      (order_id),
                                 KEY idx_updated_at (updated_at),
                                 CONSTRAINT fk_oh_order  FOREIGN KEY (order_id)  REFERENCES orders(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_oh_editor FOREIGN KEY (editor_id) REFERENCES users(id)  ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Audit log toàn bộ thay đổi của đơn hàng';


-- ============================================================
-- 18. ORDER_PARTNERS
-- Thông tin đối tác vận chuyển (VTP, GHN...)
-- ============================================================
CREATE TABLE order_partners (
                                id                INT           NOT NULL AUTO_INCREMENT,
                                order_id          BIGINT        NOT NULL,

                                partner_id        INT               NULL  COMMENT '3 = VTP, 1 = GHN...',
                                partner_name      VARCHAR(50)       NULL  COMMENT 'VD: VTP, GHN',
                                partner_status    VARCHAR(50)       NULL  COMMENT 'on_the_way | request_received | delivered | returned...',

    -- Mã vận đơn
                                order_number_vtp  VARCHAR(100)      NULL  COMMENT 'Mã VTP: PKE1413592993',
                                order_id_ghn      VARCHAR(100)      NULL,
                                extend_code       VARCHAR(100)      NULL  COMMENT 'PKE180157088225568',

    -- Người giao hàng
                                delivery_name     VARCHAR(255)      NULL,
                                delivery_tel      VARCHAR(20)       NULL,

    -- Tài chính
                                cod               BIGINT        NOT NULL DEFAULT 0,
                                total_fee         BIGINT        NOT NULL DEFAULT 0,
                                partner_fee       BIGINT        NOT NULL DEFAULT 0,

    -- Link in phiếu giao hàng
                                printed_form      TEXT              NULL,

    -- Sort code (VTP)
                                sort_code         VARCHAR(255)      NULL  COMMENT 'VD: HUBNBH-HUYỆN YÊN KHÁNH-KHNC',

    -- Thời gian
                                picked_up_at      DATETIME          NULL  COMMENT 'Thời điểm lấy hàng',
                                first_delivery_at DATETIME          NULL,
                                paid_at           DATETIME          NULL,

    -- Flags
                                is_returned       TINYINT(1)        NULL,
                                is_ghn_v2         TINYINT(1)        NULL,
                                system_created    TINYINT(1)    NOT NULL DEFAULT 0,

                                updated_at        DATETIME          NULL,
                                created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                PRIMARY KEY (id),
                                UNIQUE KEY uq_order           (order_id),
                                KEY idx_partner_id            (partner_id),
                                KEY idx_partner_status        (partner_status),
                                KEY idx_order_number_vtp      (order_number_vtp),
                                CONSTRAINT fk_op_order FOREIGN KEY (order_id)
                                    REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Thông tin đối tác vận chuyển (VTP, GHN...)';


-- ============================================================
-- 19. ORDER_PARTNER_TRACKING
-- Lịch sử vận chuyển (extend_update từ VTP/GHN)
-- ============================================================
CREATE TABLE order_partner_tracking (
                                        id           INT          NOT NULL AUTO_INCREMENT,
                                        order_id     BIGINT       NOT NULL,
                                        tracking_key CHAR(36)         NULL  COMMENT 'UUID từ extend_update.key — dùng deduplicate',
                                        tracking_id  VARCHAR(100)     NULL  COMMENT 'Mã vận đơn: PKE180157088225568',
                                        status       VARCHAR(255) NOT NULL  COMMENT 'VD: Đóng bảng kê đi, Nhận bảng kê đến...',
                                        note         TEXT             NULL,
                                        location     TEXT             NULL,
                                        updated_at   DATETIME     NOT NULL  COMMENT 'Thời điểm xảy ra sự kiện vận chuyển',
                                        created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                        PRIMARY KEY (id),
                                        UNIQUE KEY uq_tracking_key (tracking_key)  COMMENT 'Chống duplicate khi webhook gửi lại',
                                        KEY idx_order      (order_id),
                                        KEY idx_updated_at (updated_at),
                                        CONSTRAINT fk_opt_order FOREIGN KEY (order_id)
                                            REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Lịch sử vận chuyển từ đối tác (VTP, GHN...)';


-- ============================================================
-- 20. ORDER_PARTNER_SERVICE_SNAPSHOTS
-- Snapshot params gửi cho đối tác vận chuyển
-- Bất biến sau khi INSERT — không UPDATE
-- ============================================================
CREATE TABLE order_partner_service_snapshots (
                                                 id                INT          NOT NULL AUTO_INCREMENT,
                                                 order_id          BIGINT       NOT NULL,
                                                 partner_id        INT              NULL,

    -- Người nhận
                                                 receiver_fullname VARCHAR(255) NOT NULL,
                                                 receiver_phone    VARCHAR(20)  NOT NULL,
                                                 receiver_address  VARCHAR(500)     NULL,
                                                 receiver_province VARCHAR(20)      NULL,
                                                 receiver_district VARCHAR(20)      NULL,
                                                 receiver_ward     VARCHAR(20)      NULL,

    -- Người gửi
                                                 sender_fullname   VARCHAR(255)     NULL,
                                                 sender_phone      VARCHAR(20)      NULL,
                                                 sender_address    VARCHAR(500)     NULL,
                                                 sender_province   VARCHAR(20)      NULL,
                                                 sender_district   VARCHAR(20)      NULL,
                                                 sender_ward       VARCHAR(20)      NULL,

    -- Hàng hóa
                                                 product_name      TEXT             NULL,
                                                 product_weight    INT              NULL  COMMENT 'Gram',
                                                 product_quantity  INT              NULL,
                                                 product_price     BIGINT           NULL,
                                                 product_type      VARCHAR(20)      NULL  COMMENT 'VD: HH (hàng hóa)',

    -- Dịch vụ
                                                 order_service     VARCHAR(50)      NULL  COMMENT 'VD: VSL7',
                                                 order_service_add VARCHAR(50)      NULL,
                                                 order_payment     INT              NULL  COMMENT '3 = COD',
                                                 order_note        TEXT             NULL,
                                                 order_number      VARCHAR(100)     NULL,

    -- Tài chính
                                                 money_collection  BIGINT           NULL  COMMENT 'Tiền COD thu hộ',

    -- Thời gian & sort
                                                 delivery_date     DATETIME         NULL,
                                                 enable_sort_code  TINYINT(1)       NULL,

                                                 created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                                 PRIMARY KEY (id),
                                                 UNIQUE KEY uq_order (order_id),
                                                 CONSTRAINT fk_opss_order FOREIGN KEY (order_id)
                                                     REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Snapshot params gửi cho đối tác vận chuyển — bất biến sau INSERT';


-- ============================================================
-- 21. EINVOICES
-- Hóa đơn điện tử
-- ============================================================
CREATE TABLE einvoices (
                           id                  BIGINT        NOT NULL,
                           display_id          INT               NULL  COMMENT 'Số hóa đơn hiển thị',
                           order_id            BIGINT        NOT NULL,
                           shop_id             BIGINT        NOT NULL,
                           warehouse_id        CHAR(36)          NULL,

                           full_name           VARCHAR(255)      NULL,
                           phone_number        VARCHAR(20)       NULL,
                           email               VARCHAR(255)      NULL,

                           invoice_type        TINYINT           NULL  COMMENT '1 = hóa đơn thường',
                           status              TINYINT       NOT NULL DEFAULT 0  COMMENT '0 = chưa phát hành',
                           payment_method      VARCHAR(50)       NULL  COMMENT 'TM/CK',
                           tax                 BIGINT        NOT NULL DEFAULT 0,
                           discount            BIGINT        NOT NULL DEFAULT 0,
                           total_amount        BIGINT        NOT NULL,
                           shipping_fee        BIGINT        NOT NULL DEFAULT 0,
                           included_tax        BIGINT        NOT NULL DEFAULT 0,
                           is_included_tax     TINYINT(1)    NOT NULL DEFAULT 0,

                           template_code       VARCHAR(100)      NULL,
                           invoice_series      VARCHAR(100)      NULL,
                           invoice_no          VARCHAR(100)      NULL  COMMENT 'NULL khi chưa phát hành',
                           verification_code   VARCHAR(100)      NULL,
                           purchaser_tax_code  VARCHAR(100)      NULL,
                           purchaser_unit      VARCHAR(255)      NULL,
                           bank_name           VARCHAR(255)      NULL,
                           bank_account_number VARCHAR(100)      NULL,

                           einvoice_date       DATETIME          NULL,
                           release_date        DATETIME          NULL,

                           note                TEXT              NULL,

                           PRIMARY KEY (id),
                           KEY idx_order   (order_id),
                           KEY idx_display (display_id),
                           CONSTRAINT fk_inv_order FOREIGN KEY (order_id)
                               REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Hóa đơn điện tử';


-- ============================================================
-- 22. EINVOICE_ITEMS
-- Sản phẩm trong hóa đơn (snapshot giá khi xuất hóa đơn)
-- ============================================================
CREATE TABLE einvoice_items (
                                id            BIGINT        NOT NULL,
                                einvoice_id   BIGINT        NOT NULL,
                                order_id      BIGINT        NOT NULL,
                                order_item_id BIGINT            NULL,
                                variation_id  CHAR(36)          NULL,
                                item_code     VARCHAR(100)      NULL,
                                name          VARCHAR(255)  NOT NULL,
                                quantity      INT           NOT NULL,
                                retail_price  BIGINT        NOT NULL,
                                discount      BIGINT        NOT NULL DEFAULT 0,
                                tax           TINYINT       NOT NULL DEFAULT 0  COMMENT '% thuế: 5 hoặc 8',
                                category      VARCHAR(50)       NULL,
                                type          TINYINT           NULL,
                                note          TEXT              NULL,

                                PRIMARY KEY (id),
                                KEY idx_einvoice (einvoice_id),
                                KEY idx_order    (order_id),
                                CONSTRAINT fk_einv_item FOREIGN KEY (einvoice_id)
                                    REFERENCES einvoices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Sản phẩm trong hóa đơn điện tử';


-- ============================================================
-- 23. EINVOICE_SHIPPING_SNAPSHOTS
-- Snapshot địa chỉ xuất trong hóa đơn
-- Bất biến sau khi INSERT — không UPDATE
-- ============================================================
CREATE TABLE einvoice_shipping_snapshots (
                                             id            INT          NOT NULL AUTO_INCREMENT,
                                             einvoice_id   BIGINT       NOT NULL,
                                             full_name     VARCHAR(255)     NULL,
                                             phone_number  VARCHAR(20)      NULL,
                                             full_address  VARCHAR(500) NOT NULL,
                                             province_id   VARCHAR(20)      NULL,
                                             province_name VARCHAR(100)     NULL,
                                             district_id   VARCHAR(20)      NULL,
                                             district_name VARCHAR(100)     NULL,
                                             commune_id    VARCHAR(20)      NULL,
                                             created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                             PRIMARY KEY (id),
                                             UNIQUE KEY uq_einvoice (einvoice_id),
                                             CONSTRAINT fk_ess_einvoice FOREIGN KEY (einvoice_id)
                                                 REFERENCES einvoices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Snapshot địa chỉ trong hóa đơn — bất biến sau INSERT';


-- ============================================================
-- 24. PENDING_FOLLOWUP_NOTIFICATIONS
-- Lưu SDT cần check sau 30 phút khi tạo record Bitable.
-- Scheduler đọc bảng này, gọi API search, gửi tin nhắn nếu chưa link.
-- ============================================================
CREATE TABLE pending_followup_notifications (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    phone_number      VARCHAR(20)    NOT NULL,
    base_id           VARCHAR(64)    NOT NULL,
    table_id          VARCHAR(64)    NOT NULL,
    view_id           VARCHAR(64)         NULL,
    customer_name     VARCHAR(255)         NULL,
    created_record_id VARCHAR(64)         NULL,
    scheduled_at      DATETIME       NOT NULL,
    processed         TINYINT(1)     NOT NULL DEFAULT 0,
    processed_at      DATETIME             NULL,
    note              VARCHAR(500)         NULL,
    retry_count       INT UNSIGNED   NOT NULL DEFAULT 0,
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_pending_scheduled_at (scheduled_at),
    INDEX idx_pending_processed (processed),
    INDEX idx_pending_phone (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;