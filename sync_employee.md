# Luồng Đồng Bộ Nhân Viên (Employee Sync)

## 1. Tổng Quan

Chức năng đồng bộ nhân viên lấy danh sách user từ **POS API** và lưu vào database **NhiKhoa** qua 6 bảng:

```
┌─────────────────┐     GET /shops/{shopId}/users      ┌─────────────────┐
│   POS API       │ ─────────────────────────────────► │  MeRa V2 App    │
│ pos.pages.fm    │                                    │                 │
└─────────────────┘                                    │  - Fetch API    │
                                                         │  - Map to DTO   │
                                                         │  - Save to DB    │
                                                         └────────┬────────┘
                                                                  │
                         ┌─────────────────────────────────────────┘
                         ▼
              ┌──────────────────┐    ┌──────────────────┐
              │   pos_users      │    │  pos_shop_users  │
              ├──────────────────┤    ├──────────────────┤
              │  pos_profiles    │    │ pos_departments   │
              ├──────────────────┤    ├──────────────────┤
              │ pos_sale_groups  │    │pos_sale_group_   │
              │                  │    │members            │
              └──────────────────┘    └──────────────────┘
```

## 2. Trigger Đồng Bộ

### 2.1 Qua Web (HTML Page)
- **URL**: `GET /dongbo`
- **Page**: `templates/dongbo.html`
- **Action**: User click nút "Đồng bộ nhân viên" trên giao diện

### 2.2 Qua API
- **URL**: `GET /api/employees/sync`
- **Controller**: `DongBoController.syncEmployees()`
- **Response**: JSON chứa `EmployeeSyncResult`

## 3. Gọi POS API

### 3.1 Thông Tin API

| Thông số | Giá trị |
|----------|---------|
| **Method** | GET |
| **URL** | `https://pos.pages.fm/api/v1/shops/{shopId}/users` |
| **Query Params** | `api_key={apiKey}` |
| **Timeout** | Connect: 10s, Read: 60s |

### 3.2 Cấu Hình (application.properties)

```properties
pos.api.base-url=https://pos.pages.fm/api/v1
pos.api.shop-id=1022012469
pos.api.api-key=a997e6e7d3b74de2a2badde3de03e8c8
pos.api.connect-timeout=10000
pos.api.read-timeout=60000
```

### 3.3 Code Gọi API

```java
// EmployeeApiClient.fetchAllUsers()
String url = baseUrl + "/shops/" + shopId + "/users?api_key=" + apiKey;
ResponseEntity<EmployeeApiResponse> response = restTemplate.getForEntity(url, EmployeeApiResponse.class);
```

### 3.4 Response Từ API

```json
{
  "data": [
    {
      "id": "shop_user_id_123",
      "shop_id": 1022012469,
      "user_id": "user_abc",
      "department_id": 1,
      "role": "admin",
      "permission_in_sale_group": "full",
      "is_assigned": true,
      "enable_api": true,
      "api_key": "key_xyz",
      "note_api_key": "Key for API",
      "is_api_key": true,
      "pending_order_count": 5,
      "preferred_shop": 1022012469,
      "app_warehouse": "warehouse_1",
      "creator_id": "creator_001",
      "profile_id": "profile_xyz",
      "inserted_at": "2024-01-15T10:30:00Z",
      "department": {
        "id": 1,
        "name": "Kinh Doanh"
      },
      "sale_group": {
        "id": 100,
        "name": "Nhóm A"
      },
      "user": {
        "id": "user_abc",
        "name": "Nguyễn Văn A",
        "email": "nva@email.com",
        "fb_id": "fb_123",
        "phone_number": "0912345678",
        "avatar_url": "https://..."
      }
    }
  ]
}
```

## 4. Quy Trình Xử Lý (4 Phase)

### 4.1 Phase 1: Collect IDs

Thu thập tất cả ID từ response để load cache:

