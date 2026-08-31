# Nhánh `feat/lark-only-webhook` — POS → Lark, không cần database

Nhánh rút gọn để chạy khi database `pos_db` đang lỗi. App **không kết nối database**, giữ 2 chức năng:

1. **Webhook POS → tạo bản ghi Lark Bitable** (`POST /api/lark/orders`)
2. **Trang tra cứu khách hàng 360** (`/search-info`) — gộp dữ liệu POS + các Base Lark

Cả hai dùng chung một nguồn cấu hình duy nhất: file `cskh-mapping.json`.

Mọi thứ khác trên `master` (báo cáo, chấm công, LT khách, DT ADS, đồng bộ POS...) đã bị loại khỏi nhánh này.

## Khác biệt so với `master`

| Thành phần | master | nhánh này |
|---|---|---|
| Datasource / JPA / MariaDB | có | **đã bỏ khỏi `pom.xml`** |
| Lưu webhook vào DB (`WebhookPersistenceService`) | có | bỏ |
| Mapping CSKH → Base/Table | bảng `search_config` | file JSON |
| Nhắc follow-up 30 phút (`pending_followup_notifications`) | có | bỏ |
| Thông báo Lark IM khi đổi account | có | bỏ |
| Trang `/search-info` | đọc bảng `search_config` | đọc file JSON, trang tĩnh (bỏ Thymeleaf) |
| Các trang khác + Thymeleaf layout | có | bỏ |
| Token Lark | in-memory (giữ nguyên) | in-memory |

## Luồng webhook

`POST /api/lark/orders` → kiểm tra `X-Pancake-Secret` → parse JSON → quyết định có tạo bản ghi không:

- Đơn có tag **"Đồng bộ DATA"** (id = 32) → tạo, hoặc
- `status` được bật trong `WebhookConfigService` (mặc định: status 1 bật, status 6 tắt) **và** đơn nguồn Facebook **và** không phải trạng thái hủy (5, 7, 8, 9, -1) **và** `status = 1` hoặc có `status = 1` trong lịch sử.

Khi tạo bản ghi:

1. Tra mapping của CSKH theo thứ tự: **SĐT trong tên CSKH** → SĐT tài khoản POS (`assigning_care.phone_number`) → tên CSKH đã chuẩn hóa.
   SĐT tài khoản POS thường **khác** SĐT dùng trong bảng mapping nên không được ưu tiên.
2. Lấy `baseId`, `khachHangTableId`, `khachHangViewId` từ entry tra được.
3. Lấy user access token (fallback: tenant token từ `app-id`/`app-secret`).
4. Kiểm tra SĐT khách đã tồn tại trong bảng Lark chưa → nếu có thì bỏ qua.
5. Tạo bản ghi qua Bitable API.

Mọi lỗi ở bước tạo bản ghi đều được log và webhook vẫn trả `ok` để POS không retry dồn.

## File mapping CSKH

Thay cho bảng `search_config`. Thứ tự tìm file:

1. `cskh.mapping.file` — mặc định `config/cskh-mapping.json` (đường dẫn tương đối thư mục chạy app)
2. `classpath:cskh-mapping.json` (bản mẫu trong `src/main/resources`)

Bản đang dùng có 63 entry: **54 CSKH** + **9 base hệ thống**.

```json
{
  "mappings": [
    {
      "cskhName": "Tên CSKH trên POS",
      "posPhone": "0968420624",
      "baseName": "Tên Base",
      "baseId": "bascnXXXXXXXX",
      "khachHangTableId": "tblXXXXXXXX",
      "khachHangViewId": "vew5Ou4Kee",
      "traoDoiTableId": "tblYYYYYYYY",
      "lichHenTableId": "tblZZZZZZZZ"
    },
    {
      "baseName": "TỪ CHỐI CHĂM",
      "baseId": "bascnWWWWWWWW",
      "khachHangTableId": "tblWWWWWWWW",
      "specialWarningName": "TỪ CHỐI CHĂM"
    }
  ]
}
```

