# Báo cáo chức năng "Số Thả Nổi" (Khách hàng cảnh báo)

> Trang nội bộ liệt kê khách hàng cần chú ý, đọc trực tiếp từ DB `pos_db`.
> Route: `GET /khach-hang-canh-bao` — link "⚠️ Số Thả Nổi" trong sidebar nhóm "Tra cứu & POS".
> Cập nhật: 08/07/2026.

---

## 1. Mục tiêu

Giúp đội CSKH nhìn thấy nhanh các khách hàng đang "thả nổi" — được chăm nhưng chưa ra đơn,
để lâu không chuyển đổi, hoặc khách cũ lâu ngày chưa mua lại — để ưu tiên chăm sóc / re-marketing.

## 2. Định nghĩa trạng thái đơn hàng (theo bảng mã POS)

| Nhóm trạng thái | Mã status | Tên |
|---|---|---|
| **Đang xử lý** | 11, 1, 8, 9, 2 | Chờ hàng, Đã xác nhận, Đang đóng hàng, Chờ chuyển hàng, Đã gửi hàng |
| **Đã nhận** | 3, 16 | Đã nhận, Đã thu tiền (đã qua bước nhận) |

**Nguyên tắc loại trừ chung:** khách đang có bất kỳ đơn nào ở nhóm "Đang xử lý" thì KHÔNG lên bảng số nổi.

## 3. Điều kiện 3 nhóm cảnh báo

| Nhóm | Điều kiện | Ý nghĩa |
|---|---|---|
| **A** | ≥ 5 ghi chú còn hiệu lực (`removed_at IS NULL`) và chưa có đơn đang xử lý / đã nhận | Chăm nhiều nhưng chưa chốt |
| **B** | Tạo quá 7 giờ (`customers.inserted_at`), có ≥ 1 ghi chú, chưa có đơn đang xử lý / đã nhận. Giới hạn trong cửa sổ 30 ngày gần nhất để tránh quét toàn DB | Để lâu chưa lên đơn |
| **C** | Có đơn đã nhận nhưng lần gần nhất cách đây > 5 tháng, hiện không có đơn đang xử lý | Khách cũ lâu chưa mua lại |

Một khách có thể thuộc nhiều nhóm cùng lúc (hiển thị nhiều badge).
Tất cả ngưỡng chỉnh được qua form filter: `minNotes` (5), `hours` (7), `maxDays` (30), `months` (5), `limit` (1000, trần 5000).

## 4. Cơ chế khớp đơn hàng ↔ khách hàng

Dữ liệu tồn tại 2 loại mã khách (lịch sử):
- Khách từ **đồng bộ POS**: mã UUID thật (vd `57cab618-...`).
- Khách/đơn từ **webhook** (trước đây): mã tự chế `CUST_<sđt>`.

Vì vậy đơn được khớp theo **2 đường song song**:
1. `orders.customer_id = customers.id` (khớp mã trực tiếp).
2. Khớp **SĐT**: so **9 số cuối** của `orders.customer_phone` với SĐT trong `customer_phone_numbers`
   (chấp nhận mọi biến thể `84...`, `0...`, `+84...`).

Khách bị loại / được tính "đã nhận" nếu **một trong hai đường** khớp. Số liệu "Tổng đơn" và
"Đơn thành công" cũng đếm gộp cả hai đường, có chống đếm trùng (đơn khớp cả 2 đường chỉ tính 1 lần).

> Ghi chú: từ 08/07/2026 webhook đã được sửa để dùng mã khách **thật** từ payload
> (không tự chế `CUST_` nữa) — về lâu dài đường khớp SĐT chỉ còn vai trò dự phòng cho dữ liệu cũ.

## 5. Kiến trúc & file liên quan

| Thành phần | File |
|---|---|
| Native query (1 câu SQL duy nhất) | `repository/ProblemCustomerRepository.java` |
| Projection kết quả | `customer/DTO/ProblemCustomerView.java` |
| Controller + phân loại nhóm + format | `customer/Controller/ProblemCustomerController.java` |
| Giao diện Thymeleaf | `resources/templates/problemCustomers.html` |
| Link sidebar | `resources/templates/index.html` |

### Khung câu SQL

