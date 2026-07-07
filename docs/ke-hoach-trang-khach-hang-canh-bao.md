# Kế hoạch xây dựng chức năng "Khách hàng cảnh báo"

> Trang nội bộ hiển thị các khách hàng cần chú ý (CSKH chăm nhiều nhưng chưa chốt / chưa mua),
> đọc trực tiếp từ DB `pos_db`.

---

## 1. Mục tiêu & phạm vi

Xây một trang (route nội bộ) liệt kê khách hàng thỏa **một trong hai nhóm điều kiện**:

- **Nhóm A** — chăm nhiều nhưng chưa chốt: có **≥ 5 ghi chú còn hiệu lực** nhưng **chưa có đơn chốt** (`orders.status = 1`).
- **Nhóm B** — để lâu chưa lên đơn: **quá 72h** kể từ khi tạo khách (`customers.inserted_at`) nhưng **chưa có đơn thành công**.

Hai nhóm nối bằng `OR`. Kèm bộ lọc để chỉnh ngưỡng (số ghi chú, số giờ, giới hạn dòng).

**Ngoài phạm vi:** không sửa luồng đồng bộ ghi chú (đã hoạt động đúng — xem mục 7).

---

## 2. Các quyết định thiết kế cần chốt trước khi code

Đây là các điểm mơ hồ đã phát hiện; phải chốt để câu truy vấn đúng ý:

| # | Vấn đề | Phương án đề xuất | Cần chốt |
|---|--------|-------------------|----------|
| 1 | **Nguồn dữ liệu** | Query thẳng `pos_db` (có `customer_notes`, `orders`). KHÔNG dùng Lark Bitable vì Lark thiếu trạng thái đơn. | ✅ nên chốt dùng `pos_db` |
| 2 | **"Đơn thành công"** = status nào? | Enum: `NEW(0), PENDING(1), COMPLETED(3), CANCELLED(5), SHIPPING(6)`. `status >= 1` **bao gồm cả CANCELLED(5)** → sai nghĩa. Đề xuất dùng `status IN (1,3,6)`. | ⚠️ chốt tập status |
| 3 | **Nhóm B dễ "nổ" kết quả** | "Mọi KH >72h chưa mua" ≈ toàn bộ DB. Giới hạn nhóm B trong KH **có ≥ 1 ghi chú** (đang trong pipeline CSKH), và/hoặc khung thời gian gần (72h–30 ngày). | ⚠️ chốt điều kiện thu hẹp |
| 4 | **Đếm ghi chú** | Chỉ đếm ghi chú **còn hiệu lực**: `removed_at IS NULL`. | ✅ |
| 5 | **Mốc 72h** | Tính từ `customers.inserted_at`. | ✅ |
| 6 | **Hiển thị số lượng** | Có thể rất nhiều → **phân trang** hoặc `LIMIT` + cảnh báo khi bị cắt. | ⚠️ chọn phân trang hay limit |

---

## 3. Kiến trúc & vị trí file

Đi theo pattern sẵn có của module đọc DB (tham chiếu `report/sale/SaleReportController`), gồm 4 thành phần:

```
src/main/java/mera/mera_v2/
├── customer/DTO/ProblemCustomerView.java        (interface projection)
├── repository/ProblemCustomerRepository.java    (native query)
├── customer/Controller/ProblemCustomerController.java
└── resources/templates/problemCustomers.html    (giao diện)
+ sửa resources/templates/index.html             (thêm link sidebar)
```

Route đề xuất: `GET /khach-hang-canh-bao` (trả template), tham số filter: `minNotes`, `hours`, `limit`.

---

## 4. Các bước triển khai

### Bước 1 — Repository + Projection (truy vấn)
- Tạo interface projection `ProblemCustomerView` với getter khớp alias cột trả về:
  `customerId, name, phone, insertedAt, orderCount, succeedOrderCount, lastOrderAt, noteCount, lastNoteAt, hasChotOrder, hasSuccessOrder`.
- Tạo `ProblemCustomerRepository` (native query). Khung logic:
  - Bảng chính `customers c`.
  - `LEFT JOIN` sub-query đếm ghi chú (`customer_notes` lọc `removed_at IS NULL`, group theo `customer_id`, lấy thêm `MAX(created_at)` làm "ghi chú gần nhất").
  - `LEFT JOIN` sub-query đếm đơn chốt (`orders WHERE status = 1`).
  - `LEFT JOIN` sub-query đếm đơn thành công (theo tập status đã chốt ở mục 2).
  - `LEFT JOIN` sub-query lấy 1 số điện thoại (`customer_phone_numbers`, ưu tiên `is_primary`).
  - `WHERE (nhóm A) OR (nhóm B)`; tham số hoá `:minNotes`, `:thresholdTime` (tính sẵn `now - hours` ở Java), `:maxRows`.
  - `ORDER BY noteCount DESC, inserted_at ASC` + `LIMIT :maxRows`.

