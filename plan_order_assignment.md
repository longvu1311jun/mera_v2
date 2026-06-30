# Plan: Chức Năng Tự Động Phân Công CSKH Cho Đơn Hàng Status = 1

## 1. Tổng Quan

### Mục tiêu
- Nhân viên điểm danh trong ngày sẽ được chia đơn hàng theo thứ tự ngày vào làm (hireDate sớm nhất trước)
- Gọi POS API để update `assigning_care_id` cho đơn hàng
- Gửi thông báo Lark cho CSKH khi được phân công
- Giữ nguyên luồng tag "Đồng bộ DATA" để tạo bản ghi thủ công khi cần

### Luồng Nghiệp Vụ Mới

```mermaid
flowchart TD
    A[Webhook status=1] --> B{Có Tag<br/>Đồng bộ DATA?}
    B -->|Có| C[Luồng Tag<br/>Tạo bản ghi trực tiếp]
    B -->|Không| D{Auto Assign<br/>CSKH enabled?}
    D -->|Có| E[Luồng Auto Assign]
    D -->|Không| F[Skip]

    E --> E1{Lấy DS nhân viên<br/>điểm danh trong ngày}
    E1 --> E2[Sắp xếp theo hireDate<br/>tăng dần]
    E2 --> E3[Lấy CSKH tiếp theo<br/>trong danh sách vòng tròn]
    E3 --> E4[Gọi POS API<br/>Update assigning_care_id]
    E4 --> E5[Tạo bản ghi Lark<br/>Bitable]
    E5 --> E6[Gửi tin nhắn Lark<br/>thông báo CSKH]
    E6 --> E7[Lưu OrderAssignment]

    C --> G[Tạo Bản ghi Lark]
    E7 --> H[Return "ok"]
    G --> H
```

---

## 2. Những Gì Đã Có (Tái Sử Dụng)

### 2.1 Entities & Database

| Entity | File | Mục đích |
|--------|------|----------|
| `EmployeeMapping` | `entity/EmployeeMapping.java` | Map Lark Employee với POS User, có `hireDate` |
| `LarkAttendancePunch` | `entity/LarkAttendancePunch.java` | Lưu punch checkin (punchType=1) |
| `LarkAttendanceDay` | `entity/LarkAttendanceDay.java` | Tổng hợp attendance theo ngày |
| `PosOrder` | `entity/Order.java` | Đơn hàng, có `assigningCareId` |
| `LarkEmployee` | `entity/LarkEmployee.java` | Lark user với `openId`, `mobile` |

### 2.2 Repositories

| Repository | Method hữu ích |
|------------|-----------------|
| `LarkAttendancePunchRepository` | `findDistinctEmployeeIdsCheckedIn(date)` - Lấy employee đã checkin |
| `EmployeeMappingRepository` | `findAll()` - Lấy tất cả mapping để lọc |
| `OrderRepository` | `findById()`, `save()` - CRUD order |

### 2.3 Services

| Service | Method | Mục đích |
|---------|--------|----------|
| `LarkImService` | `sendToCskh(openId, tenKhach, dienThoai, diaChi, tuoi)` | Gửi tin nhắn Lark |
| `LarkSendMessage` | `resolveCskhOpenIdByPhone(phone)` | Lấy openId từ SĐT |
| `PosToBitableMapper` | `mapToBitableFields()` | Map order sang Bitable fields |
| `LarkBitableService` | `createRecord()`, `checkPhoneExistsWithFilter()` | Tạo record trong Bitable |
| `CskhBaseMappingService` | `findMappingResultByPhone()` | Lấy Base/Table ID |

---

## 3. Những Gì Cần Làm Mới

### 3.1 Database - Tạo Bảng Mới

**Bảng `order_assignments`** - Theo dõi lịch sử phân công

```sql
CREATE TABLE order_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    employee_mapping_id BIGINT NOT NULL,
    lark_employee_id VARCHAR(64) NOT NULL,
    lark_employee_name VARCHAR(255),
    customer_name VARCHAR(255),
    customer_phone VARCHAR(32),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_lark_employee_id (lark_employee_id),
    INDEX idx_assigned_at (assigned_at)
);
```

**Entity:** `OrderAssignment.java`

---

### 3.2 Database - Thêm Trường Tracking

**Bảng `employee_mappings`** - Thêm trường đếm:

```sql
ALTER TABLE employee_mappings
ADD COLUMN today_assignment_count INT DEFAULT 0,
ADD COLUMN last_assignment_date DATE;
```

**Entity:** Cập nhật `EmployeeMapping.java`

---

### 3.3 POS API Client

**File:** `src/main/java/mera/mera_v2/pos/api/PosOrderApiClient.java`