```java
Set<String> userIds = new HashSet<>();          // Từ user_id và user.id
Set<String> profileIds = new HashSet<>();        // Từ profile_id
Set<String> shopUserIds = new HashSet<>();      // Từ id (shopUserId)
Set<Long> departmentIds = new HashSet<>();       // Từ department_id
Set<Integer> saleGroupIds = new HashSet<>();    // Từ sale_group.id
```

### 4.2 Phase 2: Load Cache

Load toàn bộ entity đang tồn tại trong DB vào memory cache:

```java
Map<String, PosUser> userCache = posUserRepository.findAllById(userIds);
Map<String, PosProfile> profileCache = posProfileRepository.findAllById(profileIds);
Map<String, PosShopUser> shopUserCache = posShopUserRepository.findAllById(shopUserIds);
Map<Long, PosDepartment> departmentCache = posDepartmentRepository.findAllById(departmentIds);
Map<Integer, PosSaleGroup> saleGroupCache = posSaleGroupRepository.findAllById(saleGroupIds);
```

### 4.3 Phase 3: Process Each Employee

Với mỗi employee từ API:

```
┌─────────────────────────────────────────────────────────────┐
│                    PROCESS EMPLOYEE                         │
├─────────────────────────────────────────────────────────────┤
│ 1. PosUser          ← user_id field hoặc user.id nested    │
│ 2. PosProfile       ← profile_id                           │
│ 3. PosDepartment    ← department + department_id           │
│ 4. PosSaleGroup     ← sale_group                           │
│ 5. PosShopUser      ← id (shopUserId) + userId + dept      │
│ 6. PosSaleGroupMember ← shopUserId + saleGroupId           │
└─────────────────────────────────────────────────────────────┘
```

**Upsert Logic**: Nếu đã tồn tại trong cache → UPDATE, chưa có → INSERT

### 4.4 Phase 4: Save Theo Thứ Tự Phụ Thuộc

```
Priority 1 (Root)     → Priority 2 (Dependent)  → Priority 3 (Leaf)
─────────────────────────────────────────────────────────────────────
PosUser               → PosShopUser
PosProfile            → PosSaleGroupMember
PosDepartment
PosSaleGroup
```

```java
// 1. Save root entities first
posUserRepository.saveAll(usersToSave);
entityManager.flush();

posProfileRepository.saveAll(profilesToSave);
entityManager.flush();

posDepartmentRepository.saveAll(departmentsToSave);
entityManager.flush();

posSaleGroupRepository.saveAll(saleGroupsToSave);
entityManager.flush();

// 2. Save dependent entities
posShopUserRepository.saveAll(shopUsersToSave);
entityManager.flush();

// 3. Save leaf entities
posSaleGroupMemberRepository.saveAll(membersToSave);
```

## 5. Database Schema

### 5.1 pos_users

| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR(64) PK | User ID từ API |
| name | VARCHAR(255) | Tên user |
| email | VARCHAR(255) | Email |
| fb_id | VARCHAR(64) | Facebook ID |
| phone_number | VARCHAR(20) | Số điện thoại |
| avatar_url | VARCHAR(1024) | URL avatar |
| base_lark | VARCHAR(100) | Lark user ID |
| created_at | DATETIME | Thời gian tạo |
| updated_at | DATETIME | Thời gian cập nhật |

### 5.2 pos_shop_users

| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR(64) PK | Shop User ID (API field: id) |
| shop_id | BIGINT | Shop ID |
| user_id | VARCHAR(64) FK | Liên kết pos_users |
| department_id | BIGINT FK | Liên kết pos_departments |
| profile_id | VARCHAR(64) FK | Liên kết pos_profiles |
| role | VARCHAR(64) | Vai trò |
| permission_in_sale_group | VARCHAR(32) | Quyền trong nhóm bán |
| is_assigned | BOOLEAN | Đã được gán |
| enable_api | BOOLEAN | Bật API |
| api_key | VARCHAR(64) | API Key |
| note_api_key | VARCHAR(255) | Ghi chú API Key |
| is_api_key | BOOLEAN | Là API Key |
| pending_order_count | INT | Số đơn chờ |
| preferred_shop | INT | Shop ưu tiên |
| app_warehouse | VARCHAR(64) | Kho app |
| creator_id | VARCHAR(64) | Người tạo |
| inserted_at | DATETIME | Thời gian insert |
| updated_at | DATETIME | Thời gian cập nhật |

