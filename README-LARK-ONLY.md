# Nhánh `feat/lark-only-webhook` — chỉ tạo bản ghi Lark từ data POS

Nhánh rút gọn để chạy khi database `pos_db` đang lỗi. App **không kết nối database**: nhận webhook đơn hàng từ POS (Pancake) và tạo bản ghi trong Lark Bitable.

Mọi thứ khác trên `master` (báo cáo, khách hàng, chấm công, LT khách, DT ADS, đồng bộ POS...) đã bị loại khỏi nhánh này.

## Khác biệt so với `master`

| Thành phần | master | nhánh này |
|---|---|---|
| Datasource / JPA / MariaDB | có | **đã bỏ khỏi `pom.xml`** |
| Lưu webhook vào DB (`WebhookPersistenceService`) | có | bỏ |
| Mapping CSKH → Base/Table | bảng `search_config` | file JSON |
| Nhắc follow-up 30 phút (`pending_followup_notifications`) | có | bỏ |
| Thông báo Lark IM khi đổi account | có | bỏ |
| Thymeleaf / templates / static | có | bỏ |
| Token Lark | in-memory (giữ nguyên) | in-memory |

## Luồng xử lý

`POST /api/lark/orders` → kiểm tra `X-Pancake-Secret` → parse JSON → quyết định có tạo bản ghi không:

- Đơn có tag **"Đồng bộ DATA"** (id = 32) → tạo, hoặc
- `status` được bật trong `WebhookConfigService` (mặc định: status 1 bật, status 6 tắt) **và** đơn nguồn Facebook **và** không phải trạng thái hủy (5, 7, 8, 9, -1) **và** `status = 1` hoặc có `status = 1` trong lịch sử.

Khi tạo bản ghi:

1. Lấy SĐT CSKH từ `assigning_care.phone_number`, nếu trống thì tách SĐT từ tên CSKH.
2. Tra file mapping → `baseId`, `khachHangTableId`, `khachHangViewId`.
3. Lấy user access token (fallback: tenant token từ `app-id`/`app-secret`).
4. Kiểm tra SĐT khách đã tồn tại trong bảng Lark chưa → nếu có thì bỏ qua.
5. Tạo bản ghi qua Bitable API.

Mọi lỗi ở bước tạo bản ghi đều được log và webhook vẫn trả `ok` để POS không retry dồn.

## File mapping CSKH

Thay cho bảng `search_config`. Thứ tự tìm file:

1. `cskh.mapping.file` — mặc định `config/cskh-mapping.json` (đường dẫn tương đối thư mục chạy app)
2. `classpath:cskh-mapping.json` (bản mẫu trong `src/main/resources`)

```json
{
  "mappings": [
    {
      "cskhName": "Tên CSKH trên POS",
      "posPhone": "0968420624",
      "baseName": "Tên Base (chỉ để đối soát)",
      "baseId": "bascnXXXXXXXX",
      "khachHangTableId": "tblXXXXXXXX",
      "khachHangViewId": "vew5Ou4Kee"
    }
  ]
}
```

- Khóa tra cứu là `posPhone`, so khớp theo **9 số cuối** (bỏ qua khác biệt `0` / `+84`).
- Entry thiếu `baseId` hoặc `khachHangTableId` sẽ bị bỏ qua kèm cảnh báo trong log.
- `khachHangViewId` để trống → dùng mặc định `vew5Ou4Kee`.

Sửa file xong nạp lại không cần restart:

```bash
curl -X POST http://localhost:8080/api/lark/cskh-mapping/reload
```

### Sinh file mapping từ DB (khi `pos_db` hoạt động trở lại)

```sql
SELECT JSON_OBJECT('mappings', JSON_ARRAYAGG(JSON_OBJECT(
         'cskhName', pos_name,
         'posPhone', pos_phone,
         'baseName', lark_base_name,
         'baseId', lark_base_id,
         'khachHangTableId', khach_hang_table_id,
         'khachHangViewId', khach_hang_view_id)))
FROM search_config
WHERE sync_status = 2
  AND pos_phone IS NOT NULL
  AND lark_base_id IS NOT NULL
  AND khach_hang_table_id IS NOT NULL;
```

Lưu kết quả vào `config/cskh-mapping.json` cạnh file jar rồi gọi endpoint reload.

## Endpoints

| Method | Path | Mô tả |
|---|---|---|
| POST | `/api/lark/orders` | Webhook đơn hàng từ POS |
| GET | `/api/lark/health` | Trạng thái app, token, số mapping đang nạp |
| GET | `/api/lark/cskh-mapping` | Danh sách mapping đang dùng |
| POST | `/api/lark/cskh-mapping/reload` | Nạp lại mapping từ file |
| GET | `/lark` | Trang đăng nhập Lark (OAuth) |
| GET | `/oauth/callback`, `/lark/oauth/callback` | Callback OAuth |
| GET | `/lark/token`, `/token` | Trạng thái token |

## Cấu hình chính (`application.properties`)

| Key | Ý nghĩa |
|---|---|
| `lark.app-id`, `lark.app-secret` | App Lark |
| `lark.redirect-uri` | Phải khớp redirect URI khai báo trên Lark |
| `lark.bitable.auto-create` | `false` = chỉ log, không ghi sang Lark |
| `pancake.webhook.secret` | Header `X-Pancake-Secret`; để trống là tắt kiểm tra |
| `cskh.mapping.file` | Đường dẫn file mapping |
| `lark.http.connect-timeout`, `lark.http.read-timeout` | Timeout gọi API Lark |

## Chạy

```bash
./mvnw spring-boot:run
```

Sau khi app chạy, vào `http://localhost:8080/lark` đăng nhập Lark để lấy user access token (token giữ trong bộ nhớ, tự làm mới mỗi giờ; **restart app phải đăng nhập lại**). Nếu chưa đăng nhập, app fallback sang tenant token lấy từ `app-id`/`app-secret`.

## Lưu ý khi deploy

- App này chiếm cổng 8080 giống bản `master` — chạy song song thì đổi `server.port` hoặc dừng bản cũ, và trỏ webhook POS về đúng cổng.
- Không có DB nghĩa là không có lịch sử đơn hàng: các bản ghi trùng chỉ được chặn nhờ kiểm tra SĐT trực tiếp trên bảng Lark.