```java
@Service
@Slf4j
public class PosOrderApiClient {

    @Value("${pos.api.base-url}")
    private String baseUrl;

    @Value("${pos.api.shop-id}")
    private String shopId;

    @Value("${pos.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    /**
     * Gọi POS API để update assigning_care_id cho đơn hàng
     */
    public void updateAssigningCare(Long orderId, String assigningCareId) {
        String url = String.format("%s/api/v1/shops/%s/orders/%s?api_key=%s",
                baseUrl, shopId, orderId, apiKey);

        Map<String, Object> body = Map.of(
                "assigning_care_id", assigningCareId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to update order: " + response.getStatusCode());
        }
    }
}
```

---

### 3.4 Service Mới: Assignment Service

**File:** `src/main/java/mera/mera_v2/pos/assignment/OrderAssignmentService.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderAssignmentService {

    private final LarkAttendancePunchRepository attendancePunchRepository;
    private final EmployeeMappingRepository employeeMappingRepository;
    private final OrderAssignmentRepository orderAssignmentRepository;
    private final PosOrderApiClient posOrderApiClient;
    private final LarkEmployeeRepository larkEmployeeRepository;

    /**
     * Lấy danh sách nhân viên điểm danh trong ngày, sắp xếp theo hireDate tăng dần
     */
    public List<EmployeeMapping> getTodayCheckedInEmployeesSorted() {
        LocalDate today = LocalDate.now();
        List<String> checkedInEmployeeIds = attendancePunchRepository
                .findDistinctEmployeeIdsCheckedIn(today);

        if (checkedInEmployeeIds.isEmpty()) {
            return Collections.emptyList();
        }

        return employeeMappingRepository.findAll().stream()
                .filter(em -> checkedInEmployeeIds.contains(em.getLarkEmployeeId()))
                .filter(em -> em.getHireDate() != null)
                .sorted(Comparator.comparing(EmployeeMapping::getHireDate))
                .collect(Collectors.toList());
    }

    /**
     * Phân công đơn hàng cho CSKH tiếp theo (vòng tròn)
     * - Chọn nhân viên có hireDate sớm nhất và chưa đạt giới hạn
     * - Nếu hết nhân viên, quay vòng từ đầu
     */
    public AssignmentResult assignOrderToNextCskh(Long orderId, PosOrderWebhook orderWebhook) {
        List<EmployeeMapping> employees = getTodayCheckedInEmployeesSorted();
        if (employees.isEmpty()) {
            throw new RuntimeException("Khong co nhan vien diem danh trong ngay");
        }

        // Reset count nếu cần
        resetDailyCountIfNeeded(employees);

        // Tìm nhân viên tiếp theo có số assignment ít nhất
        EmployeeMapping selected = employees.stream()
                .min(Comparator.comparingInt(EmployeeMapping::getAssignmentCount))
                .orElse(employees.get(0));

        // Update POS API
        posOrderApiClient.updateAssigningCare(orderId, selected.getPosUserId());

        // Update local count
        selected.incrementAssignmentCount();
        employeeMappingRepository.save(selected);

        // Lưu assignment history
        OrderAssignment assignment = new OrderAssignment();
        assignment.setOrderId(orderId);
        assignment.setEmployeeMappingId(selected.getId());
        assignment.setLarkEmployeeId(selected.getLarkEmployeeId());
        assignment.setLarkEmployeeName(selected.getLarkEmployeeName());
        assignment.setCustomerName(posToBitableMapper.getTenKhach(orderWebhook));
        assignment.setCustomerPhone(posToBitableMapper.getDienThoai(orderWebhook));
        orderAssignmentRepository.save(assignment);

        // Get Lark openId
        String cskhOpenId = larkEmployeeRepository.findById(selected.getLarkEmployeeId())
                .map(LarkEmployee::getOpenId)
                .orElse(null);

        return new AssignmentResult(selected, cskhOpenId);
    }

    /**
     * Reset count khi đổi ngày
     */
    private void resetDailyCountIfNeeded(List<EmployeeMapping> employees) {
        LocalDate today = LocalDate.now();
        for (EmployeeMapping em : employees) {
            if (em.getLastAssignmentDate() == null || !em.getLastAssignmentDate().equals(today)) {
                em.resetAssignmentCount();
                em.setLastAssignmentDate(today);
                employeeMappingRepository.save(em);
            }
        }
    }
}
```

---

### 3.5 API Controller Mới

**File:** `src/main/java/mera/mera_v2/pos/assignment/OrderAssignmentController.java`

| Endpoint | Method | Mô tả |
|----------|--------|--------|
| `GET /api/order-assignment/today` | GET | Danh sách phân công trong ngày |
| `GET /api/order-assignment/stats` | GET | Thống kê phân công |
| `GET /api/order-assignment/employees` | GET | DS nhân viên điểm danh |
| `GET /assignment-monitoring` | GET | Page HTML theo dõi |

