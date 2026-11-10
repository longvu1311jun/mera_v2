/*M!999999\- enable the sandbox mode */ 
-- MariaDB dump 10.19-11.7.2-MariaDB, for Win64 (AMD64)
--
-- Host: 100.70.133.122    Database: pos_db
-- ------------------------------------------------------
-- Server version	11.8.9-MariaDB-ubu2404

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*M!100616 SET @OLD_NOTE_VERBOSITY=@@NOTE_VERBOSITY, NOTE_VERBOSITY=0 */;

--
-- Sequence structure for `order_payment_seq`
--

DROP SEQUENCE IF EXISTS `order_payment_seq`;
CREATE SEQUENCE `order_payment_seq` start with 1 minvalue 1 maxvalue 9223372036854775806 increment by 50 nocache nocycle ENGINE=InnoDB;
DO SETVAL(`order_payment_seq`, 1, 0);

--
-- Sequence structure for `order_status_history_seq`
--

DROP SEQUENCE IF EXISTS `order_status_history_seq`;
CREATE SEQUENCE `order_status_history_seq` start with 1 minvalue 1 maxvalue 9223372036854775806 increment by 50 nocache nocycle ENGINE=InnoDB;
DO SETVAL(`order_status_history_seq`, 87751, 0);

--
-- Table structure for table `app_config`
--

DROP TABLE IF EXISTS `app_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_group` varchar(255) DEFAULT NULL,
  `config_key` varchar(255) NOT NULL,
  `config_value` text DEFAULT NULL,
  `description` text DEFAULT NULL,
  `display_name` varchar(255) DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlb58j8yn1k5qn7efmtic7mc0r` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audit_logs`
--

DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) DEFAULT NULL COMMENT 'ID người dùng (nếu có)',
  `user_name` varchar(255) DEFAULT NULL COMMENT 'Tên người dùng (nếu có)',
  `action` varchar(100) NOT NULL COMMENT 'Hành động: VIEW_REPORT, SAVE_COMBO, EXPORT_EXCEL, etc.',
  `resource` varchar(255) DEFAULT NULL COMMENT 'Tài nguyên: API endpoint, page name',
  `ip_address` varchar(45) DEFAULT NULL COMMENT 'Địa chỉ IP',
  `user_agent` varchar(512) DEFAULT NULL COMMENT 'User Agent của browser',
  `request_method` varchar(10) DEFAULT NULL COMMENT 'HTTP Method: GET, POST, PUT, DELETE',
  `request_path` varchar(512) DEFAULT NULL COMMENT 'Đường dẫn request',
  `status_code` int(11) DEFAULT NULL COMMENT 'HTTP Status Code',
  `status` varchar(50) NOT NULL DEFAULT 'SUCCESS' COMMENT 'Trạng thái: SUCCESS, FAILED, ERROR',
  `error_message` text DEFAULT NULL COMMENT 'Thông báo lỗi (nếu có)',
  `details` text DEFAULT NULL COMMENT 'Chi tiết bổ sung (JSON hoặc text)',
  `created_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT 'Thời gian tạo',
  PRIMARY KEY (`id`),
  KEY `idx_action` (`action`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_status` (`status`),
  KEY `idx_resource` (`resource`),
  KEY `idx_ip_address` (`ip_address`)
) ENGINE=InnoDB AUTO_INCREMENT=10235 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bảng lưu audit log các thao tác của người dùng';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `categories`
--

DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` bigint(20) NOT NULL,
  `shop_id` bigint(20) DEFAULT 1546758,
  `is_admin_category` int(11) DEFAULT NULL,
  `nodes` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`nodes`)),
  `text` varchar(500) DEFAULT NULL,
  `third_party` varchar(255) DEFAULT NULL,
  `inserted_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_vietnamese_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `combo`
--

DROP TABLE IF EXISTS `combo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `combo` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `combo_name` varchar(255) NOT NULL COMMENT 'Tên combo',
  `product_id` varchar(64) DEFAULT NULL COMMENT 'ID sản phẩm',
  `variation_id` varchar(64) DEFAULT NULL COMMENT 'ID variation (nếu có)',
  `product_name` varchar(512) NOT NULL COMMENT 'Tên sản phẩm',
  `unit_price` decimal(18,4) DEFAULT NULL,
  `quantity` int(11) NOT NULL DEFAULT 1 COMMENT 'Số lượng',
  `amount` decimal(18,4) DEFAULT NULL,
  `combo_price` decimal(18,4) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT 'Thời gian tạo',
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Thời gian cập nhật',
  `combo_type` varchar(10) DEFAULT 'LT' COMMENT 'Loại combo: LT hoặc LT_1_2',
  PRIMARY KEY (`id`),
  KEY `idx_combo_name` (`combo_name`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_variation_id` (`variation_id`),
  KEY `idx_combo_combo_type` (`combo_type`),
  CONSTRAINT `fk_combo_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_combo_variation` FOREIGN KEY (`variation_id`) REFERENCES `product_variations` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=12496 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bảng lưu cấu hình combo LT';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `combo1_2`
--

DROP TABLE IF EXISTS `combo1_2`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `combo1_2` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `combo_name` varchar(255) NOT NULL COMMENT 'Tên combo',
  `product_id` varchar(64) DEFAULT NULL COMMENT 'ID sản phẩm',
  `variation_id` varchar(64) DEFAULT NULL COMMENT 'ID variation (nếu có)',
  `product_name` varchar(512) NOT NULL COMMENT 'Tên sản phẩm',
  `unit_price` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT 'Giá đơn vị',
  `quantity` int(11) NOT NULL DEFAULT 1 COMMENT 'Số lượng',
  `amount` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT 'Thành tiền (unit_price * quantity)',
  `combo_price` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT 'Giá combo (tổng của tất cả thành tiền trong combo)',
  `created_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT 'Thời gian tạo',
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Thời gian cập nhật',
  PRIMARY KEY (`id`),
  KEY `idx_combo1_2_name` (`combo_name`),
  KEY `idx_combo1_2_product_id` (`product_id`),
  KEY `idx_combo1_2_variation_id` (`variation_id`),
  CONSTRAINT `fk_combo1_2_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_combo1_2_variation` FOREIGN KEY (`variation_id`) REFERENCES `product_variations` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bảng lưu cấu hình combo LT 1/2';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cskh_base_mapping`
--

