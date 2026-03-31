# 📊 MeraGroup — mera_v2

Backend tích hợp Lark Bitable, webhook Pancake POS và báo cáo Sale cho MeraGroup.  
Xây dựng trên **Spring Boot 4.0.5**, gộp từ 3 project cũ: `meraGroup`, `getUserToken`, `orders`.

---

## 🏗️ Kiến trúc tổng quan

```
mera_v2/
├── lark/
│   ├── auth/          # OAuth2 Lark, xác thực, quản lý token
│   ├── token/         # Token storage, auto-refresh scheduler
│   └── wiki/          # Lấy thông tin Wiki, Base ID
├── lark/webhook/
│   ├── config/        # Cấu hình Lark Base, webhook
│   ├── dto/           # DTO cho Bitable records, requests
│   ├── scheduler/     # Dọn record cũ, auto refresh token
│   └── service/       # Bitable service, mapping base-table
├── customer/          # Tìm kiếm khách hàng qua Bitable
├── pos/sync/          # Sync orders từ Pancake POS vào DB
├── report/sale/       # Báo cáo sale từ Lark Bitable
├── entity/            # JPA entities (Order, Customer, ...)
└── repository/        # Spring Data JPA repositories
```

---

## ⚙️ Cấu hình

File: `src/main/resources/application.properties`

| Key | Mô tả |
|-----|-------|
| `server.port` | Port chạy app (mặc định: `8080`) |
| `spring.datasource.url` | MariaDB connection string |
| `lark.app-id` | App ID của Lark App |
| `lark.app-secret` | App Secret của Lark App |
| `lark.redirect-uri` | OAuth2 callback URL (phải đăng ký trong Lark Console) |
| `lark.space-id` | ID của Wiki Space dùng để crawl Base IDs |
| `lark.wiki-token` | Token của Wiki node chứa các Bitable |
| `pos.api.base-url` | Base URL của Pancake POS API |
| `pos.api.shop-id` | Shop ID trên Pancake |
| `pos.api.api-key` | API key Pancake |
| `pancake.webhook.secret` | Secret xác thực webhook từ Pancake |

---

## 🚀 Khởi động

```bash
# Clone repo
git clone https://github.com/longvu1311jun/mera_v2.git
cd mera_v2

# Chạy
./mvnw spring-boot:run

# Hoặc chỉ compile để kiểm tra lỗi
./mvnw clean compile -DskipTests
```

**Yêu cầu:** Java 17+, MariaDB đang chạy và accessible.

---

## 📄 Danh sách trang

### 🏠 `GET /` — Trang chủ / Đăng nhập

**File:** `index.html`

Trang đầu tiên khi truy cập app. Hiển thị nút **"Login với Lark"** để bắt đầu OAuth2 flow.

- Nếu chưa đăng nhập: hiển thị nút login
- Nếu đã đăng nhập: hiển thị thông tin token + thời gian hết hạn

**Flow đăng nhập:**
1. Click "Login với Lark" → redirect đến Lark OAuth consent screen
2. Lark redirect về `/oauth/callback?code=xxx`
3. App đổi `code` lấy `access_token` + `refresh_token`
4. Token được lưu in-memory (global) + session

---

### 🔑 `GET /token` — Quản lý Token Lark *(larkAuthController)*

**File:** `token.html`

Trang xem thông tin chi tiết token Lark sau khi đăng nhập (thuộc `larkAuthController`, prefix `/lark`).

**Hiển thị:**
- User Access Token (masked)
- Refresh Token (masked)
- Tenant Access Token
- App Token, Wiki Token
- Danh sách Bitable tables đã kết nối
- Danh sách Base IDs trong Wiki Space

**Actions:**
- `GET /lark/token/refresh` — Làm mới token thủ công
- `GET /lark/oauth/callback` — Endpoint nhận OAuth callback (của larkAuthController)

---

### ⚙️ `GET /config` — Cấu hình Token & Bitable

**File:** `config.html`

Trang xem token hiện tại và quản lý cấu hình webhook xử lý theo trạng thái đơn hàng.