---

### 3.6 HTML Page Mới

**File:** `src/main/resources/templates/order-assignment.html`

Trang theo dõi với các cột:
- Tên nhân viên
- Giờ điểm danh (lấy từ `LarkAttendancePunch`)
- Ngày vào làm (hireDate từ `EmployeeMapping`)
- SĐT khách hàng đã phân công
- Tên khách hàng
- Thời gian phân công

---

### 3.7 Chỉnh Sửa LarkWebhookController

**File:** `src/main/java/mera/mera_v2/lark/webhook/LarkWebhookController.java`

**Thay đổi method `processStatusLogic()` (dòng 218-226):**

```java
private void processStatusLogic(Integer status, JsonNode webhookData, PosOrderWebhook orderWebhook) throws Exception {
    if (status == 1) {
        boolean hasTag = hasDongBoDataTag(root);

        if (hasTag) {
            // Luồng Tag: Tạo bản ghi trực tiếp (giữ nguyên)
            log.info("Status = 1 with tag 'Đồng bộ DATA': Tao ban ghi truc tiep");
            if (autoCreateRecord) {
                createBitableRecord(webhookData);
            }
        } else if (autoAssignCskh) {
            // Luồng Auto Assign: Phân công CSKH
            log.info("Status = 1: Phan cong CSKH tu dong");
            orderAssignmentService.resetDailyCountIfNeeded();

            AssignmentResult result = orderAssignmentService.assignOrderToNextCskh(
                    orderWebhook.getId(), orderWebhook);

            createBitableRecord(webhookData);

            sendAssignmentNotification(result, orderWebhook);
        }
    }
    // Status = 6 giữ nguyên xử lý delete/update
}
```

---

## 4. Cấu Hình Cần Thêm

**File:** `src/main/resources/application.yml`

```yaml
lark:
  bitable:
    auto-create: true
    auto-assign-cskh: true
    assignment-notify: true

pos:
  api:
    base-url: https://pos.pages.fm
    shop-id: ${POS_SHOP_ID:}
    key: ${POS_API_KEY:}
```

---

## 5. Chi Tiết Từng Bước Triển Khai

### Bước 1: Database
- [ ] Tạo bảng `order_assignments`
- [ ] Thêm columns vào `employee_mappings`
- [ ] Tạo entity `OrderAssignment.java`
- [ ] Cập nhật entity `EmployeeMapping.java`

### Bước 2: Repository
- [ ] `OrderAssignmentRepository.java`

### Bước 3: POS API Client
- [ ] `PosOrderApiClient.java`

### Bước 4: Service
- [ ] `OrderAssignmentService.java`

### Bước 5: Controller & API
- [ ] `OrderAssignmentController.java`

### Bước 6: HTML Page
- [ ] `order-assignment.html`

### Bước 7: Chỉnh Sửa Webhook
- [ ] Cập nhật `processStatusLogic()` trong `LarkWebhookController`
- [ ] Thêm method `sendAssignmentNotification()`

---

## 6. Danh Sách Files

### Tạo mới:
| File | Mô tả |
|------|-------|
| `entity/OrderAssignment.java` | Entity cho bảng order_assignments |
| `repository/OrderAssignmentRepository.java` | Repository |
| `pos/api/PosOrderApiClient.java` | Gọi POS API update đơn hàng |
| `pos/assignment/OrderAssignmentService.java` | Service xử lý phân công |
| `pos/assignment/OrderAssignmentController.java` | Controller & API |
| `templates/order-assignment.html` | Trang theo dõi |

### Sửa đổi:
| File | Thay đổi |
|------|----------|
| `entity/EmployeeMapping.java` | Thêm columns `today_assignment_count`, `last_assignment_date` |
| `lark/webhook/LarkWebhookController.java` | Chỉnh sửa processStatusLogic, thêm sendAssignmentNotification |
| `application.yml` | Thêm config `pos.api.*`, `lark.bitable.auto-assign-cskh` |

---

## 7. SQL Scripts

```sql
-- 1. Tạo bảng order_assignments
CREATE TABLE order_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    employee_mapping_id BIGINT NOT NULL,
    lark_employee_id VARCHAR(64) NOT NULL,
    lark_employee_name VARCHAR(255),
    customer_name VARCHAR(255),
    customer_phone VARCHAR(32),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_lark_employee_id (lark_employee_id),
    INDEX idx_assigned_at (assigned_at)
);

-- 2. Thêm columns vào employee_mappings
ALTER TABLE employee_mappings
ADD COLUMN today_assignment_count INT DEFAULT 0,
ADD COLUMN last_assignment_date DATE;
```