DROP TABLE IF EXISTS `cskh_base_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `cskh_base_mapping` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `department_name` varchar(255) DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `khach_hang_table_id` varchar(255) DEFAULT NULL,
  `lark_base_id` varchar(255) DEFAULT NULL,
  `lark_base_name` varchar(255) DEFAULT NULL,
  `lich_hen_table_id` varchar(255) DEFAULT NULL,
  `pos_name` varchar(255) NOT NULL,
  `pos_phone` varchar(255) DEFAULT NULL,
  `pos_user_id` varchar(255) DEFAULT NULL,
  `trao_doi_table_id` varchar(255) DEFAULT NULL,
  `view_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pos_phone` (`pos_phone`),
  KEY `idx_lark_base_id` (`lark_base_id`)
) ENGINE=InnoDB AUTO_INCREMENT=77 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_addresses`
--

DROP TABLE IF EXISTS `customer_addresses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_addresses` (
  `id` varchar(64) NOT NULL COMMENT 'UUID',
  `customer_id` varchar(64) NOT NULL,
  `full_name` varchar(255) DEFAULT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `address` text DEFAULT NULL,
  `full_address` text DEFAULT NULL,
  `province_id` varchar(16) DEFAULT NULL,
  `province_name` varchar(128) DEFAULT NULL,
  `district_id` varchar(16) DEFAULT NULL,
  `district_name` varchar(128) DEFAULT NULL,
  `commune_id` varchar(16) DEFAULT NULL,
  `commune_name` varchar(128) DEFAULT NULL,
  `country_code` varchar(8) DEFAULT NULL,
  `post_code` varchar(16) DEFAULT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_customer` (`customer_id`),
  CONSTRAINT `fk_addr_cust` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Địa chỉ giao hàng của khách hàng';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_note_edit_history`
--

DROP TABLE IF EXISTS `customer_note_edit_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_note_edit_history` (
  `id` varchar(64) NOT NULL,
  `note_id` varchar(64) NOT NULL COMMENT 'FK -> customer_notes.id',
  `created_at` bigint(20) NOT NULL COMMENT 'Timestamp epoch millis từ POS',
  `message` text NOT NULL,
  `images` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`images`)),
  `created_by_id` varchar(64) DEFAULT NULL COMMENT 'uid của created_by',
  `created_by_name` varchar(255) DEFAULT NULL COMMENT 'fb_name của created_by',
  `created_by_pancake_id` varchar(64) DEFAULT NULL,
  `created_by_token` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_note_edit_history_note_id` (`note_id`),
  KEY `idx_note_edit_history_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_notes`
--

DROP TABLE IF EXISTS `customer_notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_notes` (
  `id` varchar(64) NOT NULL COMMENT 'UUID',
  `customer_id` varchar(64) NOT NULL,
  `shop_id` bigint(20) unsigned NOT NULL DEFAULT 1546758,
  `order_id` varchar(64) DEFAULT NULL,
  `message` text NOT NULL,
  `created_by_id` varchar(64) DEFAULT NULL COMMENT 'pos_users.id',
  `created_by_name` varchar(255) DEFAULT NULL,
  `removed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT NULL,
  `images` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Danh sách ảnh đính kèm (POS API trả về array)' CHECK (json_valid(`images`)),
  `links` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Danh sách link đính kèm (POS API trả về array)' CHECK (json_valid(`links`)),
  `created_by_pancake_id` varchar(64) DEFAULT NULL COMMENT 'pancake_id của created_by',
  `created_by_token` varchar(255) DEFAULT NULL COMMENT 'token_for_business của created_by',
  PRIMARY KEY (`id`),
  KEY `idx_cust_note` (`customer_id`),
  KEY `idx_cn_updated` (`updated_at`),
  CONSTRAINT `fk_note_cust` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Ghi chú về khách hàng';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_phone_numbers`
--

DROP TABLE IF EXISTS `customer_phone_numbers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_phone_numbers` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `customer_id` varchar(64) NOT NULL,
  `phone_number` varchar(20) NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `phone9` char(9) GENERATED ALWAYS AS (case when octet_length(`phone_number`) >= 9 then right(`phone_number`,9) end) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cust_phone` (`customer_id`,`phone_number`),
  KEY `idx_phone` (`phone_number`),
  KEY `idx_cpn_phone9` (`phone9`),
  CONSTRAINT `fk_phone_cust` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=440682 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Số điện thoại khách hàng (nhiều số/1 KH)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customer_tags`
--