| Trường | Webhook | /search-info |
|---|---|---|
| `posPhone` | **bắt buộc** — khóa tra cứu (khớp 9 số cuối) | không dùng |
| `cskhName` | dự phòng khi không khớp SĐT | phân biệt base CSKH / base hệ thống |
| `baseId` | **bắt buộc** | **bắt buộc** |
| `khachHangTableId` | **bắt buộc** | dùng nếu có, không thì tự liệt kê bảng |
| `khachHangViewId` | mặc định `vew5Ou4Kee` | — |
| `traoDoiTableId` / `lichHenTableId` | không dùng | đọc lịch sử trao đổi |
| `specialWarningName` | không dùng | hiện cảnh báo "Khách hàng nằm trong bảng X" |

- **Entry không có `posPhone`** = base hệ thống dùng chung (Từ chối chăm, Đơn hoàn, Hủy, Đang chăm, Thống kê...). Webhook bỏ qua chúng; `/search-info` vẫn quét và tự liệt kê bảng qua Lark API (cache 10 phút).
- `specialWarningName` hiện đang bật cho 4 base: **TỪ CHỐI CHĂM, Đơn hoàn, Hủy, ĐANG CHĂM**. Thêm/bớt bằng cách sửa trường này trong file.
- Entry không có `baseId` bị bỏ qua kèm cảnh báo trong log.

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
         'khachHangViewId', khach_hang_view_id,
         'traoDoiTableId', trao_doi_table_id,
         'traoDoiViewId', trao_doi_view_id,
         'lichHenTableId', lich_hen_table_id,
         'lichHenViewId', lich_hen_view_id)))
FROM search_config
WHERE sync_status = 2
  AND lark_base_id IS NOT NULL;
```

Câu này giữ cả base hệ thống (`pos_phone` null) vì `/search-info` cần chúng.

Lưu kết quả vào `config/cskh-mapping.json` cạnh file jar rồi gọi endpoint reload.

## Endpoints

| Method | Path | Mô tả |
|---|---|---|
| POST | `/api/lark/orders` | Webhook đơn hàng từ POS |
| GET | `/api/lark/health` | Trạng thái app, token, số mapping đang nạp |
| GET | `/api/lark/cskh-mapping` | Danh sách mapping đang dùng |
| POST | `/api/lark/cskh-mapping/reload` | Nạp lại mapping từ file |
| GET | `/search-info` | Trang tra cứu khách hàng 360 |
| GET | `/api/search-info?phone=...` | API tra cứu: khách + đơn POS + trao đổi Lark |
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
| `pos.api.base-url`, `pos.api.shop-id`, `pos.api.api-key` | POS API cho trang `/search-info` |
| `lark.http.connect-timeout`, `lark.http.read-timeout` | Timeout gọi API Lark |

## Chạy

```bash
./mvnw spring-boot:run
```

Sau khi app chạy, vào `http://localhost:8080/lark` đăng nhập Lark để lấy user access token (token giữ trong bộ nhớ, tự làm mới mỗi giờ; **restart app phải đăng nhập lại**). Nếu chưa đăng nhập, app fallback sang tenant token lấy từ `app-id`/`app-secret`.

## Trang /search-info

Tra cứu 360 theo SĐT khách: thông tin khách + đơn hàng + ghi chú từ POS API, cộng lịch sử trao đổi quét song song qua tất cả Base trong file mapping.

- Trang là file tĩnh `static/search-info.html` (đã bỏ layout Thymeleaf), JS/CSS giữ nguyên như `master`.
- Base của CSKH cần **user access token** — chưa đăng nhập `/lark` thì các base này trả `RolePermNotAllow` và kết quả chỉ còn dữ liệu POS + base hệ thống (bot token đọc được).
- Base hệ thống được tự liệt kê bảng qua Lark API, cache 10 phút.

## Lưu ý khi deploy

- App này chiếm cổng 8080 giống bản `master` — chạy song song thì đổi `server.port` hoặc dừng bản cũ, và trỏ webhook POS về đúng cổng.
- Không có DB nghĩa là không có lịch sử đơn hàng: các bản ghi trùng chỉ được chặn nhờ kiểm tra SĐT trực tiếp trên bảng Lark.
