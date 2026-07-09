# Hướng dẫn sử dụng trang "Số Thả Nổi" — Dành cho team Sale

> **Đường dẫn truy cập:** http://100.70.133.122:8080/so-tha-noi
> Mở bằng trình duyệt (Chrome/Cốc Cốc/Edge). Có thể lưu (bookmark) để dùng hằng ngày.

> 🖼️ *Ghi chú khi soạn trên Lark:* các ô đánh dấu **🖼️ Ảnh …** bên dưới là chỗ chèn ảnh chụp màn hình (được gửi kèm) cho trực quan.

---

## 1. Trang này để làm gì?

"Số Thả Nổi" là danh sách những **khách hàng đang bị "bỏ quên"** — tức là khách có dấu hiệu quan tâm hoặc từng mua nhưng **hiện chưa được chốt đơn / chưa được chăm lại**.

Mục đích: giúp sale **không bỏ sót khách tiềm năng**, biết ngay ai cần gọi/nhắn để chốt đơn hoặc mời mua lại, thay vì phải tự dò trong hệ thống.

Nói ngắn gọn: **mở trang này ra là thấy ngay danh sách khách cần “vớt” về để chốt.**

---

## 2. Ba nhóm cảnh báo (A / B / C)

Mỗi khách trong danh sách thuộc ít nhất một trong ba nhóm sau. Cột **"Lý do cảnh báo"** sẽ ghi rõ khách thuộc nhóm nào.

| Nhóm | Tên gọi | Nghĩa là gì | Sale nên làm gì |
|------|---------|-------------|-----------------|
| 🟠 **Nhóm A** | Chăm nhiều chưa chốt | Khách đã được ghi chú/chăm sóc **nhiều lần** (từ 5 lần trở lên) nhưng **vẫn chưa lên đơn**. | Khách đang quan tâm rõ rệt → **ưu tiên gọi chốt ngay**. |
| 🔴 **Nhóm B** | Quá hạn chưa lên đơn | Khách **mới tạo gần đây**, đã qua vài giờ (mặc định 7h) mà **vẫn chưa có đơn**. | Khách mới đang “nguội” dần → **liên hệ sớm khi còn nóng**. |
| 🔵 **Nhóm C** | Lâu chưa mua lại | Khách **từng mua hàng** nhưng lần nhận đơn gần nhất **đã quá lâu** (mặc định hơn 5 tháng) và hiện không có đơn nào. | Khách cũ → **chăm lại, mời mua lại / up-sale**. |

> Ghi chú: một khách có thể vừa thuộc nhóm này vừa nhóm khác. Đơn của khách được đối chiếu theo **mã khách hàng** hoặc theo **số điện thoại**.

---

## 3. Màn hình hiển thị những gì?

> 🖼️ **Ảnh 1 — Toàn cảnh trang:** phía trên là 4 ô số liệu, ở giữa là thanh lọc (nhóm / khoảng ngày / tìm kiếm), bên dưới là bảng danh sách khách hàng.

### a) Bốn ô số liệu ở trên cùng
- **Tổng KH cảnh báo:** tổng số khách đang cần chú ý.
- **Nhóm A / Nhóm B / Nhóm C:** số lượng khách của từng nhóm.
- Di chuột vào dấu **"?"** cạnh mỗi tiêu đề để xem giải thích nhanh của nhóm đó.
- **Mẹo:** bấm vào một ô (ví dụ ô "Nhóm A") để **lọc nhanh** chỉ xem khách nhóm đó.

> 🖼️ **Ảnh 2 — Xem mô tả nhóm:** di chuột vào dấu "?" cạnh tên nhóm sẽ hiện ô giải thích ngắn.

### b) Bảng danh sách khách hàng
Các cột trong bảng:

| Cột | Ý nghĩa |
|-----|---------|
| **STT** | Số thứ tự dòng |
| **Tên khách hàng** | Tên khách |
| **SĐT** | Số điện thoại để liên hệ |
| **Mã KH** | Mã khách trong hệ thống |
| **Ghi chú** | Số lần đã chăm sóc/ghi chú (số **in đậm màu cam** = chăm nhiều) |
| **Tổng đơn** | Tổng số đơn của khách từ trước tới nay |
| **Đơn thành công** | Số đơn đã nhận / đã thu tiền |
| **Ngày tạo KH** | Ngày khách được đưa vào hệ thống |
| **Ghi chú gần nhất** | Lần chăm sóc gần nhất |
| **Đơn nhận gần nhất** | Lần nhận đơn gần nhất (dấu "—" nghĩa là chưa từng có đơn nhận) |
| **Lý do cảnh báo** | Nhãn nhóm (A/B/C) + mô tả vì sao khách bị cảnh báo |