**Chức năng:**
- Xem access token, refresh token, thời gian hết hạn
- Xem danh sách User Configs (nhân viên CSKH và Base/Table tương ứng)
- Xem danh sách Sale Tables
- Toggle bật/tắt xử lý webhook theo **Status = 1** (Đã xác nhận)
- Toggle bật/tắt xử lý webhook theo **Status = 6**
- `POST /config/refresh` — Reload dữ liệu từ Lark

**API nội bộ:**
- `POST /api/config/status1?enabled=true|false`
- `POST /api/config/status6?enabled=true|false`

---

### 📈 `GET /saleReport` — Báo Cáo Sale (SALE 1)

**File:** `saleReport.html`

Báo cáo tổng hợp hiệu quả sale theo từng **tư vấn viên**, dữ liệu lấy từ Lark Bitable.

**Các cột:**
| Cột | Mô tả |
|-----|-------|
| Tư vấn viên | Tên nhân viên (= tên Bitable) |
| Nhu cầu | Số khách có nhu cầu |
| Trùng | Số khách trùng |
| Rác | Số khách rác |
| Không tương tác | Số khách không phản hồi |
| Chốt nóng | Đơn chốt trong tháng |
| Chốt cũ | Đơn chốt từ tháng trước |
| Đơn hủy | Đơn bị hủy |
| Tổng mess | Tổng tất cả loại |
| Tổng đơn | Chốt nóng + Chốt cũ |
| Đơn/mess nhu cầu | Tỉ lệ chuyển đổi từ lead có nhu cầu |
| Đơn/mess tổng | Tỉ lệ chuyển đổi tổng |
| Tỉ lệ hủy | % đơn bị hủy (xanh < 10%, đỏ ≥ 10%) |

**Actions:**
- Lọc theo **Tháng này / Tháng trước**
- Tìm kiếm theo tên tư vấn viên
- Sort theo cột
- **Xuất Excel** (`.xlsx`)
- `POST /saleReport/refresh` — Làm mới dữ liệu từ Lark

---

### 📊 `GET /stats` — Thống Kê Lịch Hẹn (SALE 2)

**File:** `stats.html`

Báo cáo thống kê lịch hẹn CSKH theo từng nhân viên.

**Các cột:**
| Cột | Mô tả |
|-----|-------|
| Tên Nhân Viên | Tên nhân viên CSKH |
| Tổng Khách | Số khách được phân công |
| Tổng Lịch | Số lịch hẹn đã tạo |
| Hoàn Thành Muộn | Lịch hoàn thành nhưng trễ deadline |
| Hoàn Thành | Lịch hoàn thành đúng hạn |
| Quá Hạn | Lịch chưa xong đã qua deadline |

**Summary cards:** Tổng nhân viên, tổng khách, tổng lịch, hoàn thành  
**Actions:**
- Lọc theo **Tháng này / Tháng trước**
- Tìm kiếm theo tên nhân viên
- Sort theo cột
- **Xuất Excel** (`/stats/export`)
- `POST /stats/refresh` — Làm mới dữ liệu

---

### 🔍 `GET /searchCustomer` — Tìm Khách Hàng

**File:** `searchCustomer.html`

Trang tìm kiếm thông tin khách hàng theo số điện thoại trong Lark Bitable.

**Chức năng:**
- Nhập số điện thoại → tìm kiếm trên Bitable
- Hiển thị bản ghi khách hàng tìm được

---

### 📋 `GET /searchInfo` — Tìm Thông Tin Chi Tiết

**File:** `searchInfo.html`

Tìm kiếm thông tin chi tiết khách hàng, hỗ trợ xem lịch sử và thông tin đầy đủ từ Bitable.

---

### 📦 `GET /pancake-orders` — Xem Orders Pancake

**File:** `pancake-orders.html`

Xem danh sách orders từ Pancake POS API, hỗ trợ filter và xem chi tiết.

---

### 🧾 `GET /order-detail` — Chi Tiết Đơn Hàng

**File:** `order-detail.html`

Xem chi tiết một đơn hàng cụ thể từ Pancake POS.

---