DROP TABLE IF EXISTS `customer_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_tags` (
  `customer_id` varchar(64) NOT NULL,
  `tag_id` bigint(20) unsigned NOT NULL,
  PRIMARY KEY (`customer_id`,`tag_id`),
  KEY `fk_ctag_tag` (`tag_id`),
  CONSTRAINT `fk_ctag_cust` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ctag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customers`
--

DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `id` varchar(64) NOT NULL COMMENT 'UUID',
  `shop_id` bigint(20) unsigned NOT NULL DEFAULT 1546758,
  `name` varchar(255) NOT NULL,
  `gender` varchar(255) DEFAULT NULL,
  `date_of_birth` date DEFAULT NULL,
  `fb_id` varchar(255) DEFAULT NULL,
  `referral_code` varchar(255) DEFAULT NULL,
  `customer_referral_code` varchar(255) DEFAULT NULL,
  `is_discount_by_level` int(11) DEFAULT NULL,
  `reward_point` int(11) DEFAULT 0,
  `used_reward_point` int(11) DEFAULT 0,
  `current_debts` decimal(18,4) DEFAULT 0.0000,
  `level_id` varchar(255) DEFAULT NULL,
  `is_block` int(11) DEFAULT NULL,
  `order_count` int(11) DEFAULT 0,
  `succeed_order_count` int(11) DEFAULT 0,
  `returned_order_count` int(11) DEFAULT 0,
  `purchased_amount` decimal(18,4) DEFAULT 0.0000,
  `last_order_at` datetime DEFAULT NULL,
  `assigned_user_id` varchar(255) DEFAULT NULL,
  `creator_id` varchar(255) DEFAULT NULL,
  `inserted_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `lt_real` decimal(10,2) DEFAULT NULL,
  `lt_tay` decimal(10,2) DEFAULT NULL,
  `lt_adjustment` decimal(10,2) DEFAULT NULL,
  `note` text DEFAULT NULL COMMENT 'Ghi chú đối soát LT',
  `lt_lark` int(11) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `lt_count` int(11) NOT NULL DEFAULT 0 COMMENT 'So don combo thanh cong (LT) — LtCalculationService',
  PRIMARY KEY (`id`),
  KEY `idx_shop` (`shop_id`),
  KEY `idx_fb_id` (`fb_id`),
  KEY `idx_referral` (`referral_code`),
  KEY `idx_customers_lt_real` (`lt_real`),
  KEY `idx_lt_tay` (`lt_tay`),
  KEY `idx_customers_version` (`version`),
  KEY `idx_customers_fb_id` (`fb_id`),
  KEY `idx_customers_referral_code` (`referral_code`),
  KEY `idx_customers_assigned_user` (`assigned_user_id`),
  KEY `idx_customers_created_at` (`inserted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Khách hàng';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `employee_mappings`
--

DROP TABLE IF EXISTS `employee_mappings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `employee_mappings` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `lark_employee_id` varchar(64) DEFAULT NULL,
  `lark_employee_name` varchar(255) DEFAULT NULL,
  `lark_employee_no` varchar(64) DEFAULT NULL,
  `lark_department` varchar(255) DEFAULT NULL,
  `pos_user_id` varchar(64) DEFAULT NULL,
  `pos_user_name` varchar(255) DEFAULT NULL,
  `pos_user_email` varchar(255) DEFAULT NULL,
  `is_mapped` tinyint(1) DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `lark_hire_date` date DEFAULT NULL,
  `on_time_streak` int(11) DEFAULT 0,
  `last_worked_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lark_employee_id` (`lark_employee_id`),
  KEY `idx_pos_user_id` (`pos_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `global_configs`
--

DROP TABLE IF EXISTS `global_configs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `global_configs` (
  `config_key` varchar(100) NOT NULL,
  `config_value` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lark_attendance_days`
--

DROP TABLE IF EXISTS `lark_attendance_days`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `lark_attendance_days` (
  `id` varchar(36) NOT NULL,
  `employee_id` varchar(64) NOT NULL,
  `attendance_date` date NOT NULL,
  `employee_name` varchar(255) DEFAULT NULL,
  `employee_no` varchar(100) DEFAULT NULL,
  `department_name` text DEFAULT NULL,
  `attendance_group_name` varchar(255) DEFAULT NULL,
  `shift_name` varchar(255) DEFAULT NULL,
  `timezone` varchar(64) DEFAULT NULL,
  `weekday` varchar(64) DEFAULT NULL,
  `required_hours` decimal(8,2) DEFAULT NULL,
  `actual_hours` decimal(8,2) DEFAULT NULL,
  `leave_hours` decimal(8,2) DEFAULT NULL,
  `overtime_hours` decimal(8,2) DEFAULT NULL,
  `resigned_date` date DEFAULT NULL,
  `raw_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`raw_data`)),
  `synced_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `gio_vao` time DEFAULT NULL,
  `gio_ra` time DEFAULT NULL,
  `gio_thuc_te` decimal(8,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_lark_attendance_days_employee_date` (`employee_id`,`attendance_date`),
  KEY `idx_lark_attendance_days_date` (`attendance_date`),
  KEY `idx_lark_attendance_days_employee` (`employee_id`),
  KEY `idx_lark_attendance_days_employee_date` (`employee_id`,`attendance_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lark_attendance_punches`
--

DROP TABLE IF EXISTS `lark_attendance_punches`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `lark_attendance_punches` (
  `id` varchar(36) NOT NULL,
  `employee_id` varchar(64) NOT NULL,
  `attendance_date` date NOT NULL,
  `code` varchar(64) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `employee_name` varchar(255) DEFAULT NULL,
  `attendance_group_name` varchar(255) DEFAULT NULL,
  `weekday` varchar(64) DEFAULT NULL,
  `punch_type` varchar(20) DEFAULT NULL,
  `punch_no` int(11) DEFAULT NULL,
  `shift_time` time DEFAULT NULL,
  `punch_time` time DEFAULT NULL,
  `punch_status` varchar(20) DEFAULT NULL,
  `punch_sub_status` varchar(20) DEFAULT NULL,
  `status_msg` varchar(255) DEFAULT NULL,
  `location_name` text DEFAULT NULL,
  `task_id` varchar(100) DEFAULT NULL,
  `flow_id` varchar(100) DEFAULT NULL,
  `note` text DEFAULT NULL,
  `lark_attendance_user_id` varchar(100) DEFAULT NULL,
  `avatar_url` text DEFAULT NULL,
  `raw_features` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`raw_features`)),
  `raw_item` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`raw_item`)),
  `synced_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_lark_attendance_punches_employee_date_code` (`employee_id`,`attendance_date`,`code`),
  KEY `idx_lark_attendance_punches_date` (`attendance_date`),
  KEY `idx_lark_attendance_punches_employee` (`employee_id`),
  KEY `idx_lark_attendance_punches_employee_date` (`employee_id`,`attendance_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lark_bitable_config`
--

DROP TABLE IF EXISTS `lark_bitable_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `lark_bitable_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `base_id` varchar(255) DEFAULT NULL,
  `base_name` varchar(255) DEFAULT NULL,
  `config_name` varchar(255) NOT NULL,
  `is_default` bit(1) DEFAULT NULL,
  `shop_id` bigint(20) DEFAULT NULL,
  `table_id` varchar(255) DEFAULT NULL,
  `table_name` varchar(255) DEFAULT NULL,
  `user_access_token` varchar(255) DEFAULT NULL,
  `view_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lark_departments`
--

DROP TABLE IF EXISTS `lark_departments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `lark_departments` (
  `id` varchar(64) NOT NULL,
  `name` varchar(255) NOT NULL,
  `parent_id` varchar(64) DEFAULT NULL,
  `open_id` varchar(128) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_lark_dept_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Phòng ban từ Lark';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lark_employee_roles`
--

DROP TABLE IF EXISTS `lark_employee_roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `lark_employee_roles` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `employee_id` varchar(64) NOT NULL,
  `role_name` varchar(128) NOT NULL COMMENT 'sale, care, marketer, admin...',
  `shop_id` bigint(20) unsigned DEFAULT 1546758,
  `granted_at` datetime NOT NULL DEFAULT current_timestamp(),
  `revoked_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_emp_role` (`employee_id`,`role_name`),
  CONSTRAINT `fk_role_emp` FOREIGN KEY (`employee_id`) REFERENCES `lark_employees` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Vai trò của nhân viên theo shop';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lark_employees`
--

DROP TABLE IF EXISTS `lark_employees`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `lark_employees` (
  `id` varchar(64) NOT NULL,
  `open_id` varchar(128) DEFAULT NULL,
  `union_id` varchar(128) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `employee_no` varchar(64) DEFAULT NULL COMMENT 'Mã nhân viên nội bộ',
  `department_id` varchar(64) DEFAULT NULL,
  `job_title` varchar(255) DEFAULT NULL,
  `avatar_url` varchar(1024) DEFAULT NULL,
  `status` int(11) NOT NULL,
  `pos_user_id` varchar(64) DEFAULT NULL,
  `fb_id` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `hire_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_email` (`email`),
  UNIQUE KEY `uq_employee_no` (`employee_no`),
  KEY `idx_department` (`department_id`),
  KEY `idx_pos_user` (`pos_user_id`),
  KEY `idx_lark_emp_department` (`department_id`),
  KEY `idx_lark_emp_email` (`email`),
  KEY `idx_lark_emp_status` (`status`),
  CONSTRAINT `fk_lark_employee_department` FOREIGN KEY (`department_id`) REFERENCES `lark_departments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Nhân viên đồng bộ từ Lark';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lark_pos_sync_log`
--

DROP TABLE IF EXISTS `lark_pos_sync_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `lark_pos_sync_log` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(64) NOT NULL COMMENT 'order, customer, product...',
  `entity_id` varchar(64) NOT NULL,
  `direction` enum('lark_to_pos','pos_to_lark') NOT NULL,
  `status` enum('success','failed','pending') NOT NULL DEFAULT 'pending',
  `payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`payload`)),
  `error_message` text DEFAULT NULL,
  `synced_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_entity` (`entity_type`,`entity_id`),
  KEY `idx_sync_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Log đồng bộ dữ liệu giữa Lark và POS';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lark_sync_jobs`
