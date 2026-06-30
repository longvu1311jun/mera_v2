# Chức Năng Đồng Bộ Nhân Viên Từ Lark Suite

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Cách gọi API](#2-cách-gọi-api)
3. [Xử lý dữ liệu](#3-xử-lý-dữ-liệu)
4. [Lưu vào Database](#4-lưu-vào-database)
5. [Luồng xử lý hoàn chỉnh](#5-luồng-xử-lý-hoàn-chỉnh)
6. [Retry và xử lý lỗi](#6-retry-và-xử-lý-lỗi)

---

## 1. Tổng quan

Chức năng đồng bộ nhân viên (Employee Sync) lấy dữ liệu từ Lark Suite API và lưu vào bảng `lark_employees` trong database nội bộ.

### 1.1 Mục tiêu

- Đồng bộ toàn bộ nhân viên từ Lark về DB
- Chạy lại nhiều lần không tạo duplicate
- Có xử lý phân trang (pagination)
- Không hardcode token

### 1.2 Bảng Database hiện có

```sql
lark_employees
├── id              -- Lark user_id (Primary Key)
├── open_id         -- Lark open_id (dùng để find/update)
├── union_id        -- Lark union_id
├── name            -- Tên nhân viên
├── email           -- Email
├── phone_number    -- Số điện thoại
├── employee_no     -- Mã nhân viên
├── department_id   -- ID phòng ban trong DB (map từ open_id)
├── job_title       -- Chức danh
├── avatar_url      -- URL avatar
├── status          -- 1=active, 0=inactive
├── pos_user_id     -- (không update)
├── fb_id           -- (không update)
├── created_at
└── updated_at
```

### 1.3 Bảng phòng ban (cần có trước)

```sql
lark_departments
├── id         -- Primary Key (auto increment)
├── name       -- Tên phòng ban
├── parent_id  -- ID phòng ban cha (null cho root)
├── open_id    -- Lark open_department_id
├── created_at
└── updated_at
```

---

## 2. Cách gọi API

### 2.1 API đồng bộ phòng ban (cần gọi TRƯỚC)

**Endpoint:**
```
GET https://open.larksuite.com/open-apis/contact/v3/departments/{department_id}/children
```

**Headers:**
```
Authorization: Bearer {user_token}
Content-Type: application/json
```

**Query Parameters:**
| Parameter | Giá trị | Mô tả |
|-----------|---------|-------|
| `department_id_type` | `open_department_id` | Type của department_id |
| `page_size` | `10` | Số items mỗi page |
| `user_id_type` | `open_id` | Type của user_id |

**Điểm bắt đầu:**
- Gọi lần đầu với `department_id = "0"` để lấy phòng ban root (ví dụ: HOST)
- Sau khi lưu được root, tiếp tục gọi với `open_department_id` của từng phòng ban con

**Curl mẫu:**
```bash
curl -X GET 'https://open.larksuite.com/open-apis/contact/v3/departments/0/children?department_id_type=open_department_id&page_size=10&user_id_type=open_id' \
  -H 'Authorization: Bearer {user_token}'
```

**Response mẫu:**
```json
{
  "code": 0,
  "data": {
    "has_more": false,
    "items": [
      {
        "department_id": "c8956db6gc739dg3",
        "name": "HOST",
        "open_department_id": "od-058b35b9a2042e91906a7e33fc8fa8eb",
        "parent_department_id": "0",
        "member_count": 168,
        "primary_member_count": 82,
        "status": {
          "is_deleted": false
        }
      }
    ]
  },
  "msg": "success"
}
```

**Xử lý phân trang:**
- Nếu `has_more = true`, gọi lại API với `page_token=<token>`
- Tiếp tục cho đến khi `has_more = false`

---

### 2.2 API đồng bộ nhân viên

**Endpoint:**
```
GET https://open.larksuite.com/open-apis/contact/v3/users/find_by_department
```

**Headers:**
```
Authorization: Bearer {user_token}
```

**Query Parameters:**
| Parameter | Giá trị | Mô tả |
|-----------|---------|-------|
| `department_id` | `<open_department_id>` | ID phòng ban |
| `department_id_type` | `open_department_id` | Type của department_id |
| `page_size` | `20` | Số items mỗi page |
| `user_id_type` | `open_id` | Type của user_id |

**Curl mẫu:**
```bash
curl -X GET 'https://open.larksuite.com/open-apis/contact/v3/users/find_by_department?department_id=od-21d5d767c4b188e7d97c7082f82a55ab&department_id_type=open_department_id&page_size=20&user_id_type=open_id' \
  -H 'Authorization: Bearer {user_token}'
```

**Response mẫu:**
```json
{
  "code": 0,
  "data": {
    "has_more": false,
    "items": [
      {
        "open_id": "ou_xxx",
        "union_id": "on_xxx",
        "user_id": "xxx",
        "name": "Tên nhân viên",
        "email": "email@example.com",
        "mobile": "",
        "employee_no": "",
        "job_title": "",
        "avatar": {
          "avatar_origin": "https://...",
          "avatar_640": "https://...",
          "avatar_240": "https://...",
          "avatar_72": "https://..."
        },
        "department_ids": ["od-21d5d767c4b188e7d97c7082f82a55ab"],
        "orders": [
          {
            "department_id": "od-21d5d767c4b188e7d97c7082f82a55ab",
            "is_primary_dept": true
          }
        ],
        "status": {
          "is_activated": true,
          "is_exited": false,
          "is_frozen": false,
          "is_resigned": false,
          "is_unjoin": false
        }
      }
    ]
  },
  "msg": "success"
}
```

---

## 3. Xử lý dữ liệu

### 3.1 Thuật toán đồng bộ phòng ban (BFS)

```
1. Tạo queue, bắt đầu với { openDepartmentId: "0", parentDbId: null }
2. Lặp đến khi queue rỗng:
   a. Pop phần tử từ queue
   b. Gọi API get children của department đó
   c. Với mỗi department con:
      - Nếu is_deleted = true → skip
      - Upsert vào bảng lark_departments
      - Push con vào queue để duyệt tiếp
   d. Xử lý pagination nếu has_more = true
```

### 3.2 Mapping dữ liệu phòng ban

| API Field | DB Field | Ghi chú |
|-----------|----------|---------|
| `item.name` | `name` | Tên phòng ban |
| `item.open_department_id` | `open_id` | Dùng để find/update |
| DB id của cha | `parent_id` | Null cho root |

### 3.3 Xác định phòng ban chính của nhân viên

```java
private String getPrimaryDepartmentOpenId(JsonNode user) {
    // Ưu tiên 1: orders có is_primary_dept = true
    JsonNode orders = user.get("orders");
    if (orders != null && orders.isArray()) {
        for (JsonNode order : orders) {
            if (order.has("is_primary_dept") && order.get("is_primary_dept").asBoolean()) {
                return order.get("department_id").asText();
            }
        }
    }
    
    // Ưu tiên 2: department_ids[0]
    JsonNode deptIds = user.get("department_ids");
    if (deptIds != null && deptIds.isArray() && deptIds.size() > 0) {
        return deptIds.get(0).asText();
    }
    
    return null;
}
```

### 3.4 Mapping dữ liệu nhân viên

| API Field | DB Field | Xử lý |
|-----------|----------|--------|
| `user.user_id` | `id` | Primary Key |
| `user.open_id` | `open_id` | Dùng để find/update |
| `user.union_id` | `union_id` | Null nếu rỗng |
| `user.name` | `name` | Giữ nguyên |
| `user.email` | `email` | Null nếu rỗng |
| `user.mobile` | `phone_number` | Null nếu rỗng |
| `user.employee_no` | `employee_no` | Null nếu rỗng |
| `user.job_title` | `job_title` | Null nếu rỗng |
| `user.avatar.avatar_origin` | `avatar_url` | Fallback avatar_640, avatar_240, avatar_72 |
| DB id của dept | `department_id` | Map từ open_id |
| derived status | `status` | Xem mục 3.5 |

### 3.5 Tính status nhân viên

```java
private Integer getEmployeeStatus(JsonNode user) {
    JsonNode status = user.get("status");
    if (status == null) return 0;

    boolean isActivated = status.has("is_activated") && status.get("is_activated").asBoolean();
    boolean isExited = status.has("is_exited") && status.get("is_exited").asBoolean();
    boolean isFrozen = status.has("is_frozen") && status.get("is_frozen").asBoolean();
    boolean isResigned = status.has("is_resigned") && status.get("is_resigned").asBoolean();
    boolean isUnjoin = status.has("is_unjoin") && status.get("is_unjoin").asBoolean();

    // Active = đã kích hoạt VÀ không exit/frozen/resigned/unjoin
    if (isActivated && !isExited && !isFrozen && !isResigned && !isUnjoin) {
        return 1;
    }
    return 0;
}
```

### 3.6 Helper normalize data

```java
private String nullIfEmpty(String value) {
    if (value == null || value.trim().isEmpty()) {
        return null;
    }
    return value;
}

private String getAvatarUrl(JsonNode user) {
    JsonNode avatar = user.get("avatar");
    if (avatar == null) return null;

    // Ưu tiên avatar_origin, fallback các size khác
    String[] fields = {"avatar_origin", "avatar_640", "avatar_240", "avatar_72"};
    for (String field : fields) {
        String url = getText(avatar, field);
        if (url != null && !url.isEmpty()) {
            return url;
        }
    }
    return null;
}
```

---

## 4. Lưu vào Database

### 4.1 Upsert phòng ban

**Tìm kiếm:** Theo `open_id`

```java
Optional<LarkDepartment> existing = departmentRepo.findByOpenId(openDeptId);

if (existing.isPresent()) {
    // UPDATE
    LarkDepartment dept = existing.get();
    dept.setName(name);
    dept.setParentId(parentDbId);
    dept.setUpdatedAt(LocalDateTime.now());
    departmentRepo.save(dept);
} else {
    // INSERT
    LarkDepartment dept = new LarkDepartment();
    dept.setOpenId(openDeptId);
    dept.setName(name);
    dept.setParentId(parentDbId);
    dept.setCreatedAt(LocalDateTime.now());
    dept.setUpdatedAt(LocalDateTime.now());
    departmentRepo.save(dept);
}
```

### 4.2 Upsert nhân viên

**Tìm kiếm:** Theo `open_id`

```java
Optional<LarkEmployee> existing = employeeRepo.findByOpenId(openId);

if (existing.isPresent()) {
    // UPDATE - không update pos_user_id, fb_id
    LarkEmployee emp = existing.get();
    emp.setId(userId); // Update id to user_id
    emp.setUnionId(unionId);
    emp.setName(name);
    emp.setEmail(email);
    emp.setPhoneNumber(phone);
    emp.setEmployeeNo(employeeNo);
    emp.setDepartmentId(departmentDbId);
    emp.setJobTitle(jobTitle);
    emp.setAvatarUrl(avatarUrl);
    emp.setStatus(status);
    emp.setUpdatedAt(LocalDateTime.now());
    employeeRepo.save(emp);
} else {
    // INSERT
    LarkEmployee emp = new LarkEmployee();
    emp.setId(userId);
    emp.setOpenId(openId);
    emp.setUnionId(unionId);
    emp.setName(name);
    emp.setEmail(email);
    emp.setPhoneNumber(phone);
    emp.setEmployeeNo(employeeNo);
    emp.setDepartmentId(departmentDbId);
    emp.setJobTitle(jobTitle);
    emp.setAvatarUrl(avatarUrl);
    emp.setStatus(status);
    emp.setCreatedAt(LocalDateTime.now());
    emp.setUpdatedAt(LocalDateTime.now());
    employeeRepo.save(emp);
}
```

---

## 5. Luồng xử lý hoàn chỉnh

### 5.1 Thứ tự bắt buộc

```
1. Sync DEPARTMENTS trước (từ department_id = "0")
   ↓
2. Lấy toàn bộ open_id phòng ban từ bảng lark_departments
   ↓
3. Sync EMPLOYEES theo từng phòng ban
   ↓
4. Kết thúc
```

### 5.2 Pseudo-code tổng thể

```java
public SyncResult runSync(String userToken) {
    // Bước 1: Sync departments
    log.info("[lark-sync] start sync departments");
    SyncResult.DepartmentResult deptResult = syncAllDepartments(userToken);
    
    // Bước 2: Sync employees
    log.info("[lark-sync] start sync employees");
    SyncResult.EmployeeResult empResult = syncAllEmployees(userToken);
    
    // Log kết quả
    log.info("[lark-sync] departments inserted={} updated={} skipped_deleted={}",
        deptResult.getInserted(), deptResult.getUpdated(), deptResult.getSkippedDeleted());
    log.info("[lark-sync] employees inserted={} updated={}",
        empResult.getInserted(), empResult.getUpdated());
    
    return result;
}

public SyncResult.DepartmentResult syncAllDepartments(String userToken) {
    // 1. Tạo queue bắt đầu từ root (department_id = "0")
    Queue<QueueItem> queue = new LinkedList<>();
    queue.add(new QueueItem("0", null));
    Set<String> visited = new HashSet<>();
    
    while (!queue.isEmpty()) {
        QueueItem current = queue.poll();
        
        // Skip nếu đã visited
        if (visited.contains(current.openDepartmentId)) continue;
        visited.add(current.openDepartmentId);
        
        // 2. Gọi API get children
        String pageToken = null;
        do {
            response = larkApiClient.getDepartmentChildren(
                current.openDepartmentId, pageToken, userToken);
            
            // 3. Xử lý từng department
            for (JsonNode item : response.getItems()) {
                if (isDeletedDepartment(item)) continue;
                
                // Upsert department
                dept = upsertDepartment(item, current.parentDbId);
                
                // Thêm vào queue để duyệt children
                queue.add(new QueueItem(
                    item.open_department_id, 
                    dept.getId()
                ));
            }
            
            // 4. Xử lý pagination
            pageToken = response.hasMore() ? response.pageToken : null;
            
        } while (pageToken != null);
    }
    
    return result;
}

public SyncResult.EmployeeResult syncAllEmployees(String userToken) {
    // 1. Lấy tất cả departments từ DB
    List<LarkDepartment> allDepts = departmentRepo.findAll();
    Map<String, LarkDepartment> deptMap = ...;
    
    // 2. Với mỗi department, gọi API lấy employees
    for (LarkDepartment dept : allDepts) {
        syncEmployeesByDepartment(dept, deptMap, userToken);
    }
    
    return result;
}

public void syncEmployeesByDepartment(LarkDepartment dept, ...) {
    String pageToken = null;
    
    do {
        // Gọi API find users by department
        response = larkApiClient.findUsersByDepartment(
            dept.getOpenId(), pageToken, userToken);
        
        // Xử lý từng user
        for (JsonNode user : response.getItems()) {
            // Xác định department_id (map open_id → DB id)
            String primaryDeptOpenId = getPrimaryDepartmentOpenId(user);
            
            if (deptMap.containsKey(primaryDeptOpenId)) {
                employeeDepartmentDbId = deptMap.get(primaryDeptOpenId).getId();
            } else {
                employeeDepartmentDbId = dept.getId(); // fallback
            }
            
            // Upsert employee
            upsertEmployee(user, openId, employeeDepartmentDbId);
        }
        
        // Pagination
        pageToken = response.hasMore() ? response.pageToken : null;
        
    } while (pageToken != null);
}
```

---

## 6. Retry và xử lý lỗi

### 6.1 Retry policy

Retry các lỗi tạm thời:
- HTTP 429 (Rate limit)
- HTTP 500, 502, 503, 504 (Server errors)

**Không retry:**
- HTTP 400, 401, 403 (Client errors)
- Response `code != 0` (Business errors)

**Cấu hình:**
```
max_attempts = 3
backoff = 1s, 2s, 4s (exponential)
```

### 6.2 Xử lý lỗi

```java
try {
    response = larkApiClient.getDepartmentChildren(...);
} catch (LarkApiException e) {
    // Log lỗi (không log token)
    log.warn("[lark-sync] error endpoint=/contact/v3/departments/... " +
              "params=parent_id={} code={} msg={}",
        current.openDepartmentId, e.getErrorCode(), e.getMessage());
    
    // Thoát vòng lặp của department hiện tại
    // Tiếp tục với department tiếp theo
    break;
}
```

### 6.3 Logging

```
[Start]          [lark-sync] start sync departments
                  [lark-sync] start sync employees
[Each page]      [lark-sync] department children fetched parent=<id> count=<n> has_more=<true|false>
                  [lark-sync] employees fetched department=<id> count=<n> has_more=<true|false>
[Summary]        [lark-sync] departments inserted=<n> updated=<n> skipped_deleted=<n>
                  [lark-sync] employees inserted=<n> updated=<n>
                  [lark-sync] done duration_ms=<n>
[Error]          [lark-sync] error endpoint=<path> params=<masked> code=<n> msg=<text>
```

**Lưu ý:** Không log token trong bất kỳ log nào.

---

## 7. Cấu hình môi trường

```env
LARK_BASE_URL=https://open.larksuite.com
LARK_USER_ACCESS_TOKEN=<user_token>
LARK_DEPARTMENT_PAGE_SIZE=10
LARK_EMPLOYEE_PAGE_SIZE=20
```

**Lưu ý quan trọng:**
- Phải dùng **user_token** (không phải tenant_token) vì tenant token không có quyền truy cập contact API
- User token cần có scope: `contact:department:readonly` và `contact:user:readonly`
