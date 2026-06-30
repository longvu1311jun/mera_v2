# Chức Năng Đồng Bộ Điểm Danh Từ Lark Suite

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Cách gọi API](#2-cách-gọi-api)
3. [Xử lý dữ liệu](#3-xử-lý-dữ-liệu)
4. [Lưu vào Database](#4-lưu-vào-database)
5. [Luồng xử lý hoàn chỉnh](#5-luồng-xử-lý-hoàn-chỉnh)
6. [Retry và xử lý lỗi](#6-retry-và-xử-lý-lỗi)

---

## 1. Tổng quan

Chức năng đồng bộ điểm danh (Attendance Sync) lấy dữ liệu chấm công từ Lark Suite API và lưu vào 2 bảng:
- `lark_attendance_days` - Tổng hợp điểm danh theo ngày
- `lark_attendance_punches` - Chi tiết từng lần check-in/out

### 1.1 Đặc điểm quan trọng

| Giới hạn | Giá trị |
|-----------|---------|
| Số user mỗi request | Tối đa **20** user_ids |
| Khoảng ngày mỗi request | Tối đa **31** ngày |
| API dùng `employee_type=employee_id` | `user_ids` phải là **Lark user_id**, không phải open_id |

### 1.2 Bảng `lark_attendance_days`

Lưu 1 dòng cho 1 nhân viên / 1 ngày điểm danh.

```sql
lark_attendance_days
├── id                      -- UUID (Primary Key)
├── employee_id             -- Lark user_id (không phải open_id)
├── attendance_date         -- Ngày điểm danh
├── employee_name           -- Tên nhân viên
├── employee_no             -- Mã nhân viên
├── department_name         -- Tên phòng ban
├── attendance_group_name   -- Tên nhóm điểm danh
├── shift_name              -- Ca làm việc
├── timezone                -- Timezone
├── weekday                -- Thứ trong tuần
├── required_hours          -- Số giờ phải làm
├── actual_hours            -- Số giờ thực tế
├── leave_hours             -- Số giờ nghỉ phép
├── overtime_hours          -- Số giờ tăng ca
├── resigned_date           -- Ngày nghỉ việc
├── raw_data                -- JSON (raw data từ API)
├── synced_at               -- Thời điểm đồng bộ
├── created_at
├── updated_at
└── UNIQUE (employee_id, attendance_date)
```

### 1.3 Bảng `lark_attendance_punches`

Lưu từng lần check-in/check-out.

```sql
lark_attendance_punches
├── id                      -- UUID (Primary Key)
├── employee_id             -- Lark user_id
├── attendance_date         -- Ngày điểm danh
├── code                    -- Mã lần chấm (VD: 51503-1-1)
├── title                   -- Tiêu đề (VD: 第1次上班)
├── employee_name           -- Tên nhân viên
├── attendance_group_name   -- Tên nhóm điểm danh
├── weekday                -- Thứ trong tuần
├── punch_type             -- Loại (1=lên ca, 2=xuống ca)
├── punch_no               -- Số lần chấm
├── shift_time             -- Giờ ca (VD: 08:00:00)
├── punch_time             -- Giờ chấm thực tế (VD: 07:25:00)
├── punch_status           -- Trạng thái chấm
├── punch_sub_status       -- Trạng thái phụ
├── status_msg             -- Thông điệp (VD: 正常, 尚未打卡)
├── location_name          -- Tên vị trí chấm công
├── task_id                -- Task ID từ Lark
├── flow_id                -- Flow ID từ Lark
├── note                   -- Ghi chú
├── raw_features           -- JSON features
├── raw_item               -- JSON item đầy đủ
├── synced_at
├── created_at
├── updated_at
└── UNIQUE (employee_id, attendance_date, code)
```

### 1.4 Bảng `lark_sync_jobs`

Theo dõi job sync.

```sql
lark_sync_jobs
├── id
├── job_type               -- 'attendance_daily_sync'
├── trigger_source         -- 'manual' hoặc 'cron'
├── status                 -- pending, running, success, partial_success, failed
├── start_date
├── end_date
├── total_employees
├── total_requests
├── total_success_employees
├── total_invalid_employees
├── total_failed_requests
├── locked_key             -- Key chống chạy trùng
├── error_message
├── meta                   -- JSON metadata
├── started_at
├── finished_at
└── created_by
```

---

## 2. Cách gọi API

### 2.1 Endpoint

```
POST https://open.larksuite.com/open-apis/attendance/v1/user_stats_datas/query?employee_type=employee_id
```

**Headers:**
```
Authorization: Bearer {tenant_access_token}
Content-Type: application/json
```

### 2.2 Request Body

```json
{
  "current_group_only": true,
  "end_date": 20260626,
  "locale": "zh",
  "need_history": true,
  "start_date": 20260626,
  "stats_type": "daily",
  "user_id": "144dc69c",
  "user_ids": [
    "144dc69c",
    "1bac3fg5"
  ]
}
```

| Field | Bắt buộc | Mô tả |
|-------|----------|--------|
| `employee_type` | Có (query) | Luôn dùng `employee_id` |
| `start_date` | Có | Số nguyên YYYYMMDD |
| `end_date` | Có | Số nguyên YYYYMMDD (≤ 31 ngày) |
| `user_ids` | Có | Danh sách Lark user_id (≤ 20) |
| `user_id` | Có | User query report (config cố định) |
| `locale` | Có | Dùng `zh` |
| `stats_type` | Có | Dùng `daily` |
| `current_group_only` | Không | Dùng `true` |
| `need_history` | Không | Dùng `true` |

**Cấu hình môi trường:**
```env
LARK_ATTENDANCE_OPERATOR_USER_ID=144dc69c
LARK_ATTENDANCE_LOCALE=zh
LARK_ATTENDANCE_STATS_TYPE=daily
LARK_ATTENDANCE_CURRENT_GROUP_ONLY=true
LARK_ATTENDANCE_NEED_HISTORY=true
```

### 2.3 Curl mẫu

```bash
curl -X POST 'https://open.larksuite.com/open-apis/attendance/v1/user_stats_datas/query?employee_type=employee_id' \
  -H 'Authorization: Bearer {tenant_access_token}' \
  -H 'Content-Type: application/json' \
  -d '{
    "current_group_only": true,
    "end_date": 20260626,
    "locale": "zh",
    "need_history": true,
    "start_date": 20260626,
    "stats_type": "daily",
    "user_id": "144dc69c",
    "user_ids": ["144dc69c", "1bac3fg5"]
  }'
```

### 2.4 Response mẫu

```json
{
  "code": 0,
  "data": {
    "invalid_user_list": [],
    "user_datas": [
      {
        "name": "Nguyễn Văn Huy_PhongKham",
        "user_id": "144dc69c",
        "datas": [
          {
            "code": "51201",
            "title": "日期",
            "value": "20260626",
            "features": []
          },
          {
            "code": "51503-1-1",
            "title": "打卡结果",
            "value": "正常",
            "features": [
              { "key": "PunchTime", "value": "07:25" },
              { "key": "ShiftTime", "value": "08:00" },
              { "key": "PunchStatus", "value": "1" },
              { "key": "StatusMsg", "value": "正常" }
            ]
          },
          {
            "code": "51503-1-2",
            "title": "打卡结果",
            "value": "尚未打卡",
            "features": [
              { "key": "PunchTime", "value": "-" },
              { "key": "StatusMsg", "value": "尚未打卡" }
            ]
          }
        ]
      }
    ]
  },
  "msg": ""
}
```

---

## 3. Xử lý dữ liệu

### 3.1 Mapping code dữ liệu tổng hợp ngày

| Code | Ý nghĩa | DB Field |
|------|---------|----------|
| `50101` | Tên nhân viên | `employee_name` |
| `50102` | Phòng ban | `department_name` |
| `50103` | Mã nhân viên | `employee_no` |
| `51201` | Ngày điểm danh | `attendance_date` |
| `51202` | Ca làm việc | `shift_name` |
| `51203` | Tên nhóm điểm danh | `attendance_group_name` |
| `51204` | Timezone | `timezone` |
| `51302` | Số giờ phải làm | `required_hours` |
| `51303` | Số giờ thực tế | `actual_hours` |
| `51307` | Số giờ tăng ca | `overtime_hours` |
| `51401` | Số giờ nghỉ phép | `leave_hours` |
| `9` | Ngày nghỉ việc | `resigned_date` |

### 3.2 Mapping code dữ liệu punch

Các item có `code` bắt đầu bằng `51503-` là dữ liệu check-in/out.

| Code | Ý nghĩa |
|------|---------|
| `51503-1-1` | Lần 1 lên ca / check-in |
| `51503-1-2` | Lần 1 xuống ca / check-out |
| `51503-2-1` | Lần 2 lên ca |
| `51503-2-2` | Lần 2 xuống ca |

### 3.3 Mapping features trong punch

```java
// features là mảng [{key, value}]
for (JsonNode f : featuresNode) {
    features.put(f.path("key").asText(), f.path("value").asText());
}

String shiftTime = normalizeTime(features.get("ShiftTime"));     // "08:00"
String punchTime = normalizeTime(features.get("PunchTime"));      // "07:25"
String statusMsg = features.get("StatusMsg");                     // "正常", "尚未打卡"
String locationName = features.get("PunchLocName");
String taskId = features.get("TaskId");
String flowId = features.get("FlowId");
```

### 3.4 Parse dữ liệu từ response

```java
private void processUserData(JsonNode userData, LocalDate fallbackDate) {
    String userId = userData.path("user_id").asText();
    JsonNode datas = userData.path("datas");
    
    // Chuyển datas thành map theo code
    Map<String, JsonNode> dataMap = new HashMap<>();
    for (JsonNode item : datas) {
        String code = item.path("code").asText();
        dataMap.put(code, item);
    }
    
    // Lấy ngày điểm danh từ code 51201
    String attendanceDateStr = getValueFromMap(dataMap, "51201");
    LocalDate attendanceDate = parseDate(attendanceDateStr);
    if (attendanceDate == null) {
        attendanceDate = fallbackDate;
    }
    
    // Upsert dòng tổng hợp ngày
    upsertAttendanceDay(userData, dataMap, userId, attendanceDate);
    
    // Upsert các punch records
    List<String> currentCodes = new ArrayList<>();
    for (JsonNode item : datas) {
        String code = item.path("code").asText();
        if (code.startsWith("51503-")) {
            upsertAttendancePunch(userData, item, userId, attendanceDate, code);
            currentCodes.add(code);
        }
    }
    
    // Xóa punch cũ không còn trong response
    if (!currentCodes.isEmpty()) {
        punchRepository.deleteStalePunches(userId, attendanceDate, currentCodes);
    }
    
    // Tính gio_vao, gio_ra, gio_thuc_te
    punchRepository.computeWorkHours(userId, attendanceDate);
}
```

### 3.5 Helper functions

```java
// Chuyển "20260626" → LocalDate
private LocalDate parseDate(String dateStr) {
    if (dateStr == null || !dateStr.matches("\\d{8}")) return null;
    return LocalDate.parse(
        dateStr.substring(0, 4) + "-" +
        dateStr.substring(4, 6) + "-" +
        dateStr.substring(6, 8)
    );
}

// Chuyển "07:25" → "07:25:00", null nếu "-"
private String normalizeTime(String time) {
    if (time == null || time.isEmpty() || time.equals("-")) return null;
    if (time.matches("\\d{2}:\\d{2}")) return time + ":00";
    if (time.matches("\\d{2}:\\d{2}:\\d{2}")) return time;
    return null;
}

// Lấy value từ map, trả null nếu "-"
private String getValueFromMap(Map<String, JsonNode> map, String code) {
    JsonNode node = map.get(code);
    if (node == null) return null;
    String value = node.path("value").asText();
    if (value == null || value.isEmpty() || value.equals("-")) return null;
    return value;
}
```

---

## 4. Lưu vào Database

### 4.1 Upsert `lark_attendance_days`

**Unique key:** `(employee_id, attendance_date)`

```java
private void upsertAttendanceDay(JsonNode userData, Map<String, JsonNode> dataMap,
                                  String userId, LocalDate attendanceDate) {
    String employeeName = getValueFromMap(dataMap, "50101");
    if (employeeName == null) employeeName = userData.path("name").asText();
    
    Double requiredHours = parseDouble(getValueFromMap(dataMap, "51302"));
    Double actualHours = parseDouble(getValueFromMap(dataMap, "51303"));
    Double leaveHours = parseDouble(getValueFromMap(dataMap, "51401"));
    Double overtimeHours = parseDouble(getValueFromMap(dataMap, "51307"));
    
    String rawDataJson = userData.toString();
    
    dayRepository.upsertAttendanceDay(
        userId,                        // employee_id
        attendanceDate,                 // attendance_date
        nullIfEmpty(employeeName),      // employee_name
        nullIfEmpty(getValueFromMap(dataMap, "50103")), // employee_no
        nullIfEmpty(getValueFromMap(dataMap, "50102")), // department_name
        nullIfEmpty(getValueFromMap(dataMap, "51203")), // attendance_group_name
        nullIfEmpty(getValueFromMap(dataMap, "51202")), // shift_name
        nullIfEmpty(getValueFromMap(dataMap, "51204")), // timezone
        nullIfEmpty(getValueFromMap(dataMap, "weekday")),
        requiredHours,
        actualHours,
        leaveHours,
        overtimeHours,
        parseDate(getValueFromMap(dataMap, "9")), // resigned_date
        null, null, null,
        rawDataJson
    );
}
```

**SQL (PostgreSQL):**
```sql
INSERT INTO lark_attendance_days (
    employee_id, attendance_date, employee_name, employee_no,
    department_name, attendance_group_name, shift_name, timezone, weekday,
    required_hours, actual_hours, leave_hours, overtime_hours,
    resigned_date, gio_vao, gio_ra, gio_thuc_te, raw_data, synced_at, updated_at
)
VALUES (
    :employee_id, :attendance_date, :employee_name, :employee_no,
    :department_name, :attendance_group_name, :shift_name, :timezone, :weekday,
    :required_hours, :actual_hours, :leave_hours, :overtime_hours,
    :resigned_date, :gio_vao, :gio_ra, :gio_thuc_te, :raw_data::jsonb, now(), now()
)
ON CONFLICT (employee_id, attendance_date)
DO UPDATE SET
    employee_name = EXCLUDED.employee_name,
    employee_no = EXCLUDED.employee_no,
    department_name = EXCLUDED.department_name,
    actual_hours = EXCLUDED.actual_hours,
    -- ... các fields khác
    raw_data = EXCLUDED.raw_data,
    synced_at = now(),
    updated_at = now();
```

### 4.2 Upsert `lark_attendance_punches`

**Unique key:** `(employee_id, attendance_date, code)`

```java
private void upsertAttendancePunch(JsonNode userData, JsonNode item, String userId,
                                   LocalDate attendanceDate, String code) {
    // Parse features
    Map<String, String> features = new HashMap<>();
    JsonNode featuresNode = item.path("features");
    for (JsonNode f : featuresNode) {
        features.put(f.path("key").asText(), f.path("value").asText());
    }
    
    String title = item.path("title").asText();
    if (title == null) title = features.get("Title");
    
    punchRepository.upsertAttendancePunch(
        userId,                          // employee_id
        attendanceDate,                  // attendance_date
        code,                            // code (VD: 51503-1-1)
        nullIfEmpty(title),
        nullIfEmpty(features.get("Name")),
        nullIfEmpty(features.get("GroupName")),
        nullIfEmpty(features.get("Weekday")),
        nullIfEmpty(features.get("PunchType")),
        parseInt(features.get("PunchNo")),
        normalizeTime(features.get("ShiftTime")),
        normalizeTime(features.get("PunchTime")),
        nullIfEmpty(features.get("PunchStatus")),
        nullIfEmpty(features.get("PunchSubStatus")),
        nullIfEmpty(features.get("StatusMsg")),
        nullIfEmpty(features.get("PunchLocName")),
        nullIfEmpty(features.get("TaskId")),
        nullIfEmpty(features.get("FlowId")),
        nullIfEmpty(features.get("Note")),
        nullIfEmpty(features.get("UserId")),
        nullIfEmpty(features.get("AvatarUrl")),
        featuresNode.toString(),         // raw_features
        item.toString()                   // raw_item
    );
}
```

### 4.3 Xóa punch cũ (stale)

Sau khi sync, xóa các punch không còn trong response mới:

```java
@Modifying
@Query(value = """
    DELETE FROM lark_attendance_punches
    WHERE employee_id = :employeeId
    AND attendance_date = :attendanceDate
    AND code <> ALL(:currentCodes)
    """, nativeQuery = true)
void deleteStalePunches(@Param("employeeId") String employeeId,
                        @Param("attendanceDate") LocalDate attendanceDate,
                        @Param("currentCodes") List<String> currentCodes);
```

### 4.4 Tính giờ vào/giờ ra

```java
@Modifying
@Query(value = """
    UPDATE lark_attendance_days
    SET gio_vao = (
        SELECT MIN(punch_time)
        FROM lark_attendance_punches
        WHERE employee_id = :employeeId
        AND attendance_date = :attendanceDate
        AND punch_time IS NOT NULL
    ),
    gio_ra = (
        SELECT MAX(punch_time)
        FROM lark_attendance_punches
        WHERE employee_id = :employeeId
        AND attendance_date = :attendanceDate
        AND punch_time IS NOT NULL
    ),
    gio_thuc_te = (
        SELECT ROUND(
            EXTRACT(EPOCH FROM (MAX(punch_time) - MIN(punch_time))) / 3600
        )
        FROM lark_attendance_punches
        WHERE employee_id = :employeeId
        AND attendance_date = :attendanceDate
        AND punch_time IS NOT NULL
    )
    WHERE employee_id = :employeeId
    AND attendance_date = :attendanceDate
    """, nativeQuery = true)
void computeWorkHours(@Param("employeeId") String employeeId,
                     @Param("attendanceDate") LocalDate attendanceDate);
```

---

## 5. Luồng xử lý hoàn chỉnh

### 5.1 Tổng quan flow

```
1. Nhận request: startDate, endDate, employeeIds, departmentIds
   ↓
2. Validate: date range hợp lệ, <= 31 ngày
   ↓
3. Resolve employees:
   - Nếu có employeeIds → lấy theo list đó
   - Nếu có departmentIds → lọc theo department
   - Ngược lại → lấy tất cả active
   ↓
4. Tạo job record trong lark_sync_jobs
   ↓
5. Chia employees thành chunk 20 user_ids
   ↓
6. Chia date range thành chunk <= 31 ngày
   ↓
7. Với mỗi (date range, user chunk):
   a. Gọi Lark API attendance
   b. Parse response
   c. Upsert vào DB
   d. Cập nhật job status
   ↓
8. Hoàn thành job
```

### 5.2 Pseudo-code

```java
public AttendanceSyncResponse syncAttendance(AttendanceSyncRequest request, 
                                              String triggerSource, String createdBy) {
    // 1. Validate
    validateRequest(request);
    
    // 2. Kiểm tra job đang chạy (lock)
    String lockKey = buildLockKey(...);
    if (!request.isForce() && isJobRunning(lockKey)) {
        return AttendanceSyncResponse.error("Job đang chạy");
    }
    
    // 3. Resolve employees
    List<LarkEmployee> employees = resolveEmployees(
        request.getEmployeeIds(), 
        request.getDepartmentIds()
    );
    List<String> employeeIds = employees.stream()
        .map(LarkEmployee::getId)
        .filter(id -> id != null && !id.isBlank())
        .collect(Collectors.toList());
    
    // 4. Tạo job
    String jobId = createJob(...);
    
    // 5. Chia chunks
    List<int[]> dateRanges = splitDateRange(startDate, endDate); // max 31 ngày
    List<List<String>> userChunks = splitUserChunks(employeeIds); // max 20 user
    
    // 6. Xử lý
    for (int[] dateRange : dateRanges) {
        LocalDate rangeStart = LocalDate.ofEpochDay(dateRange[0]);
        LocalDate rangeEnd = LocalDate.ofEpochDay(dateRange[1]);
        int startDateInt = Integer.parseInt(rangeStart.format(DATE_FORMAT));
        int endDateInt = Integer.parseInt(rangeEnd.format(DATE_FORMAT));
        
        for (List<String> userChunk : userChunks) {
            try {
                JsonNode data = attendanceClient.queryUserStatsData(
                    userChunk, startDateInt, endDateInt);
                
                processResponse(data, userChunk, rangeStart, invalidUsers);
                totalSuccess.addAndGet(userChunk.size() - invalidCount);
                
            } catch (Exception e) {
                totalFailed.incrementAndGet();
            }
        }
    }
    
    // 7. Kết thúc job
    String finalStatus = determineJobStatus(totalFailed.get(), totalRequests.get());
    finishJob(jobId, finalStatus, ...);
    
    return AttendanceSyncResponse.success(jobId, ...);
}
```

### 5.3 Các bước chia chunks

```java
// Chia date range thành các đoạn <= 31 ngày
private List<int[]> splitDateRange(LocalDate startDate, LocalDate endDate) {
    List<int[]> ranges = new ArrayList<>();
    LocalDate current = startDate;
    
    while (current.isBefore(endDate) || current.isEqual(endDate)) {
        LocalDate rangeEnd = current.plusDays(30); // max 31 ngày
        if (rangeEnd.isAfter(endDate)) rangeEnd = endDate;
        
        ranges.add(new int[]{
            (int) current.toEpochDay(),
            (int) rangeEnd.toEpochDay()
        });
        
        current = rangeEnd.plusDays(1);
    }
    return ranges;
}

// Chia employees thành chunk 20 user
private List<List<String>> splitUserChunks(List<String> employeeIds) {
    List<List<String>> chunks = new ArrayList<>();
    for (int i = 0; i < employeeIds.size(); i += 20) {
        int end = Math.min(i + 20, employeeIds.size());
        chunks.add(employeeIds.subList(i, end));
    }
    return chunks;
}
```

---

## 6. Retry và xử lý lỗi

### 6.1 Retry policy

**Retry các lỗi:**
- HTTP 429 (Rate limit)
- HTTP 500, 502, 503, 504 (Server errors)
- Network timeout
- Connection reset

**Không retry:**
- HTTP 400 (Bad request)
- HTTP 401, 403 (Auth errors)
- Response `code != 0` (Business errors)

**Cấu hình:**
```
max_attempts = 3
backoff = 1s, 3s, 10s
```

### 6.2 Xử lý job status

```java
private String determineJobStatus(int totalFailed, int totalRequests) {
    if (totalFailed == 0) return "success";
    if (totalFailed < totalRequests) return "partial_success";
    return "failed";
}
```

### 6.3 Xử lý invalid users

```java
private int processResponse(JsonNode data, List<String> requestedUserIds, ...) {
    int invalidCount = 0;
    
    // Ghi log invalid users
    JsonNode invalidList = data.path("invalid_user_list");
    if (invalidList.isArray()) {
        for (JsonNode userId : invalidList) {
            invalidUsers.add(userId.asText());
            invalidCount++;
        }
    }
    
    // Tiếp tục xử lý các user còn lại
    JsonNode userDatas = data.path("user_datas");
    for (JsonNode userData : userDatas) {
        processUserData(userData, fallbackDate);
    }
    
    return invalidCount;
}
```

### 6.4 Lock chống chạy trùng

```java
private String buildLockKey(LocalDate startDate, LocalDate endDate, 
                            List<String> employeeIds, List<Integer> departmentIds) {
    String scope;
    if (employeeIds != null && !employeeIds.isEmpty()) {
        scope = "emp:" + String.join(",", employeeIds.stream().sorted().limit(5).toList());
    } else if (departmentIds != null && !departmentIds.isEmpty()) {
        scope = "dept:" + departmentIds.stream().sorted().collect(Collectors.joining(","));
    } else {
        scope = "all";
    }
    return String.format("attendance:%s:%s:%s", startDate, endDate, scope);
}

private boolean isJobRunning(String lockKey) {
    return jobRepository.findRunningJobByLockKey(lockKey).isPresent();
}
```

### 6.5 Logging

```
[Start]        [lark-attendance] Starting sync: startDate=X, endDate=Y, triggerSource=Z
               [lark-attendance] Created job: {jobId}
[Each chunk]   [lark-attendance] Processed chunk: users=20, invalid=0
[Complete]     [lark-attendance] Job {jobId} finished: status=success, success=160, failed=0
[Error]        [lark-attendance] Chunk failed: Connection timeout
[API error]    [lark-attendance] Error processing user {userId}: {message}
```

---

## 7. Ví dụ Request/Response đầy đủ

### 7.1 Request input

```json
{
  "start_date": "2026-06-26",
  "end_date": "2026-06-26",
  "department_ids": [],
  "employee_ids": [],
  "force": false
}
```

### 7.2 Response output

```json
{
  "success": true,
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Attendance sync completed with status: success",
  "status": "success",
  "total_employees": 168,
  "total_requests": 9,
  "total_success_employees": 168,
  "total_invalid_employees": 0,
  "total_failed_requests": 0
}
```

---

## 8. Các trường hợp đặc biệt

### 8.1 Chưa chấm công

```json
{
  "code": "51503-1-2",
  "value": "尚未打卡",
  "features": [
    { "key": "PunchTime", "value": "-" },
    { "key": "StatusMsg", "value": "尚未打卡" }
  ]
}
```

→ `punch_time = NULL`, `status_msg = "尚未打卡"`, vẫn lưu record

### 8.2 Giá trị `-`

Tất cả giá trị `"-"` được convert thành `NULL` khi lưu DB, nhưng vẫn giữ nguyên trong `raw_data`/`raw_item`.

### 8.3 Employee không thuộc attendance group

Không tạo blank attendance row, chỉ ghi debug log.

---

## 9. Lưu ý quan trọng

1. **`employee_type=employee_id`** → `user_ids` phải là Lark `user_id`, không phải `open_id`

2. **Lấy token từ đâu:**
   - Attendance API dùng `tenant_access_token`
   - Lấy từ `LarkAuthService` hoặc config

3. **Upsert idempotent:**
   - Chạy lại nhiều lần không tạo duplicate
   - Dựa vào unique constraint

4. **Xử lý pagination:**
   - User chunk: 20 user/request
   - Date chunk: 31 ngày/request
   - Không có page_token trong API này