### 5.3 pos_profiles

| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR(64) PK | Profile ID |
| shop_id | BIGINT | Shop ID |
| name | VARCHAR(255) | Tên profile |
| created_at | DATETIME | Thời gian tạo |
| updated_at | DATETIME | Thời gian cập nhật |

### 5.4 pos_departments

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Department ID |
| name | VARCHAR(255) | Tên phòng ban |
| shop_id | BIGINT | Shop ID |
| created_at | DATETIME | Thời gian tạo |

### 5.5 pos_sale_groups

| Column | Type | Description |
|--------|------|-------------|
| id | INT PK | Sale Group ID |
| shop_id | BIGINT | Shop ID |
| name | VARCHAR(255) | Tên nhóm bán |

### 5.6 pos_sale_group_members

| Column | Type | Description |
|--------|------|-------------|
| shop_user_id | VARCHAR(64) PK | FK to pos_shop_users |
| sale_group_id | INT PK | FK to pos_sale_groups |
| permission | VARCHAR(32) | Quyền trong nhóm |

## 6. Mapping Dữ Liệu

### 6.1 API → PosUser

```java
PosUser user = new PosUser();
user.setId(emp.getUserId());                              // user_id field
user.setName(emp.getUser().getName());                    // user.name
user.setEmail(emp.getUser().getEmail());                  // user.email
user.setFbId(emp.getUser().getFbId());                    // user.fb_id
user.setPhoneNumber(emp.getUser().getPhoneNumber());      // user.phone_number
user.setAvatarUrl(emp.getUser().getAvatarUrl());          // user.avatar_url
user.setCreatedAt(now);
user.setUpdatedAt(now);
```

### 6.2 API → PosProfile

```java
PosProfile profile = new PosProfile();
profile.setId(emp.getProfileId());                        // profile_id
profile.setName("Profile " + profileId.substring(0,8));
profile.setShopId(emp.getShopId());                       // shop_id
profile.setCreatedAt(now);
profile.setUpdatedAt(now);
```

### 6.3 API → PosDepartment

```java
PosDepartment dept = new PosDepartment();
dept.setId(emp.getDepartmentId());                       // department_id
dept.setName(emp.getDepartment().getName());              // department.name
dept.setShopId(emp.getShopId());                          // shop_id
dept.setCreatedAt(now);
```

### 6.4 API → PosSaleGroup

```java
PosSaleGroup sg = new PosSaleGroup();
sg.setId(emp.getSale_group().getId());                    // sale_group.id
sg.setName(emp.getSale_group().getName());                 // sale_group.name
sg.setShopId(emp.getShopId());                             // shop_id
```

### 6.5 API → PosShopUser

```java
PosShopUser shopUser = new PosShopUser();
shopUser.setId(emp.getShopUserId());                      // id field
shopUser.setShopId(emp.getShopId());                       // shop_id
shopUser.setUserId(emp.getUserId());                       // user_id
shopUser.setDepartment(department);                        // FK relationship
shopUser.setRole(emp.getRole());                           // role
shopUser.setPermissionInSaleGroup(emp.getPermissionInSaleGroup());
shopUser.setIsAssigned(emp.getIsAssigned());
shopUser.setEnableApi(emp.getEnableApi());
shopUser.setApiKey(emp.getApiKey());
shopUser.setNoteApiKey(emp.getNoteApiKey());
shopUser.setIsApiKey(emp.getIsApiKey());
shopUser.setPendingOrderCount(emp.getPendingOrderCount());
shopUser.setPreferredShop(emp.getPreferredShop());
shopUser.setAppWarehouse(emp.getAppWarehouse());
shopUser.setCreatorId(emp.getCreatorId());
shopUser.setProfileId(emp.getProfileId());
shopUser.setInsertedAt(parseDateTime(emp.getInsertedAt()));
shopUser.setUpdatedAt(now);
```

