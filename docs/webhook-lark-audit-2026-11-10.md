# Kiểm tra webhook tạo bản ghi Lark — 10/11/2026

## Phạm vi và kết quả

Kiểm tra mã nguồn hiện tại và chạy 7 phép thử mô phỏng với Mockito. Cả 7 phép thử tái hiện được hành vi bên dưới. Đây là kiểm thử xác nhận hiện trạng lỗi, không phải 7 kiểm thử chứng minh chức năng đã đúng.

Không gọi API Lark thật, không ghi DB, không khởi động Spring context, không gửi tin nhắn. Chưa xác nhận cấu hình hoặc token của ứng dụng đang chạy. Không sửa mã nghiệp vụ trong lần kiểm tra này. Test tạm đã được gỡ sau khi chạy.

Lệnh kiểm thử đã chạy: `mvn -q -Dtest=WebhookAuditProbeTest test`.

## Luồng hiện tại

`POST /api/lark/orders` kiểm tra secret, parse JSON rồi đưa dữ liệu vào hai hàng đợi RAM riêng cho DB và Lark. HTTP 200 được trả trước khi xử lý hoàn tất.

Nhánh Lark xét status và tag. Khi tạo, lấy CSKH từ `assigning_care`, trích số điện thoại từ tên CSKH để tìm `search_config`, lấy Base/Table, map dữ liệu khách, kiểm tra trùng số điện thoại rồi gọi API tạo bản ghi.

Các trường được map hiện tại là Tên khách hàng, Điện thoại, Địa chỉ, Tỉnh/Thành phố và Tên Liệu Trình (mặc định Liệu trình 1). Không phải toàn bộ dữ liệu đơn hàng.

## Các trường hợp đã tái hiện

| Trường hợp | Hành vi hiện tại | Tác động |
|---|---|---|
| Status 1 và tag 32 cùng có, bước tìm trùng trả không tìm thấy | Gọi tạo 2 lần trong một lượt xử lý | Có thể trùng nếu bước tìm trùng chưa thấy bản ghi vừa tạo hoặc lỗi |
| Status 5 và tag 32 | Vẫn gọi tạo | Nhánh cấu hình status bỏ qua kiểm tra đơn hủy, sau đó nhánh tag tiếp tục tạo |
| Payload bọc trong `data`, có nguồn Facebook và status 1, không tag | Không gọi tạo | Bộ lọc nguồn đọc ở root; DTO `OrderData` không có trường nguồn/page để kiểm tra |
| CSKH có `phone_number` nhưng tên không chứa SĐT | Không tra mapping, không tạo | Chỉ trích SĐT từ tên, bỏ qua trường SĐT đã có |
| `customer: {}` và SĐT hợp lệ ở root | Mapper đưa SĐT vào record nhưng hàm kiểm tra trùng lấy null | Bỏ qua kiểm tra trùng dù bản ghi được tạo có SĐT |
| API tìm trùng ném lỗi | Trả false (chưa có SĐT) | Tiếp tục tạo khi chưa xác định được có trùng hay không |
| Kết quả tìm trùng có `items`, không có `total` | Trả false | Chỉ xét total; đây là phản hồi mô phỏng, chưa xác nhận xảy ra với API đang dùng |

Vị trí chính trong `src/main/java/mera/mera_v2/`:

- `lark/webhook/LarkWebhookController.java:235` — thứ tự điều kiện status và loại trừ nguồn/đơn hủy.
- `lark/webhook/LarkWebhookController.java:259` — nhánh tag có thể gọi tạo thêm lần nữa.
- `lark/webhook/LarkWebhookController.java:288` — đọc nguồn ở root.
- `lark/webhook/LarkWebhookController.java:573` — trích SĐT từ tên CSKH.
- `lark/webhook/service/PosToBitableMapper.java:121` — cách lấy SĐT khác mapper tạo fields.
- `lark/webhook/service/LarkBitableService.java:214` — chỉ xét total.
- `lark/webhook/service/LarkBitableService.java:229` — nuốt lỗi tìm trùng và trả false.

## Rủi ro bổ sung từ mã nguồn

- Hai webhook đồng thời có thể cùng tìm thấy chưa tồn tại rồi cùng tạo. Chưa có khóa hoặc idempotency bền vững cho thao tác tìm-và-tạo.
- Queue nằm trong RAM, trả 200 trước xử lý. Lỗi tạo chỉ ghi log, không có trạng thái retry bền vững cho nhánh tạo này. Restart có thể làm mất công việc đã nhận.
- Thiếu view trong mapping thì dùng cứng `vew5Ou4Kee`; cần kiểm tra view có thuộc đúng bảng và phạm vi tìm trùng mong muốn hay không.
- Tài liệu `pos_webhook_lark_flow.md` mô tả schedule follow-up nhưng lời gọi này đã bị bỏ khỏi nhánh tạo hiện tại.

## Thứ tự sửa đề xuất

1. Chuẩn hóa payload root/data, dùng một hàm lấy SĐT thống nhất cho mapping và kiểm tra trùng. Ưu tiên `assigning_care.phone_number`, fallback trích từ tên.
2. Tính điều kiện tạo một lần, bảo đảm mỗi lượt xử lý chỉ gọi tạo tối đa một lần. Giữ quy tắc nghiệp vụ nguồn/status/tag rõ ràng, đặc biệt đơn hủy và status đang tắt.
3. Phân biệt tìm thấy, không tìm thấy và lỗi tra cứu. Không coi lỗi là không trùng; xử lý retry có kiểm soát.
4. Bổ sung trạng thái xử lý bền vững, chống xử lý đồng thời và cơ chế đối soát sau lỗi timeout tạo bản ghi. Tránh retry mù vì Lark có thể đã tạo thành công dù ứng dụng chưa nhận được phản hồi.
5. Sau khi sửa, chạy kiểm thử hồi quy rồi đối chiếu một webhook thực và bản ghi Lark tương ứng.