---

## 4. Cách sử dụng (các thao tác chính)

### 🔎 Tìm kiếm khách
Gõ **tên khách** hoặc **số điện thoại** vào ô "Tìm kiếm" → danh sách lọc ngay lập tức.

### 🏷️ Lọc theo nhóm
Chọn ở ô "Lọc theo nhóm": *Tất cả nhóm / Nhóm A / Nhóm B / Nhóm C*.
(Hoặc bấm thẳng vào ô số liệu tương ứng ở trên.)

> 🖼️ **Ảnh 3 — Lọc theo nhóm:** khi chọn "Nhóm A", ô số liệu tương ứng sáng viền xanh và bảng chỉ còn khách Nhóm A.

### 📅 Lọc theo khoảng ngày tạo khách
Dùng **"Ngày tạo KH từ"** và **"Đến ngày"** để chỉ xem khách được tạo trong một khoảng thời gian (ví dụ: khách tạo trong tháng 11).
Rất hữu ích khi danh sách quá dài — thu hẹp theo tháng để xem cho đủ.

### 📄 Chuyển trang & số dòng mỗi trang
- Dùng các nút số trang ở dưới (‹ 1 2 3 … › ) để lật trang.
- Chọn **"… / trang"** để đổi số dòng hiển thị mỗi trang (30, 50, 100, 200, 500, 1000).

### 🔄 Làm mới dữ liệu
- Trang **tự động cập nhật khoảng mỗi 60 giây**.
- Muốn cập nhật ngay, bấm nút **"↻ Làm mới"**. Dòng chữ "Cập nhật lúc …" cho biết lần làm mới gần nhất.

### 🌙 Giao diện sáng / tối
Bấm nút 🌙 / ☀️ ở góc trên bên phải để đổi nền sáng hoặc tối tùy thích.

---

## 5. Gợi ý quy trình cho Sale

1. **Đầu ca:** mở trang, xem tổng số khách cảnh báo.
2. **Ưu tiên Nhóm A** (chăm nhiều chưa chốt) → gọi chốt trước vì khách đang quan tâm.
3. **Nhóm B** (khách mới quá hạn) → liên hệ nhanh khi khách còn “nóng”.
4. **Nhóm C** (lâu chưa mua lại) → lên kịch bản chăm lại / mời mua lại.
5. Dùng **tìm kiếm** khi cần tra nhanh một khách cụ thể; dùng **lọc theo ngày** khi muốn tập trung vào một nhóm khách theo thời điểm.

---

## 6. Một vài lưu ý

- Đây là **dữ liệu dùng chung** cho cả team — hãy phối hợp để tránh 2 người cùng gọi một khách.
- Nếu thấy dòng cảnh báo **"Kết quả đã chạm trần 5000 dòng"**: nghĩa là số khách vượt mức hiển thị tối đa. Hãy **chọn khoảng "Ngày tạo KH từ … đến …"** (ví dụ theo từng tháng) để xem đầy đủ hơn.
- Số liệu phản ánh dữ liệu tại thời điểm cập nhật; sau khi bạn chốt đơn/chăm khách, ở lần làm mới sau khách có thể tự rời khỏi danh sách.
- Trang chỉ để **xem** — không chỉnh sửa gì trên đây, nên bạn cứ thao tác thoải mái.

---

## 7. Câu hỏi thường gặp

**Hỏi: Sao khách này lại xuất hiện ở đây?**
Xem cột **"Lý do cảnh báo"** — sẽ ghi rõ (ví dụ: *"Chăm 17 ghi chú nhưng chưa có đơn xử lý / đã nhận"*).

**Hỏi: Tôi chốt đơn xong rồi, sao khách vẫn còn?**
Danh sách cập nhật theo chu kỳ (~60 giây) và theo dữ liệu đồng bộ về hệ thống. Bấm **"↻ Làm mới"** hoặc đợi lần cập nhật kế tiếp, khách sẽ tự rời danh sách khi đã có đơn.

**Hỏi: Cột "Đơn nhận gần nhất" hiển thị "—" là sao?**
Nghĩa là khách **chưa từng có đơn được nhận** — thường là khách Nhóm A hoặc Nhóm B.

**Hỏi: Mở trên điện thoại được không?**
Được. Trang hiển thị tốt trên điện thoại; bảng có thể vuốt ngang để xem hết các cột.

---

*Tài liệu dành cho team Sale — MeraGroup · Trang "Số Thả Nổi".*