### 6.6 API → PosSaleGroupMember

```java
PosSaleGroupMember member = new PosSaleGroupMember();
member.setShopUserId(emp.getShopUserId());                // id field
member.setSaleGroupId(emp.getSale_group().getId());       // sale_group.id
member.setPermission(emp.getPermissionInSaleGroup());      // permission_in_sale_group
```

## 7. Error Handling

### 7.1 Retry Logic cho Deadlock

```java
private void saveWithRetry(RetryableOperation op) {
    transactionTemplate.executeWithoutResult(status -> {
        int retries = 0;
        while (retries < 3) {
            try {
                op.execute();
                return;
            } catch (Exception e) {
                if (e.getMessage().contains("Deadlock")) {
                    retries++;
                    log.warn("Deadlock on save attempt {}, retrying...", retries);
                    Thread.sleep(200L * retries);  // Exponential backoff
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    });
}
```

### 7.2 API Error Handling

```java
catch (HttpClientErrorException e) {
    throw new ApiClientException("API returned client error: " + e.getStatusCode(), e);
}
catch (HttpServerErrorException e) {
    throw new ApiClientException("API returned server error: " + e.getStatusCode(), e);
}
catch (ResourceAccessException e) {
    throw new ApiClientException("Failed to connect to API: " + e.getMessage(), e);
}
```

## 8. Response Trả Về

### 8.1 EmployeeSyncResult

```json
{
  "totalFromApi": 150,
  "insertedUsers": 10,
  "updatedUsers": 140,
  "insertedProfiles": 5,
  "updatedProfiles": 145,
  "insertedShopUsers": 10,
  "updatedShopUsers": 140,
  "insertedDepartments": 2,
  "updatedDepartments": 8,
  "insertedSaleGroups": 3,
  "updatedSaleGroups": 7,
  "insertedSaleGroupMembers": 25,
  "skippedCount": 0,
  "message": "Synced 150 employees: 150 users, 150 profiles, 150 shop_users, 10 departments, 10 sale_groups, 25 group_members"
}
```

## 9. File Liên Quan

| File | Mô tả |
|------|-------|
| `DongBoController.java` | Controller xử lý API endpoint |
| `EmployeeSyncService.java` | Service chính xử lý sync |
| `EmployeeApiClient.java` | Gọi POS API |
| `EmployeeApiResponse.java` | DTO nhận response từ API |
| `EmployeeSyncResult.java` | DTO trả về kết quả sync |
| `PosUser.java` | Entity user |
| `PosShopUser.java` | Entity shop user |
| `PosProfile.java` | Entity profile |
| `PosDepartment.java` | Entity phòng ban |
| `PosSaleGroup.java` | Entity nhóm bán |
| `PosSaleGroupMember.java` | Entity thành viên nhóm bán |
| `dongbo.html` | Giao diện đồng bộ |

## 10. Sơ Đồ Sequence

```
┌────────┐          ┌───────────────┐          ┌────────────┐          ┌─────────┐
│ Client │          │ DongBoController│         │EmployeeSync│          │ POS API │
└───┬────┘          └───────┬───────┘          │  Service   │          └────┬────┘
    │  GET /api/employees/sync │                    │                   │
    │ ─────────────────────────►                    │                   │
    │                         │  syncAllEmployees() │                   │
    │                         │ ───────────────────►│                   │
    │                         │                     │                   │
    │                         │                     │  fetchAllUsers()  │
    │                         │                     │ ─────────────────►│
    │                         │                     │ ◄───────────────── │ 200 OK
    │                         │                     │                   │
    │                         │                     │  syncEmployeesBatch()
    │                         │                     │ ────┐             │
    │                         │                     │     │ Phase 1-4   │
    │                         │                     │ ◄───┘             │
    │                         │                     │                   │
    │ ◄────────────────────────│                     │                   │
    │      EmployeeSyncResult │                    │                   │
    │                         │                     │                   │
```