--

DROP TABLE IF EXISTS `lark_sync_jobs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `lark_sync_jobs` (
  `id` varchar(36) NOT NULL,
  `job_type` varchar(50) NOT NULL,
  `trigger_source` varchar(50) NOT NULL,
  `status` varchar(30) NOT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `total_employees` int(11) NOT NULL DEFAULT 0,
  `total_requests` int(11) NOT NULL DEFAULT 0,
  `total_success_employees` int(11) NOT NULL DEFAULT 0,
  `total_invalid_employees` int(11) NOT NULL DEFAULT 0,
  `total_failed_requests` int(11) NOT NULL DEFAULT 0,
  `locked_key` varchar(255) DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  `meta` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`meta`)),
  `started_at` timestamp NULL DEFAULT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  `created_by` varchar(100) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_lark_sync_jobs_job_type` (`job_type`),
  KEY `idx_lark_sync_jobs_status` (`status`),
  KEY `idx_lark_sync_jobs_locked_key` (`locked_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `optimized_customer`
--

DROP TABLE IF EXISTS `optimized_customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `optimized_customer` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `customer_id` varchar(64) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `note_count` int(11) NOT NULL DEFAULT 0,
  `reason` varchar(500) DEFAULT NULL,
  `customer_created_text` varchar(32) DEFAULT NULL,
  `order_count_before` int(11) NOT NULL DEFAULT 0,
  `succeed_before` int(11) NOT NULL DEFAULT 0,
  `order_count_after` int(11) NOT NULL DEFAULT 0,
  `succeed_after` int(11) NOT NULL DEFAULT 0,
  `first_seen_at` datetime DEFAULT NULL,
  `optimized_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `customer_id` (`customer_id`),
  KEY `idx_oc_optimized_at` (`optimized_at`)
) ENGINE=InnoDB AUTO_INCREMENT=4934 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_assignment_config`
--

DROP TABLE IF EXISTS `order_assignment_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_assignment_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL,
  `config_value` varchar(1000) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`),
  KEY `idx_config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_assignments`
--

DROP TABLE IF EXISTS `order_assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_assignments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL,
  `employee_mapping_id` bigint(20) NOT NULL,
  `lark_employee_id` varchar(64) NOT NULL,
  `lark_employee_name` varchar(255) DEFAULT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `customer_phone` varchar(32) DEFAULT NULL,
  `assigned_at` timestamp NULL DEFAULT current_timestamp(),
  `order_code` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_assign_order_id` (`order_id`),
  KEY `idx_order_assign_lark_employee_id` (`lark_employee_id`),
  KEY `idx_order_assign_assigned_at` (`assigned_at`),
  KEY `idx_order_assign_order_code` (`order_code`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_edit_histories`
--

DROP TABLE IF EXISTS `order_edit_histories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_edit_histories` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) unsigned NOT NULL,
  `editor_id` varchar(64) DEFAULT NULL,
  `field_changed` varchar(128) NOT NULL COMMENT 'tên field thay đổi',
  `old_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`old_value`)),
  `new_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`new_value`)),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_order_edit` (`order_id`),
  CONSTRAINT `fk_edit_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Lịch sử chỉnh sửa đơn hàng (audit log)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_items`
--

DROP TABLE IF EXISTS `order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_items` (
  `id` bigint(20) NOT NULL,
  `order_id` bigint(20) unsigned NOT NULL,
  `product_id` varchar(255) DEFAULT NULL,
  `variation_id` varchar(64) DEFAULT NULL,
  `variation_name` varchar(255) DEFAULT NULL,
  `quantity` int(11) NOT NULL DEFAULT 1,
  `retail_price` double DEFAULT NULL,
  `discount_each_product` decimal(18,4) DEFAULT 0.0000,
  `is_discount_percent` int(11) DEFAULT NULL,
  `same_price_discount` decimal(18,4) DEFAULT 0.0000,
  `total_discount` double DEFAULT NULL,
  `tax_rate` decimal(5,4) DEFAULT 0.0000,
  `weight` decimal(10,2) DEFAULT NULL,
  `note` varchar(255) DEFAULT NULL,
  `note_product` text DEFAULT NULL,
  `is_bonus_product` int(11) DEFAULT NULL,
  `is_composite` int(11) DEFAULT NULL,
  `is_wholesale` int(11) DEFAULT NULL,
  `one_time_product` int(11) DEFAULT NULL,
  `return_quantity` int(11) DEFAULT 0,
  `returning_quantity` int(11) DEFAULT 0,
  `returned_count` int(11) DEFAULT 0,
  `exchange_count` int(11) DEFAULT 0,
  `added_to_cart_quantity` int(11) DEFAULT 0,
  `composite_item_id` varchar(255) DEFAULT NULL,
  `item_id` bigint(20) DEFAULT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_variation` (`variation_id`),
  KEY `idx_oi_order_id` (`order_id`),
  KEY `idx_oi_product_id` (`product_id`),
  CONSTRAINT `fk_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_item_variation` FOREIGN KEY (`variation_id`) REFERENCES `product_variations` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chi tiết sản phẩm trong đơn hàng';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_payments`
--

DROP TABLE IF EXISTS `order_payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_payments` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) unsigned NOT NULL,
  `method` varchar(255) DEFAULT NULL,
  `bank_name` varchar(255) DEFAULT NULL,
  `account_number` varchar(255) DEFAULT NULL,
  `account_name` varchar(255) DEFAULT NULL,
  `amount` decimal(18,4) NOT NULL DEFAULT 0.0000,
  `paid_at` datetime DEFAULT NULL,
  `note` text DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_order_pay` (`order_id`),
  KEY `idx_op_order_id` (`order_id`),
  CONSTRAINT `fk_pay_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chi tiết thanh toán đơn hàng';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_sources`
