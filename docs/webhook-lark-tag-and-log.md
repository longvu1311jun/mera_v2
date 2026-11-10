# Tạo bản ghi Lark theo tag và theo dõi log

## Điều kiện mới

- Hai điều kiện kích hoạt tạo bản ghi: (1) tag ID 32 hoặc tên `Đồng bộ DATA`, bất kể status/nguồn; (2) status hiện tại = 1, đơn nguồn Facebook và công tắc status 1 (`/api/config/status1`, mặc định bật) đang bật.
- `lark.bitable.auto-create` vẫn là công tắc tổng. Đơn vừa status 1 vừa có tag chỉ đi qua nhánh tạo một lần; webhook lặp lại của cùng đơn bị chặn bởi kiểm tra trùng SĐT.
- Mapping CSKH và token phải hợp lệ. SĐT khách được lấy từ chính dữ liệu chuẩn bị gửi sang Lark để kiểm tra trùng trên toàn bảng đích. Lỗi tra cứu không còn được coi là chưa trùng.
- Thay đổi này không bổ sung khóa chống hai webhook độc lập chạy đồng thời, cũng không phải hàng đợi retry bền vững.

## Ngày từ webhook

Đọc `histories[].status.new = 1` và `histories[].updated_at`, hỗ trợ cả root và `data.histories`. Bỏ qua lịch sử `old = new = 1`. Nếu có nhiều lần chuyển về 1, dùng timestamp lớn nhất, không phụ thuộc thứ tự mảng.

Timestamp có offset/Z giữ đúng múi giờ. Timestamp không có múi giờ mặc định hiểu là giờ Việt Nam, có thể cấu hình bằng `pancake.webhook.history-zone`. Hỗ trợ ISO datetime và epoch giây/mili giây.

Gửi timestamp mili giây vào cột `Ngày tạo`, có thể đổi tên cột đích bằng `lark.bitable.status-one-date-field`. Cột phải là **Date (type 5)**. Nếu là **Created time (type 1001)**, log báo `WAITING_FIELD`; cần cấu hình một cột Date có thể ghi. Không tự sửa cấu trúc hoặc dữ liệu cũ của các Base.

Không tìm thấy ngày hoặc ngày không hợp lệ thì ghi `WAITING_DATE`, không thay bằng giờ hiện tại. Đây là kết quả chưa tạo, không tự động retry. Sau khi sửa dữ liệu/cấu hình, gửi lại webhook.

## Màn hình log

Đường dẫn `/webhook-lark-logs`, menu Hệ Thống → Log tạo khách Lark.

Lọc theo ngày nhận/cập nhật log, mã đơn và kết quả. Mỗi lượt nhận webhook có một ID riêng; trang hiển thị trạng thái mới nhất của lượt đó, mã đơn/shop, CSKH, Base/Table, ngày chuyển status 1, record ID và lý do.

Log lưu tại `logs/lark-webhook/YYYY-MM-DD.jsonl` trên máy chạy ứng dụng. Có thể cấu hình thư mục bằng `lark.webhook.creation-log-dir`. Không cần tạo bảng DB. Log tồn tại sau restart, không chứa token hoặc toàn bộ payload khách hàng. Khi chuyển máy triển khai cần giữ thư mục này. Hiện không tự xóa log cũ.

Trang hiển thị tối đa 200 lượt trong 10.000 lượt gần nhất khớp mã đơn, khoảng ngày tối đa 31 ngày. Bấm Làm mới để xem cập nhật. Log chỉ có từ lúc phiên bản này được chạy.

- `CREATED`: tạo thành công, có record ID.
- `DUPLICATE`: SĐT đã có trong bảng đích.
- `SKIPPED`: không tag và không phải status 1 nguồn Facebook, hoặc tắt auto-create.
- `WAITING_MAPPING/DATE/FIELD/TOKEN/PHONE`: chưa tạo; xem lý do và bổ sung cấu hình/dữ liệu.
- `FAILED`: lỗi xử lý, xem bước lỗi và log máy chủ.
- `UNKNOWN`: API tạo không trả record ID; đối soát Lark trước khi gửi lại.
- `QUEUED/PROCESSING`: đã nhận/đang xử lý. Nếu treo sau restart, đối soát trước khi gửi lại.

HTTP 200 vẫn chỉ xác nhận tiếp nhận webhook. Log là nhật ký theo dõi, không lưu payload để tự phục hồi công việc. Không có nút retry trong phiên bản này.

## Kiểm chứng

Chạy các test `CskhWebhookMappingTest`, `WebhookLogAndDateTest`, `LarkBitableCreationGuardTest`, `PosMappingReloadTest`. Dùng Mockito/HTTP mock, không khởi động ứng dụng hoặc ghi dữ liệu thật. Bao gồm render Thymeleaf và escape nội dung log.
