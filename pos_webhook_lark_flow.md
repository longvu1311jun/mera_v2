# Luồng Xử Lý POS Webhook - Tạo Bản Ghi Lark

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Sơ đồ luồng dữ liệu](#2-sơ-đồ-luồng-dữ-liệu)
3. [Webhook Endpoint](#3-webhook-endpoint)
4. [Các bước xử lý chính](#4-các-bước-xử-lý-chính)
5. [Tạo Bản Ghi Bitable](#5-tạo-bản-ghi-bitable)
6. [Schedule Follow-up](#6-schedule-follow-up)
7. [Error Handling](#7-error-handling)
8. [Cấu trúc dữ liệu Webhook](#8-cấu-trúc-dữ-liệu-webhook)
9. [File Reference](#9-file-reference)

---

## 1. Tổng quan

Khi POS (Pancake) nhận được đơn hàng mới, hệ thống sẽ gửi webhook `POST /api/lark/orders` đến ứng dụng Mera V2. Ứng dụng sẽ:

1. Lưu dữ liệu vào database cục bộ
2. Kiểm tra thay đổi tài khoản và gửi thông báo
3. Xử lý theo status của đơn hàng
4. Kiểm tra tags "Đồng bộ DATA" để tạo bản ghi trong Lark Bitable

---

## 2. Sơ đồ luồng dữ liệu

```mermaid
flowchart TD
    A[POS System] -->|POST /api/lark/orders| B[LarkWebhookController]
    
    B --> C{Validate Secret}
    C -->|Invalid| D[Return 401]
    C -->|Valid| E[Parse JSON]
    
    E --> F[WebhookPersistenceService]
    F --> F1[Save Order]
    F --> F2[Save Customer]
    F --> F3[Save Items]
    F --> F4[Save Payments]
    F --> F5[Save Histories]
    
    F --> G{Check Account History}
    G -->|Changed| H[Send Lark Message]
    
    H --> I{Extract Status}
    I -->|Status = 1| J[processStatusLogic]
    I -->|Other| K[Check WebhookConfig]
    
    J --> L{Has Tag 32 or "Đồng bộ DATA"?}
    K --> L
    
    L -->|Yes| M[createBitableRecord]
    L -->|No| N[Skip Bitable Creation]
    
    M --> M1[Extract CSKH from webhook]
    M1 --> M2[Find Base ID / Table ID from mapping]
    M2 --> M3{Check Phone Duplicate}
    M3 -->|Exists| N
    M3 -->|Not Exists| M4[Create Bitable Record]
    
    M4 --> M5[Schedule Follow-up]
    M5 --> O[Return "ok"]
    
    D --> P[Return "unauthorized"]
    N --> O
    O --> Q[End]
```

---

## 3. Webhook Endpoint

**File:** `src/main/java/mera/mera_v2/lark/webhook/LarkWebhookController.java`

```java
@PostMapping("/orders")
public ResponseEntity<String> onOrderWebhook(
        @RequestHeader(value = "X-Pancake-Secret", required = false) String secret,
        @RequestBody String rawBody
) {
    // Validate secret
    if (expectedSecret != null && !expectedSecret.isBlank()
            && !expectedSecret.equals(secret)) {
        log.error("Invalid X-Pancake-Secret");
        return ResponseEntity.status(401).body("unauthorized");
    }
    
    // Parse webhook data
    JsonNode root = mapper.readTree(rawBody);
    PosOrderWebhook orderWebhook = mapper.treeToValue(root, PosOrderWebhook.class);
    
    // ... process steps ...
    
    return ResponseEntity.ok("ok");
}
```

### Authentication

- Header `X-Pancake-Secret` được so sánh với config `pancake.webhook.secret`
- Nếu không khớp, trả về HTTP 401

---

## 4. Các bước xử lý chính

### 4.1 Lưu data vào Database

**Service:** `WebhookPersistenceService`

```java
WebhookPersistenceService.PersistenceResult dbResult = 
        webhookPersistenceService.saveFromWebhook(root);

// Result contains:
- isOrderSaved() / isOrderUpdated()
- isCustomerSaved() / isCustomerUpdated()
- getItemsSaved()
- getPaymentsSaved()
- getHistoriesSaved()
```

**Entities được tạo:**
- `PosOrder`
- `PosCustomer`
- `PosOrderItem`
- `PosOrderPayment`
- `PosOrderHistory`

### 4.2 Check lịch sử Account

Kiểm tra xem tài khoản có thay đổi không:

```java
if (orderWebhook.hasAccountChanged()) {
    log.info("Account da thay doi - gui thong bao");
    sendAccountChangeNotification(orderWebhook);
}
```

Gửi tin nhắn Lark IM đến user được cấu hình:
- URL: `https://open.larksuite.com/open-apis/im/v1/messages?receive_id_type=open_id`
- Nội dung: "Đơn hàng đã thay đổi nguồn đơn: {orderId}"

### 4.3 Check Status

```java
private void processStatusLogic(Integer status, JsonNode webhookData, PosOrderWebhook orderWebhook) throws Exception {
    if (status == 1) {
        log.info("Status = 1: Tao ban ghi moi");
        if (autoCreateRecord) {
            createBitableRecord(webhookData);
        }
    }
    // Status = 6 da duoc bo xu ly
}
```

### 4.4 Check Tags "Đồng bộ DATA"

```java
private boolean hasDongBoDataTag(JsonNode root) {
    JsonNode tagsNode = root.get("tags");
    // ... check for tag ID = 32 or name = "Đồng bộ DATA"
    for (JsonNode tagNode : tagsNode) {
        if (idNode.asInt() == 32) return true;
        if ("Đồng bộ DATA".equals(nameNode.asText())) return true;
    }
    return false;
}
```

---

## 5. Tạo Bản Ghi Bitable

### 5.1 Extract CSKH từ Webhook

```java
PosOrderWebhook.AssigningSeller cskh = posToBitableMapper.getAssigningCare(orderWebhook);
String cskhName = (cskh != null && cskh.getName() != null) ? cskh.getName().trim() : null;
```

### 5.2 Tìm Base ID / Table ID từ Mapping

```java
// Trich xuat phone tu ten CSKH
// Ví dụ: "Hà Quang Vượng Sale 2 NT 0968420624" -> "0968420624"
String cskhPhone = extractPhoneFromName(cskhName);

CskhBaseMappingService.CskhMappingResult result = 
    cskhBaseMappingService.findMappingResultByPhone(cskhPhone);

// Get mapping config
appToken = result.getBaseId();           // Lark App Token
targetTableId = result.getKhachHangTableId();  // Table ID cho bảng Khách Hàng
viewId = result.getViewId();             // View ID để filter
```

### 5.3 Kiểm tra trùng lặp theo SĐT

```java
String phoneNumber = posToBitableMapper.getDienThoai(orderWebhook);

boolean phoneExists = bitableService.checkPhoneExistsWithFilter(
    appToken, 
    targetTableId, 
    userAccessToken, 
    phoneNumber,
    searchViewId
);

if (phoneExists) {
    log.warn("Phone number '{}' already exists - skipping", phoneNumber);
    return; // Không tạo bản ghi trùng
}
```

### 5.4 Map sang Bitable Fields

**Service:** `PosToBitableMapper`

```java
Map<String, Object> fields = posToBitableMapper.mapToBitableFields(orderWebhook);

// Fields bao gồm:
// - Tên khách hàng
// - Điện thoại
// - Địa chỉ
// - Tỉnh/Thành phố
// - Tên Liệu Trình (mặc định: "Liệu trình 1")
// - ... các field khác
```

### 5.5 Tạo Record trong Lark

```java
BitableRecordResponse response = bitableService.createRecord(
    appToken,      // Base App Token
    targetTableId,  // Table ID
    userAccessToken, // Lark Access Token
    fields         // Dữ liệu fields
);

if (response.isSuccess() && response.getData() != null) {
    String recordId = response.getData().getRecord().getRecordId();
    log.info("Created Bitable record: recordId={}", recordId);
}
```

---

## 6. Schedule Follow-up

Sau khi tạo bản ghi thành công, hệ thống lưu thông tin vào bảng `pending_followup_notifications` để scheduler xử lý sau 30 phút.

```java
private void scheduleFollowupIfPossible(
    String phoneNumber,
    String baseId,
    String tableId,
    String viewId,
    String createdRecordId,
    PosOrderWebhook orderWebhook
) {
    PendingFollowupNotification pending = new PendingFollowupNotification();
    pending.setPhoneNumber(phoneNumber);
    pending.setBaseId(baseId);
    pending.setTableId(tableId);
    pending.setViewId(viewId);
    pending.setCreatedRecordId(createdRecordId);
    pending.setCustomerName(posToBitableMapper.getTenKhach(orderWebhook));
    
    // Scheduler sẽ chạy sau 30 phút
    pending.setScheduledAt(LocalDateTime.now().plusMinutes(30));
    pending.setProcessed(false);
    
    pendingFollowupNotificationRepository.save(pending);
}
```

---

## 7. Error Handling

Mỗi bước xử lý được wrap trong try-catch riêng biệt để đảm bảo một lỗi không ảnh hưởng đến các bước khác:

```java
// ============ LUU DATA VAO DATABASE ============
try {
    // ...
} catch (Exception e) {
    log.error("Loi khi luu data vao DB: {}", e.getMessage(), e);
}

// ============ CHECK LICH SU ACCOUNT ============
try {
    // ...
} catch (Exception e) {
    log.error("Loi khi check lich su account: {}", e.getMessage(), e);
}

// ============ CHECK STATUS ============
try {
    // ...
} catch (Exception e) {
    log.error("Loi khi xu ly status: {}", e.getMessage(), e);
}

// ============ CHECK TAGS ============
try {
    // ...
} catch (Exception e) {
    log.error("Loi khi xu ly tags: {}", e.getMessage(), e);
}
```

---

## 8. Cấu trúc dữ liệu Webhook

**File:** `src/main/java/mera/mera_v2/lark/webhook/dto/PosOrderWebhook.java`

### 8.1 Root Level Fields

```java
@JsonProperty("id")           private Long id;
@JsonProperty("status")       private Integer status;
@JsonProperty("customer")     private CustomerInfo customer;
@JsonProperty("shipping_address") private ShippingAddress shippingAddress;
@JsonProperty("assigning_care")   private AssigningSeller assigningCare;
@JsonProperty("tags")          private List<Tag> tags;
@JsonProperty("histories")     private List<HistoryItem> histories;
```

### 8.2 Nested Data

```java
// Wrapper
@JsonProperty("data")
private OrderData data;
```

### 8.3 Customer Info

```java
public static class CustomerInfo {
    @JsonProperty("id")          private Long id;
    @JsonProperty("name")       private String name;
    @JsonProperty("phone")       private String phone;
    @JsonProperty("email")       private String email;
    @JsonProperty("address")     private String address;
    @JsonProperty("total_spent") private Double totalSpent;
    @JsonProperty("total_orders") private Integer totalOrders;
}
```

### 8.4 Shipping Address

```java
public static class ShippingAddress {
    @JsonProperty("full_name")   private String fullName;
    @JsonProperty("phone")        private String phone;
    @JsonProperty("address_1")   private String address1;
    @JsonProperty("address_2")   private String address2;
    @JsonProperty("city")         private String city;
    @JsonProperty("city_name")   private String cityName;
    @JsonProperty("district")     private String district;
    @JsonProperty("district_name") private String districtName;
    @JsonProperty("ward")         private String ward;
    @JsonProperty("ward_name")   private String wardName;
}
```

### 8.5 Assigning Seller (CSKH)

```java
public static class AssigningSeller {
    @JsonProperty("id")     private Long id;
    @JsonProperty("name")   private String name;  // Ví dụ: "Hà Quang Vượng Sale 2 NT 0968420624"
    @JsonProperty("email")  private String email;
    @JsonProperty("phone")   private String phone;
}
```

### 8.6 Tag

```java
public static class Tag {
    @JsonProperty("id")   private Integer id;   // Tag ID = 32 là "Đồng bộ DATA"
    @JsonProperty("name") private String name;
}
```

### 8.7 History Item

```java
public static class HistoryItem {
    @JsonProperty("id")          private Long id;
    @JsonProperty("created_at") private String createdAt;
    @JsonProperty("account")     private AccountChange account;
    
    public HistoryItem getLatestHistory() {
        // Trả về history item mới nhất
    }
    
    public boolean hasAccountChanged() {
        // Kiểm tra account có thay đổi không
    }
}
```

---

## 9. File Reference

| File | Mô tả |
|------|-------|
| `lark/webhook/LarkWebhookController.java` | Controller chính xử lý webhook |
| `lark/webhook/dto/PosOrderWebhook.java` | DTO cho dữ liệu webhook |
| `lark/webhook/service/WebhookPersistenceService.java` | Service lưu data vào DB |
| `lark/webhook/service/PosToBitableMapper.java` | Mapper POS data sang Bitable fields |
| `lark/webhook/service/LarkBitableService.java` | Service gọi Lark Bitable API |
| `cskh/mapping/CskhBaseMappingService.java` | Service tìm mapping CSKH -> Base/Table |
| `entity/PendingFollowupNotification.java` | Entity cho bảng pending notifications |

---

## 10. Cấu hình

### 10.1 Properties

```yaml
# application.yml
pancake:
  webhook:
    secret: ${PANCAKE_WEBHOOK_SECRET:}

lark:
  bitable:
    app-token: ${LARK_BITALBE_APP_TOKEN:}
    table-id: ${LARK_BITALBE_TABLE_ID:}
    auto-create: true
```

### 10.2 Database Tables

- `lark_bitable_config` - Cấu hình Base ID, Table ID cho từng CSKH
- `search_config` - Mapping CSKH phone -> Base/Table config (quản lý tại `/admin/cskh-mapping`)
- `pending_followup_notifications` - Bảng chờ xử lý follow-up

---

## 11. Troubleshooting

### 11.1 Log quan trọng

```bash
# Webhook nhận được
data: <raw_json>

# Lưu DB
=== BAT DAU LUU DATA VAO DB ===
=== HOAN THANH LUU DB ===
Order: saved=true, updated=false

# CSKH mapping
Extracted phone from CSKH name 'Hà Quang Vượng Sale 2 NT 0968420624': 0968420624
Found mapping for CSKH 'Hà Quang Vượng Sale 2 NT 0968420624': Base ID=xxx, Table ID=xxx

# Tạo record
Created Bitable record successfully: recordId=xxx
```

### 11.2 Lỗi thường gặp

| Lỗi | Nguyên nhân | Giải pháp |
|------|-------------|------------|
| `Invalid X-Pancake-Secret` | Secret không khớp | Kiểm tra config `pancake.webhook.secret` |
| `No mapping found for CSKH` | Không tìm thấy mapping | Bấm Reload tại `/admin/cskh-mapping` (bảng `search_config`) |
| `Phone number already exists` | Khách hàng đã tồn tại | Bình thường - skip tạo trùng |
| `User access token is not available` | Chưa login Lark | Login tại `/token` trước |

### 11.3 Health Check

```bash
GET /api/lark/health
```

Response:
```json
{
  "status": "UP",
  "endpoint": "/api/lark/orders",
  "autoCreate": true,
  "hasUserToken": true,
  "hasTenantTokenInStorage": true,
  "tenantTokenValidInStorage": true
}
```