--

DROP TABLE IF EXISTS `order_sources`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_sources` (
  `id` varchar(100) NOT NULL,
  `shop_id` bigint(20) DEFAULT 1546758,
  `custom_id` varchar(50) DEFAULT NULL,
  `name` varchar(500) DEFAULT NULL,
  `image` varchar(1000) DEFAULT NULL,
  `parent_id` bigint(20) DEFAULT 0,
  `project_id` varchar(100) DEFAULT NULL,
  `link_source_id` varchar(100) DEFAULT NULL,
  `is_removed` int(11) DEFAULT NULL,
  `inserted_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop_id` (`shop_id`),
  KEY `idx_custom_id` (`custom_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_vietnamese_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_status_histories`
--

DROP TABLE IF EXISTS `order_status_histories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_status_histories` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) unsigned NOT NULL,
  `old_status` int(11) DEFAULT NULL,
  `new_status` int(11) NOT NULL,
  `editor_id` varchar(64) DEFAULT NULL,
  `editor_name` varchar(255) DEFAULT NULL,
  `editor_fb` varchar(64) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_status_history_order_status_time` (`order_id`,`new_status`,`updated_at`),
  KEY `idx_order_status` (`order_id`),
  KEY `idx_status_history_order_id` (`order_id`),
  KEY `idx_status_history_updated_at` (`updated_at`),
  KEY `idx_osh_order_id` (`order_id`),
  CONSTRAINT `fk_status_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5127865 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Lịch sử thay đổi trạng thái đơn hàng';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_tags`
--