### 🧪 `GET /test-api.html` — Test API Sync Orders *(static)*

**File:** `src/main/resources/static/test-api.html`

Giao diện để gọi thủ công API sync orders từ Pancake vào Database.

**Chức năng:**
- Chọn khoảng ngày (Start Date / End Date)
- Lọc theo Status đơn hàng (Mới tạo / Chờ xác nhận / Hoàn thành / Hủy / ...)
- Chọn `updateStatus`: `inserted_at` hoặc `updated_at`
- Chọn `page_size` và `start_page`
- Hiển thị kết quả: tổng orders, số inserted/updated cho customers, orders, order items

**API gọi:**
```
POST /api/orders/sync
Body: { startDate, endDate, pageSize, updateStatus, status?, startPage? }
```

---

### 📝 `GET /logs` — Xem Logs

**File:** `logs.html`

Xem logs của hệ thống theo thời gian thực (in-memory log storage).

---

### 📊 `GET /report` — Báo Cáo Tổng Hợp

**File:** `report.html`

Trang báo cáo tổng hợp (đơn giản, dùng nội bộ).

---

### 🛠️ `GET /demo` — Demo / Thử Nghiệm

**File:** `demo.html`

Trang demo các chức năng, dùng trong quá trình phát triển.

---

## 🔗 API Endpoints chính

| Method | URL | Mô tả |
|--------|-----|-------|
| `GET` | `/` | Trang chủ / Đăng nhập |
| `GET` | `/oauth/callback` | OAuth2 callback từ Lark |
| `GET` | `/config` | Cấu hình token & webhook |
| `POST` | `/config/refresh` | Reload dữ liệu config |
| `GET` | `/saleReport` | Báo cáo Sale 1 |
| `POST` | `/saleReport/refresh` | Làm mới Sale Report |
| `GET` | `/saleReport/export` | Xuất Sale Report Excel |
| `GET` | `/stats` | Báo cáo Lịch Hẹn |
| `POST` | `/stats/refresh` | Làm mới Stats |
| `GET` | `/stats/export` | Xuất Stats Excel |
| `POST` | `/api/orders/sync` | Sync orders Pancake → DB |
| `GET` | `/api/orders/sync/health` | Health check |
| `POST` | `/webhook/pancake` | Nhận webhook từ Pancake POS |
| `GET` | `/lark` | Lark login page (legacy) |
| `GET` | `/lark/token` | Xem token (larkAuthController) |
| `GET` | `/lark/token/refresh` | Làm mới token thủ công |

---

## 🔄 Webhook Flow (Pancake → Lark Bitable)

```
Pancake POS
    ↓ POST /webhook/pancake
LarkWebhookController
    ↓ Xác thực HMAC signature
    ↓ Parse PosOrderWebhook
    ↓ Kiểm tra Status (1 = Đã xác nhận)
PosToBitableMapper
    ↓ Map fields POS → Bitable record
BaseTableMappingService
    ↓ Tìm Base ID + Table ID theo tên CSKH (match SĐT)
LarkBitableService
    ↓ POST /bitable/v1/apps/{baseId}/tables/{tableId}/records
Lark Bitable ✅
```

> **Lưu ý:** Webhook hiện tại chỉ ghi bản ghi vào Lark Bitable.  
> Lưu vào Database sẽ được triển khai theo yêu cầu riêng qua `/test-api.html`.

---

## 🔐 Lưu ý bảo mật

- Token Lark được lưu **in-memory** và trong **HTTP Session**. Nếu restart server, cần đăng nhập lại.
- `lark.app-secret` và `pos.api.api-key` không được commit lên repo public.
- Webhook được xác thực bằng HMAC signature với `pancake.webhook.secret`.

---

## 📦 Tech Stack

| Thành phần | Công nghệ |
|-----------|-----------|
| Backend | Spring Boot 4.0.5 |
| Template | Thymeleaf |
| Database | MariaDB + Spring Data JPA |
| Build | Maven Wrapper |
| Java | 17 |
| HTTP Client | RestTemplate |
| Excel Export | Apache POI |
| Lark SDK | REST API trực tiếp |