- Bảng chính `customers`, LEFT JOIN các sub-query:
  - `n` — đếm ghi chú hiệu lực + ghi chú gần nhất (`customer_notes`, `removed_at IS NULL`).
  - `act` — đếm đơn **đang xử lý** khớp mã KH (`status IN (11,1,8,9,2)`).
  - `rcv` — đếm đơn **đã nhận** khớp mã KH + `MAX(inserted_at)` (`status IN (3,16)`).
  - `tot` — tổng đơn mọi trạng thái khớp mã KH.
  - `p` — lấy 1 SĐT hiển thị (ưu tiên `is_primary`).
  - `oph` — các cờ/đếm tương tự nhưng khớp qua **SĐT** (9 số cuối), kèm chống trùng.
- `WHERE`: loại khách có đơn đang xử lý → `(điều kiện nhóm A OR B) OR (điều kiện nhóm C)`.
- `ORDER BY noteCount DESC, inserted_at ASC` + `LIMIT`.
- Thời điểm ngưỡng (`now - 7h`, `now - 30 ngày`, `now - 5 tháng`) tính sẵn ở Java, truyền vào làm tham số.

## 6. Giao diện

- 4 thẻ tổng quan: Tổng KH cảnh báo / Nhóm A (cam) / Nhóm B (đỏ) / Nhóm C (tím).
- Form filter (GET) với 5 tham số ở mục 3.
- Bảng: STT, Tên, SĐT, Mã KH, Ghi chú (tô đậm khi ≥ ngưỡng), Tổng đơn, Đơn thành công,
  Ngày tạo KH, Ghi chú gần nhất, Đơn nhận gần nhất, Lý do cảnh báo (badge A/B/C + mô tả).
- Cảnh báo vàng khi kết quả bị cắt bởi `limit`.

## 7. Các sửa đổi hạ tầng đi kèm (phát sinh khi xây dựng)

1. **Lưu SĐT khi đồng bộ khách** (`CustomerSyncService`): trước đây API trả `phone_numbers`
   nhưng không lưu → bảng `customer_phone_numbers` trống với khách UUID → trang không hiện SĐT
   và không khớp được đơn theo SĐT. Đã lưu (chuẩn hóa chỉ chữ số, số đầu là primary).
2. **Sửa parse ghi chú** (`NoteApiDto`): API đôi khi trả `links`/`images` là chuỗi trần thay vì
   object → thêm `@JsonCreator` nhận String, hết lỗi dừng sync giữa chừng.
3. **Sửa phân trang API khách** (`CustomerListResponseDto` + client): thiếu mapping snake_case
   `total_pages`/`total_entries` → luôn null → logic "đoán trang". Đã map đúng, vòng lặp chạy
   `page_number` 1 → `total_pages`, bỏ hard-stop 1000 trang.
4. **Đổi tham số ngày API khách**: `start_date`/`end_date` → `start_time_inserted_at`/`end_time_inserted_at`
   (epoch giây, quy về múi giờ VN: 00:00:00 và 23:59:59).
5. **Sửa `shop_id` webhook**: khách từ webhook bị hardcode `shop_id = 1` → lấy từ payload,
   fallback `1546758`. Dữ liệu cũ sửa bằng `UPDATE customers SET shop_id = 1546758 WHERE shop_id = 1`.
6. **Webhook dùng mã khách thật**: map `customer.id` / `customer_id` từ payload, bỏ hẳn việc
   tự chế `CUST_<sđt>`; đơn hàng cũng gán mã thật. Khách `CUST_` cũ xóa bằng
   `DELETE FROM customers WHERE id LIKE 'CUST\_%'` (các bảng con cascade; `orders.customer_id`
   về NULL và được webhook điền lại dần).

## 8. Vận hành & lưu ý

- Nhóm B cố ý giữ điều kiện "có ≥ 1 ghi chú" + cửa sổ 30 ngày — nếu bỏ, gần như toàn bộ khách
  trong DB sẽ lọt vào (ngưỡng 7h rất ngắn).
- Bảng sắp theo số ghi chú giảm dần nên khách nhóm C (thường 0 ghi chú) nằm cuối — nếu tổng
  vượt `limit` thì nhóm C bị cắt trước; cần xem riêng nhóm C thì tăng `limit`.
- Độ phủ khớp SĐT phụ thuộc bảng `customer_phone_numbers` — cần chạy đồng bộ khách hàng đầy đủ
  (`POST /api/customers/sync`) để bảng SĐT được phủ kín.
- Index nên có: `customer_notes(customer_id)`, `orders(customer_id, status)`,
  `customer_phone_numbers(customer_id)`, `customer_phone_numbers(phone_number)`.
- Nếu query chậm khi dữ liệu lớn: cân nhắc cache kết quả vài phút hoặc chuyển sang phân trang.
