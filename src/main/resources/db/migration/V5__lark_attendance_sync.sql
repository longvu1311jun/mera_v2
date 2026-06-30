-- ============================================================
-- V5: Lark Attendance Sync Tables
-- MariaDB 10.5+
-- ============================================================

-- Bảng tổng hợp điểm danh theo ngày
CREATE TABLE IF NOT EXISTS lark_attendance_days (
    id                      VARCHAR(36)     NOT NULL PRIMARY KEY,
    employee_id             VARCHAR(64)     NOT NULL COMMENT 'Lark user_id',
    attendance_date         DATE            NOT NULL COMMENT 'Ngày điểm danh',
    employee_name           VARCHAR(255)         NULL,
    employee_no             VARCHAR(64)          NULL COMMENT 'Mã nhân viên',
    department_name         VARCHAR(255)         NULL COMMENT 'Tên phòng ban',
    attendance_group_name   VARCHAR(255)         NULL COMMENT 'Tên nhóm điểm danh',
    shift_name              VARCHAR(255)         NULL COMMENT 'Ca làm việc',
    timezone                VARCHAR(64)          NULL,
    weekday                 VARCHAR(20)          NULL COMMENT 'Thứ trong tuần',
    required_hours          DECIMAL(10,2)        NULL COMMENT 'Số giờ phải làm',
    actual_hours            DECIMAL(10,2)        NULL COMMENT 'Số giờ thực tế',
    leave_hours             DECIMAL(10,2)        NULL COMMENT 'Số giờ nghỉ phép',
    overtime_hours          DECIMAL(10,2)        NULL COMMENT 'Số giờ tăng ca',
    resigned_date           DATE                NULL COMMENT 'Ngày nghỉ việc',
    gio_vao                 TIME                NULL COMMENT 'Giờ vào',
    gio_ra                  TIME                NULL COMMENT 'Giờ ra',
    gio_thuc_te             DECIMAL(10,2)        NULL COMMENT 'Giờ thực tế (tính từ punch)',
    raw_data                JSON                NULL COMMENT 'Raw data từ API',
    synced_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời điểm đồng bộ',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_employee_date (employee_id, attendance_date),
    INDEX idx_attendance_date (attendance_date),
    INDEX idx_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tổng hợp điểm danh theo ngày từ Lark';

-- Bảng chi tiết từng lần check-in/out
CREATE TABLE IF NOT EXISTS lark_attendance_punches (
    id                      VARCHAR(36)     NOT NULL PRIMARY KEY,
    employee_id             VARCHAR(64)     NOT NULL COMMENT 'Lark user_id',
    attendance_date         DATE            NOT NULL COMMENT 'Ngày điểm danh',
    code                    VARCHAR(50)     NOT NULL COMMENT 'Mã lần chấm (VD: 51503-1-1)',
    title                   VARCHAR(255)         NULL COMMENT 'Tiêu đề (VD: 第1次上班)',
    employee_name           VARCHAR(255)         NULL,
    attendance_group_name   VARCHAR(255)         NULL COMMENT 'Tên nhóm điểm danh',
    weekday                 VARCHAR(20)          NULL COMMENT 'Thứ trong tuần',
    punch_type              INT                  NULL COMMENT 'Loại (1=lên ca, 2=xuống ca)',
    punch_no                INT                  NULL COMMENT 'Số lần chấm',
    shift_time              TIME                NULL COMMENT 'Giờ ca (VD: 08:00:00)',
    punch_time              TIME                NULL COMMENT 'Giờ chấm thực tế (VD: 07:25:00)',
    punch_status            VARCHAR(50)          NULL COMMENT 'Trạng thái chấm',
    punch_sub_status        VARCHAR(50)          NULL COMMENT 'Trạng thái phụ',
    status_msg              VARCHAR(255)         NULL COMMENT 'Thông điệp (VD: 正常, 尚未打卡)',
    location_name           VARCHAR(255)         NULL COMMENT 'Tên vị trí chấm công',
    task_id                 VARCHAR(64)          NULL,
    flow_id                 VARCHAR(64)          NULL,
    note                    TEXT                NULL COMMENT 'Ghi chú',
    raw_features            JSON                NULL COMMENT 'JSON features',
    raw_item                JSON                NULL COMMENT 'JSON item đầy đủ',
    synced_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời điểm đồng bộ',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_employee_date_code (employee_id, attendance_date, code),
    INDEX idx_punch_date (attendance_date),
    INDEX idx_punch_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Chi tiết từng lần check-in/out từ Lark';

-- Bảng theo dõi job sync
CREATE TABLE IF NOT EXISTS lark_sync_jobs (
    id                      VARCHAR(36)     NOT NULL PRIMARY KEY,
    job_type                VARCHAR(50)     NOT NULL COMMENT 'VD: attendance_daily_sync, department_sync, employee_sync',
    trigger_source          VARCHAR(20)         NULL COMMENT 'manual hoặc cron',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'pending' COMMENT 'pending, running, success, partial_success, failed',
    start_date              DATE                NULL COMMENT 'Ngày bắt đầu sync',
    end_date                DATE                NULL COMMENT 'Ngày kết thúc sync',
    total_employees         INT                  NULL,
    total_requests          INT                  NULL,
    total_success_employees INT                  NULL,
    total_invalid_employees INT                  NULL,
    total_failed_requests   INT                  NULL,
    locked_key              VARCHAR(255)         NULL COMMENT 'Key chống chạy trùng',
    error_message           TEXT                NULL,
    meta                    JSON                NULL COMMENT 'JSON metadata',
    started_at              DATETIME            NULL,
    finished_at             DATETIME            NULL,
    created_by              VARCHAR(255)         NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_job_type (job_type),
    INDEX idx_status (status),
    INDEX idx_locked_key (locked_key),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Theo dõi job sync Lark';

-- Cập nhật bảng lark_employees để thêm index trên open_id
ALTER TABLE lark_employees ADD INDEX idx_lark_employee_open_id (open_id);

-- Cập nhật bảng lark_departments để thêm index trên open_id
ALTER TABLE lark_departments ADD INDEX idx_lark_dept_open_id (open_id);