### Bước 2 — Controller
- `@GetMapping("/khach-hang-canh-bao")`, nhận `minNotes` (mặc định 5), `hours` (72), `limit` (1000); validate biên.
- Tính `threshold = now.minusHours(hours)`, gọi repository.
- Với mỗi dòng: xác định thuộc nhóm A / B, ghép chuỗi "lý do cảnh báo", format ngày.
- Đưa vào model: danh sách dòng, tổng, số lượng nhóm A, nhóm B, cờ `capped`, thời điểm tạo.
- Bọc `try/catch` để lỗi query hiển thị thân thiện thay vì stacktrace.

### Bước 3 — Template Thymeleaf
- Trang standalone, dùng font/màu hệ thống (Inter, nền `#f7f7f4`, xanh `#1456f0`).
- Thẻ tổng quan: Tổng KH cảnh báo / Nhóm A / Nhóm B.
- Form filter (minNotes, hours, limit) — submit GET.
- Bảng: STT, tên, SĐT, mã KH, số ghi chú (tô đậm khi ≥ ngưỡng), tổng đơn, đơn thành công, có đơn chốt?, ngày tạo, ghi chú gần nhất, lý do (badge A/B).
- Cảnh báo khi kết quả bị cắt theo `limit`.

### Bước 4 — Điều hướng
- Thêm link "⚠️ KH Cảnh Báo" vào sidebar `index.html` (nhóm "Tra cứu & POS").

### Bước 5 — Hiệu năng
- Đảm bảo index: `customer_notes(customer_id)`, `orders(customer_id, status)`, `customer_phone_numbers(customer_id)`.
- Nếu dữ liệu lớn: chuyển từ `LIMIT` sang **phân trang** (`Pageable` + count query), hoặc cache kết quả trong vài phút.

### Bước 6 — Kiểm thử & nghiệm thu
- Compile (`mvnw compile`) — **cần Java 17+**, không chạy được trên môi trường Java 11.
- Đối chiếu vài KH mẫu: số ghi chú, trạng thái đơn khớp thực tế.
- Kiểm biên: KH 0 ghi chú, KH đã có đơn chốt (không được xuất hiện ở nhóm A), KH vừa tạo <72h (không được ở nhóm B).
- Kiểm tải: đo thời gian query với ngưỡng thực tế; bật `show-sql` để xem kế hoạch thực thi nếu chậm.

---

## 5. Đối chiếu schema (đã xác minh với dump `pos_db`)

| Bảng | Cột dùng |
|------|----------|
| `customers` | `id, name, shop_id, order_count, succeed_order_count, last_order_at, inserted_at` |
| `customer_notes` | `id, customer_id, removed_at, created_at` |
| `customer_phone_numbers` | `customer_id, phone_number, is_primary` |
| `orders` | `id, customer_id, status` |

`OrderStatus`: `NEW(0), PENDING(1), COMPLETED(3), CANCELLED(5), SHIPPING(6)` — theo nghiệp vụ, `status = 1` = đã chốt.

---

## 6. Rủi ro & lưu ý

- **CANCELLED lọt vào "thành công"** nếu dùng `status >= 1` → nên dùng tập status tường minh.
- **Nhóm B quá rộng** nếu không thu hẹp → nặng và ít giá trị.
- **Interface projection + native query**: alias cột phải trùng tên getter (không phân biệt hoa/thường).
- **Spring Boot 4 cần Java 17+**: môi trường build phải đúng phiên bản.

---

## 7. Kết luận kiểm tra luồng đồng bộ ghi chú

Luồng lưu/đồng bộ ghi chú **đã xử lý đầy đủ** — không cần sửa cho chức năng này:

- `CustomerSyncService.persistPage()` upsert ghi chú vào `customer_notes` theo `id`,
  lưu lịch sử sửa vào `customer_note_edit_history`, xử lý soft-delete qua `removed_at`,
  và đếm kết quả (`insertedNotes / updatedNotes / insertedEditHistory / skippedNotes`).
- Không có chỗ "fetch mà không lưu". Ghi chú thiếu `id`/`message` bị skip có đếm.

**Nên kiểm thêm ở mức vận hành (không phải code):**
- Scheduler đồng bộ khách hàng có đang bật và chạy đúng tần suất không.
- Cửa sổ thời gian mỗi lần quét (`startDate/endDate`) có bỏ sót ghi chú mới không.
- Đối chiếu số ghi chú DB vs POS cho vài KH mẫu để xác nhận độ phủ.