DROP TABLE IF EXISTS `order_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_tags` (
  `order_id` bigint(20) unsigned NOT NULL,
  `tag_id` bigint(20) unsigned NOT NULL,
  PRIMARY KEY (`order_id`,`tag_id`),
  KEY `fk_otag_tag` (`tag_id`),
  CONSTRAINT `fk_otag_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_otag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint(20) unsigned NOT NULL COMMENT 'system_id từ POS',
  `order_code` varchar(255) DEFAULT NULL,
  `shop_id` bigint(20) unsigned NOT NULL DEFAULT 1546758,
  `page_id` varchar(255) DEFAULT NULL,
  `customer_id` varchar(64) DEFAULT NULL,
  `conversation_id` varchar(255) DEFAULT NULL,
  `post_id` varchar(255) DEFAULT NULL,
  `ad_id` varchar(128) DEFAULT NULL,
  `creator_id` varchar(64) DEFAULT NULL,
  `assigning_seller_id` varchar(64) DEFAULT NULL,
  `assigning_care_id` varchar(64) DEFAULT NULL,
  `marketer_id` varchar(255) DEFAULT NULL,
  `last_editor_id` varchar(255) DEFAULT NULL,
  `warehouse_id` varchar(64) DEFAULT NULL,
  `status` int(11) NOT NULL,
  `sub_status` varchar(255) DEFAULT NULL,
  `status_name` varchar(255) DEFAULT NULL,
  `bill_full_name` varchar(255) DEFAULT NULL,
  `bill_phone_number` varchar(50) DEFAULT NULL,
  `bill_email` varchar(255) DEFAULT NULL,
  `shipping_full_name` varchar(255) DEFAULT NULL,
  `shipping_phone_number` varchar(255) DEFAULT NULL,
  `shipping_address` text DEFAULT NULL,
  `shipping_full_address` text DEFAULT NULL,
  `shipping_province_id` varchar(255) DEFAULT NULL,
  `shipping_province_name` varchar(255) DEFAULT NULL,
  `shipping_district_id` varchar(255) DEFAULT NULL,
  `shipping_district_name` varchar(255) DEFAULT NULL,
  `shipping_commune_id` varchar(255) DEFAULT NULL,
  `shipping_commune_name` varchar(255) DEFAULT NULL,
  `shipping_country_code` varchar(255) DEFAULT NULL,
  `shipping_post_code` varchar(255) DEFAULT NULL,
  `order_sources` bigint(20) DEFAULT NULL,
  `order_sources_name` varchar(255) DEFAULT NULL,
  `ads_source` varchar(64) DEFAULT NULL,
  `p_utm_source` varchar(255) DEFAULT NULL,
  `p_utm_medium` varchar(255) DEFAULT NULL,
  `p_utm_campaign` varchar(255) DEFAULT NULL,
  `p_utm_content` varchar(255) DEFAULT NULL,
  `p_utm_term` varchar(255) DEFAULT NULL,
  `p_utm_id` varchar(255) DEFAULT NULL,
  `is_livestream` int(11) DEFAULT NULL,
  `is_live_shopping` int(11) DEFAULT NULL,
  `total_price` double DEFAULT NULL,
  `total_price_after_sub_discount` decimal(18,4) DEFAULT NULL,
  `total_discount` decimal(18,4) DEFAULT NULL,
  `shipping_fee` decimal(18,4) DEFAULT NULL,
  `surcharge` decimal(18,4) DEFAULT 0.0000,
  `tax` decimal(18,4) DEFAULT NULL,
  `cod` decimal(18,4) DEFAULT NULL,
  `money_to_collect` decimal(18,4) DEFAULT NULL,
  `prepaid` decimal(18,4) DEFAULT NULL,
  `cash` decimal(18,4) DEFAULT 0.0000,
  `transfer_money` decimal(18,4) DEFAULT 0.0000,
  `charged_by_momo` decimal(18,4) DEFAULT 0.0000,
  `charged_by_card` decimal(18,4) DEFAULT 0.0000,
  `charged_by_qrpay` decimal(18,4) DEFAULT 0.0000,
  `exchange_payment` decimal(18,4) DEFAULT 0.0000,
  `exchange_value` decimal(18,4) DEFAULT 0.0000,
  `partner_fee` decimal(18,4) DEFAULT 0.0000,
  `return_fee` decimal(38,2) DEFAULT NULL,
  `fee_marketplace` decimal(18,4) DEFAULT NULL,
  `buyer_total_amount` decimal(18,4) DEFAULT NULL,
  `levera_point` int(11) DEFAULT 0,
  `is_free_shipping` int(11) DEFAULT NULL,
  `is_exchange_order` bit(1) DEFAULT NULL,
  `is_calculation_tax` bit(1) DEFAULT NULL,
  `is_smc` int(11) DEFAULT NULL,
  `customer_pay_fee` bit(1) DEFAULT NULL,
  `received_at_shop` int(11) DEFAULT NULL,
  `partner` varchar(255) DEFAULT NULL,
  `tracking_link` text DEFAULT NULL,
  `time_send_partner` datetime DEFAULT NULL,
  `estimate_delivery_date` date DEFAULT NULL,
  `returned_reason` varchar(255) DEFAULT NULL,
  `returned_reason_name` varchar(255) DEFAULT NULL,
  `note` text DEFAULT NULL,
  `note_print` text DEFAULT NULL,
  `link` varchar(1024) DEFAULT NULL,
  `time_assign_seller` datetime DEFAULT NULL,
  `time_assign_care` datetime DEFAULT NULL,
  `inserted_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `lt_type` tinyint(1) DEFAULT 0,
  `lt_max` int(11) DEFAULT 0,
  `created_at` datetime(6) DEFAULT NULL,
  `customer_address` text DEFAULT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `customer_phone` varchar(255) DEFAULT NULL,
  `last_editor_name` varchar(255) DEFAULT NULL,
  `order_id` bigint(20) DEFAULT NULL,
  `partner_delivery_name` varchar(255) DEFAULT NULL,
  `partner_tracking_id` varchar(255) DEFAULT NULL,
  `raw_data` longtext DEFAULT NULL,
  `account` varchar(255) DEFAULT NULL,
  `account_name` varchar(255) DEFAULT NULL,
  `order_link` text DEFAULT NULL,
  `tick` int(11) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `phone9` char(9) GENERATED ALWAYS AS (case when `customer_phone` is not null and octet_length(regexp_replace(`customer_phone`,'[^0-9]','')) >= 9 then right(regexp_replace(`customer_phone`,'[^0-9]',''),9) end) VIRTUAL,
  `lt_count_snapshot` int(11) DEFAULT NULL COMMENT 'customers.lt_count tai thoi diem don duoc tao — bao cao LT theo thang',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_order_code` (`order_code`),
  KEY `idx_shop` (`shop_id`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_status` (`status`),
  KEY `idx_creator` (`creator_id`),
  KEY `idx_care` (`assigning_care_id`),
  KEY `idx_inserted` (`inserted_at`),
  KEY `idx_orders_lt_type` (`lt_type`),
  KEY `idx_orders_shipping_full_name` (`shipping_full_name`),
  KEY `idx_orders_shipping_phone_number` (`shipping_phone_number`),
  KEY `idx_orders_tick` (`lt_max`),
  KEY `idx_bill_phone` (`bill_phone_number`),
  KEY `idx_orders_version` (`version`),
  KEY `idx_orders_status` (`status`),
  KEY `idx_orders_created_at` (`created_at`),
  KEY `idx_orders_customer_id` (`customer_id`),
  KEY `idx_orders_creator_id` (`creator_id`),
  KEY `idx_orders_shop_id` (`shop_id`),
  KEY `idx_orders_inserted_at` (`inserted_at`),
  KEY `idx_orders_assigning_care_id` (`assigning_care_id`),
  KEY `idx_orders_assigning_seller_id` (`assigning_seller_id`),
  KEY `idx_orders_warehouse_id` (`warehouse_id`),
  KEY `idx_orders_bill_phone` (`bill_phone_number`),
  KEY `idx_orders_shipping_phone` (`shipping_phone_number`),
  KEY `idx_orders_lt_type1` (`lt_type`),
  KEY `idx_orders_phone9` (`phone9`),
  KEY `idx_orders_creator_status_lt` (`creator_id`,`status`,`lt_type`,`lt_count_snapshot`),
  CONSTRAINT `fk_order_cust` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_order_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_orders_creator` FOREIGN KEY (`creator_id`) REFERENCES `pos_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Đơn hàng';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pending_followup_notifications`
--

DROP TABLE IF EXISTS `pending_followup_notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pending_followup_notifications` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `phone_number` varchar(20) NOT NULL,
  `base_id` varchar(64) NOT NULL,
  `table_id` varchar(64) NOT NULL,
  `view_id` varchar(64) DEFAULT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `created_record_id` varchar(64) DEFAULT NULL,
  `scheduled_at` datetime NOT NULL,
  `processed` tinyint(1) NOT NULL DEFAULT 0,
  `processed_at` datetime DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `retry_count` int(10) unsigned NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_pending_scheduled_at` (`scheduled_at`),
  KEY `idx_pending_processed` (`processed`),
  KEY `idx_pending_phone` (`phone_number`)
) ENGINE=InnoDB AUTO_INCREMENT=3542 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_departments`
--

DROP TABLE IF EXISTS `pos_departments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_departments` (
  `id` bigint(20) NOT NULL,
  `name` varchar(255) NOT NULL,
  `shop_id` bigint(20) unsigned DEFAULT 1546758,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Phòng ban trong POS';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_profiles`
--

DROP TABLE IF EXISTS `pos_profiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_profiles` (
  `id` varchar(64) NOT NULL,
  `shop_id` bigint(20) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_sale_group_members`
--

DROP TABLE IF EXISTS `pos_sale_group_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_sale_group_members` (
  `shop_user_id` varchar(64) NOT NULL,
  `sale_group_id` int(11) NOT NULL,
  `permission` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`shop_user_id`,`sale_group_id`),
  KEY `fk_sgm_sg` (`sale_group_id`),
  CONSTRAINT `fk_sgm_sg` FOREIGN KEY (`sale_group_id`) REFERENCES `pos_sale_groups` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sgm_su` FOREIGN KEY (`shop_user_id`) REFERENCES `pos_shop_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_sale_groups`
--

DROP TABLE IF EXISTS `pos_sale_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_sale_groups` (
  `id` int(11) NOT NULL,
  `shop_id` bigint(20) unsigned NOT NULL DEFAULT 1546758,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Nhóm sale/marketing';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_shop`
--

DROP TABLE IF EXISTS `pos_shop`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_shop` (
  `id` bigint(20) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_shop_users`
--

DROP TABLE IF EXISTS `pos_shop_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_shop_users` (
  `id` varchar(64) NOT NULL COMMENT 'shop_user id từ POS',
  `shop_id` bigint(20) unsigned NOT NULL DEFAULT 1546758,
  `user_id` varchar(64) NOT NULL,
  `department_id` bigint(20) DEFAULT NULL,
  `role` varchar(64) DEFAULT NULL COMMENT 'role bitmask',
  `permission_in_sale_group` varchar(32) DEFAULT NULL COMMENT 'member/leader/...',
  `is_assigned` tinyint(1) NOT NULL DEFAULT 0,
  `enable_api` tinyint(1) NOT NULL DEFAULT 0,
  `api_key` varchar(64) DEFAULT NULL,
  `note_api_key` varchar(255) DEFAULT NULL,
  `is_api_key` tinyint(1) NOT NULL DEFAULT 0,
  `pending_order_count` int(11) NOT NULL DEFAULT 0,
  `preferred_shop` int(11) DEFAULT NULL,
  `app_warehouse` varchar(64) DEFAULT NULL,
  `creator_id` varchar(64) DEFAULT NULL,
  `profile_id` varchar(64) DEFAULT NULL,
  `inserted_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_shop_user` (`shop_id`,`user_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_dept` (`department_id`),
  CONSTRAINT `fk_su_dept` FOREIGN KEY (`department_id`) REFERENCES `pos_departments` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_su_user` FOREIGN KEY (`user_id`) REFERENCES `pos_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Nhân viên gắn với shop, chứa role và config';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_users`
--

DROP TABLE IF EXISTS `pos_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_users` (
  `id` varchar(64) NOT NULL COMMENT 'UUID từ POS',
  `name` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `fb_id` varchar(64) DEFAULT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `avatar_url` varchar(1024) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `base_lark` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_fb_id` (`fb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User hệ thống POS (sale, care, marketer)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_customer_facts`
--

DROP TABLE IF EXISTS `problem_customer_facts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_customer_facts` (
  `customer_id` varchar(64) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `inserted_at` datetime DEFAULT NULL,
  `order_count` int(11) NOT NULL DEFAULT 0,
  `succeed_order_count` int(11) NOT NULL DEFAULT 0,
  `active_order_count` int(11) NOT NULL DEFAULT 0,
  `note_count` int(11) NOT NULL DEFAULT 0,
  `last_note_at` datetime DEFAULT NULL,
  `has_received` tinyint(4) NOT NULL DEFAULT 0,
  `last_received_at` datetime DEFAULT NULL,
  `last_order_at` datetime DEFAULT NULL,
  `refused` tinyint(4) NOT NULL DEFAULT 0,
  `refused_uploaded_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`customer_id`),
  KEY `idx_pcf_note` (`note_count`),
  KEY `idx_pcf_inserted` (`inserted_at`),
  KEY `idx_pcf_received` (`has_received`,`last_received_at`),
  KEY `idx_pcf_refused` (`refused`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_customer_facts_state`
--

DROP TABLE IF EXISTS `problem_customer_facts_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_customer_facts_state` (
  `id` tinyint(4) NOT NULL,
  `last_incremental_at` datetime DEFAULT NULL,
  `last_full_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_customer_group_removed`
--

DROP TABLE IF EXISTS `problem_customer_group_removed`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_customer_group_removed` (
  `customer_id` varchar(64) NOT NULL,
  `grp` char(1) NOT NULL,
  `removed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`customer_id`,`grp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_customer_tracking`
--

DROP TABLE IF EXISTS `problem_customer_tracking`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_customer_tracking` (
  `customer_id` varchar(64) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `note_count` int(11) NOT NULL DEFAULT 0,
  `order_count` int(11) NOT NULL DEFAULT 0,
  `succeed_order_count` int(11) NOT NULL DEFAULT 0,
  `reason` varchar(500) DEFAULT NULL,
  `customer_created_text` varchar(32) DEFAULT NULL,
  `first_seen_at` datetime DEFAULT NULL,
  `last_seen_at` datetime DEFAULT NULL,
  PRIMARY KEY (`customer_id`),
  KEY `idx_pct_last_seen` (`last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_substitutions`
--

DROP TABLE IF EXISTS `product_substitutions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_substitutions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `group_id` bigint(20) NOT NULL DEFAULT 0 COMMENT 'ID nhóm thuốc thay thế (tự động tạo)',
  `group_name` varchar(255) NOT NULL DEFAULT '' COMMENT 'Tên nhóm thuốc thay thế',
  `product_id` varchar(64) NOT NULL DEFAULT '' COMMENT 'ID sản phẩm trong nhóm',
  `variation_id` varchar(64) DEFAULT NULL COMMENT 'ID biến thể (có thể NULL)',
  `quantity` int(11) NOT NULL DEFAULT 1 COMMENT 'Số lượng quy đổi (mặc định 1)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=143 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bảng lưu thông tin thuốc thay thế cho combo matching';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_variations`
--

DROP TABLE IF EXISTS `product_variations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_variations` (
  `id` varchar(64) NOT NULL COMMENT 'UUID',
  `product_id` varchar(64) NOT NULL,
  `shop_id` bigint(20) unsigned NOT NULL DEFAULT 1546758,
  `name` varchar(255) DEFAULT NULL,
  `display_id` varchar(255) DEFAULT NULL,
  `barcode` varchar(255) DEFAULT NULL,
  `retail_price` decimal(18,4) NOT NULL DEFAULT 0.0000,
  `retail_price_original` decimal(18,4) DEFAULT NULL,
  `avg_price` decimal(18,4) NOT NULL DEFAULT 0.0000,
  `last_imported_price` decimal(18,4) NOT NULL DEFAULT 0.0000,
  `tax_rate` decimal(5,4) NOT NULL DEFAULT 0.0000 COMMENT 'VD: 0.08 = 8%',
  `weight` decimal(10,2) DEFAULT NULL COMMENT 'gram',
  `is_upsale_product` int(11) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `is_hidden` int(11) DEFAULT NULL,
  `is_locked` int(11) DEFAULT NULL,
  `is_sell_negative` int(11) DEFAULT NULL,
  `average_imported_price` decimal(18,4) DEFAULT NULL,
  `inserted_at` datetime(6) DEFAULT NULL,
  `is_composite` bit(1) DEFAULT NULL,
  `is_removed` bit(1) DEFAULT NULL,
  `is_sell_negative_variation` bit(1) DEFAULT NULL,
  `price_at_counter` decimal(18,4) DEFAULT NULL,
  `remain_quantity` int(11) DEFAULT NULL,
  `retail_price_after_discount` decimal(18,4) DEFAULT NULL,
  `total_purchase_price` decimal(18,4) DEFAULT NULL,
  `wholesale_price` decimal(18,4) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`),
  CONSTRAINT `fk_var_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Biến thể sản phẩm';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` varchar(64) NOT NULL COMMENT 'UUID',
  `shop_id` bigint(20) unsigned NOT NULL DEFAULT 1546758,
  `display_id` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `brand_id` varchar(255) DEFAULT NULL,
  `is_composite` int(11) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `custom_id` varchar(255) DEFAULT NULL,
  `image` text DEFAULT NULL,
  `is_published` bit(1) DEFAULT NULL,
  `measure_group_id` varchar(255) DEFAULT NULL,
  `note` text DEFAULT NULL,
  `note_product` text DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop` (`shop_id`),
  KEY `idx_products_name` (`name`),
  KEY `idx_products_shop_id` (`shop_id`),
  KEY `idx_products_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sản phẩm';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `refused_care`
--

DROP TABLE IF EXISTS `refused_care`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `refused_care` (
  `customer_id` varchar(64) NOT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `uploaded_at` datetime DEFAULT NULL,
  PRIMARY KEY (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `search_config`
--

DROP TABLE IF EXISTS `search_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `search_config` (
  `lark_base_id` varchar(64) NOT NULL,
  `lark_base_name` varchar(255) DEFAULT NULL,
  `lark_obj_type` varchar(50) DEFAULT NULL,
  `pos_user_id` varchar(100) DEFAULT NULL,
  `pos_name` varchar(255) DEFAULT NULL,
  `pos_phone` varchar(20) DEFAULT NULL,
  `department_name` varchar(100) DEFAULT NULL,
  `khach_hang_table_id` varchar(64) DEFAULT NULL,
  `lich_hen_table_id` varchar(64) DEFAULT NULL,
  `trao_doi_table_id` varchar(64) DEFAULT NULL,
  `view_id` varchar(64) DEFAULT NULL,
  `sync_status` int(11) NOT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `last_synced_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `khach_hang_view_id` varchar(64) DEFAULT NULL,
  `lich_hen_view_id` varchar(64) DEFAULT NULL,
  `trao_doi_view_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`lark_base_id`),
  KEY `idx_sync_status` (`sync_status`),
  KEY `idx_pos_phone` (`pos_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cache mapping Lark Base ↔ POS user cho search';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shops`
--

DROP TABLE IF EXISTS `shops`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `shops` (
  `id` bigint(20) unsigned NOT NULL,
  `name` varchar(255) NOT NULL,
  `fb_page_id` varchar(64) DEFAULT NULL,
  `username` varchar(128) DEFAULT NULL,
  `currency` char(3) NOT NULL DEFAULT 'VND',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_fb_page` (`fb_page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cửa hàng / Fanpage';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tags`
--

DROP TABLE IF EXISTS `tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tags` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `shop_id` bigint(20) unsigned NOT NULL DEFAULT 1546758,
  `name` varchar(128) NOT NULL,
  `color` varchar(16) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_shop_tag` (`shop_id`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=5017 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Nhãn tag';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` varchar(100) NOT NULL,
  `shop_id` bigint(20) DEFAULT 1546758,
  `user_id` varchar(100) DEFAULT NULL,
  `profile_id` varchar(100) DEFAULT NULL,
  `department_id` bigint(20) DEFAULT NULL,
  `name` varchar(500) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `phone_number` varchar(50) DEFAULT NULL,
  `avatar_url` varchar(1000) DEFAULT NULL,
  `role` varchar(100) DEFAULT NULL,
  `is_assigned` int(11) DEFAULT NULL,
  `is_assigned_break_time` int(11) DEFAULT NULL,
  `enable_api` int(11) DEFAULT NULL,
  `inserted_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop_id` (`shop_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_users_shop` (`shop_id`),
  KEY `idx_users_lark_user` (`user_id`),
  KEY `idx_users_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_vietnamese_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `variation_warehouse_stock`
--

DROP TABLE IF EXISTS `variation_warehouse_stock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `variation_warehouse_stock` (
  `variation_id` varchar(64) NOT NULL,
  `warehouse_id` varchar(64) NOT NULL,
  `remain_quantity` int(11) NOT NULL DEFAULT 0,
  `actual_remain_qty` int(11) NOT NULL DEFAULT 0,
  `pending_quantity` int(11) NOT NULL DEFAULT 0,
  `waiting_quantity` int(11) NOT NULL DEFAULT 0,
  `returning_quantity` int(11) NOT NULL DEFAULT 0,
  `total_quantity` int(11) NOT NULL DEFAULT 0,
  `selling_avg` decimal(18,6) NOT NULL DEFAULT 0.000000,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`variation_id`,`warehouse_id`),
  KEY `fk_stock_wh` (`warehouse_id`),
  CONSTRAINT `fk_stock_var` FOREIGN KEY (`variation_id`) REFERENCES `product_variations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_stock_wh` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tồn kho biến thể theo kho';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `warehouses`
--

DROP TABLE IF EXISTS `warehouses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `warehouses` (
  `id` varchar(64) NOT NULL COMMENT 'UUID',
  `name` varchar(255) NOT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `address` text DEFAULT NULL,
  `full_address` text DEFAULT NULL,
  `province_id` varchar(255) DEFAULT NULL,
  `district_id` varchar(255) DEFAULT NULL,
  `commune_id` varchar(255) DEFAULT NULL,
  `affiliate_id` varchar(255) DEFAULT NULL,
  `ffm_id` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_warehouses_province` (`province_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Kho hàng';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary table structure for view `xn_t3`
--

DROP TABLE IF EXISTS `xn_t3`;
/*!50001 DROP VIEW IF EXISTS `xn_t3`*/;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8mb4;
/*!50001 CREATE VIEW `xn_t3` AS SELECT
 1 AS `id`,
  1 AS `latest_updated_at` */;
SET character_set_client = @saved_cs_client;

--
-- Dumping routines for database 'pos_db'
--

--
-- Final view structure for view `xn_t3`
--

/*!50001 DROP VIEW IF EXISTS `xn_t3`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_uca1400_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `xn_t3` AS select `o`.`id` AS `id`,max(`osh`.`updated_at`) AS `latest_updated_at` from (`orders` `o` join `order_status_histories` `osh` on(`o`.`id` = `osh`.`order_id`)) where `o`.`order_sources_name` = 'Facebook' and `osh`.`updated_at` >= '2026-02-28 17:00:00' and `osh`.`updated_at` <= '2026-03-31 16:59:59' and `osh`.`new_status` = 1 group by `o`.`id` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*M!100616 SET NOTE_VERBOSITY=@OLD_NOTE_VERBOSITY */;

-- Dump completed on 2026-11-09 10:59:53
