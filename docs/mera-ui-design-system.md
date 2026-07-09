# MERA UI — Design system nội bộ & quy tắc chuyển đổi template

> File CSS dùng chung: `src/main/resources/static/css/mera-ui.css` (Spring Boot serve tại `/css/mera-ui.css`).
> Fragments layout dùng chung: `src/main/resources/templates/fragments/mera-layout.html`.
> Design Read: redesign-preserve bộ dashboard nội bộ, ngôn ngữ sáng kiểu Notion/Linear, app shell kiểu Fluent.
> Dials: VARIANCE 3 / MOTION 2 / DENSITY 6. Accent lock: #1456f0 (light) / #5b8bf7 (dark).
> Shape lock: card 12px, control 8px, badge pill. Font: Inter. Theme: light mặc định + dark mode qua `[data-theme="dark"]` (toggle lưu localStorage `mera-theme`).

## Hai layout chuẩn

### 1. CRM shell (có sidebar) — mọi trang quản trị/cấu hình/danh sách

```html
<head>
  <script th:replace="~{fragments/mera-layout :: themeInit}"></script>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="/css/mera-ui.css">
</head>
<body class="mera-page">
<div class="mr-shell">
  <th:block th:replace="~{fragments/mera-layout :: sidebar('KEY')}"></th:block>
  <div class="mr-shell-main">
    <th:block th:replace="~{fragments/mera-layout :: topbar('Tên Trang')}"></th:block>
    <main class="mr-shell-content"> ...nội dung... </main>
    <footer class="mr-shell-footer">MeraGroup Dashboard v2.0</footer>
  </div>
</div>
<th:block th:replace="~{fragments/mera-layout :: shellScript}"></th:block>
</body>
```

Sidebar KEY hiện có: dashboard, saleReport, sale1report, stats, report, dongbo, attendance,
assignment, assignmentConfig, searchInfo, searchSale, searchCustomer, searchConfig,
pancakeOrders, problemCustomers, config, token, posUserMapping, cskhMapping, larkBitable,
employeeMapping, logs.

### 2. Workspace (không sidebar) — trang report/search/analytics cần tối đa bề rộng

```html
<body class="mera-page">
<th:block th:replace="~{fragments/mera-layout :: workspaceHeader('Tên Trang')}"></th:block>
<div class="mr-workspace-content"> ...filter toolbar / summary cards / bảng... </div>
<th:block th:replace="~{fragments/mera-layout :: shellScript}"></th:block>
</body>
```

Trang workspace: saleReport, sale1report, stats, report, searchInfo, searchCustomer.
Trang có kiến trúc full-height riêng (overflow:hidden, bảng cuộn nội bộ): giữ kiến trúc,
chèn workspaceHeader lên đầu, giữ `flex:1; min-height:0`.

## Class chính

Bố cục: `mr-shell`, `mr-sidebar`, `mr-shell-main`, `mr-topbar` (breadcrumb), `mr-shell-content`,
`mr-shell-footer`, `mr-workspace-header`, `mr-workspace-content`, `mr-bento` (+`mr-bento-3/4/6/8/12`).
Thành phần: `mr-card`(+`--flush`), `mr-stat-grid`/`mr-stat`(+`--accent/--warn/--danger/--info/--success`),
`mr-table` (bọc trong `mr-card mr-card--flush` + `mr-table-scroll`, sticky header sẵn),
`mr-btn`(+`--primary/--danger/--ghost`), `mr-icon-btn`, `mr-form-row`/`mr-field`/`mr-input`/`mr-select`,
`mr-badge`(+màu), `mr-alert`(+màu), `mr-tabs`/`mr-tab`, `mr-pagination`/`mr-page-btn`, `mr-toolbar`,
`mr-dropdown`, tooltip qua `data-mr-tip`, `mr-empty`, `mr-skeleton`.

## Quy tắc chuyển đổi (BẮT BUỘC — vi phạm là hỏng chức năng)

**KHÔNG được đổi:**
1. Mọi thuộc tính Thymeleaf (`th:*`) — giữ nguyên từng ký tự.
2. Mọi JavaScript — giữ nguyên logic. Mọi `id`, `name` của form, và mọi class được JS
   `getElementById`/`querySelector`/`querySelectorAll`/template string tham chiếu — TRA JS TRƯỚC khi đổi.
3. Route, href, action, method của form.
4. Nội dung chữ / copy tiếng Việt (chỉ được sửa lỗi hiển thị, không viết lại).
5. Emoji icon hiện có (giọng thương hiệu nội bộ, giữ theo chế độ preserve).

**PHẢI đổi:**
1. Nền tối / gradient tím / Tailwind CDN → theme mera-ui (bỏ Tailwind CDN nếu trang không còn dùng
   class Tailwind sau chuyển đổi; nếu JS render class Tailwind động thì GIỮ và chỉ đổi màu cho khớp token).
2. Font khác → Inter.
3. Màu accent lệch → #1456f0 cho hành động chính; trạng thái dùng token success/warn/danger/info.
4. Bo góc lệch hệ → card 12px, control 8px, badge pill.
5. Shadow đen thuần → token `--mr-shadow-*` hoặc bỏ.
6. Em-dash (—, –) trong chuỗi hiển thị → dấu gạch nối thường hoặc viết lại câu
   (ngoại lệ: "—" dùng làm placeholder ô trống dữ liệu thì giữ).
7. Inline style trùng vai trò với class có sẵn → dùng class mera-ui.
8. Màu hardcode trong `<style>` trang → `var(--mr-*)` để dark mode hoạt động.

**Cách làm an toàn với trang nhiều JS:** giữ nguyên cấu trúc DOM và id; chỉ thay khối `<style>` bằng
link mera-ui.css + style riêng còn thiếu (viết đè bằng token var(--mr-*)); đổi class ở wrapper
thuần trình bày. Trang rủi ro cao thì đổi tối thiểu: nền, font, màu accent, bảng, nút.

## Dark mode

- Token đổi qua `[data-theme="dark"]` trên `<html>`; script `themeInit` chống FOUC, `mrToggleTheme()`
  lưu lựa chọn vào localStorage, mặc định theo `prefers-color-scheme`.
- Khi viết style riêng cho trang, chỉ dùng token; nếu bắt buộc hardcode màu sáng, thêm override
  `[data-theme="dark"] ...`.
