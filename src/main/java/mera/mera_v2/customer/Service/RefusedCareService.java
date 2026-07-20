package mera.mera_v2.customer.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mera.mera_v2.customer.DTO.PhoneMatchView;
import mera.mera_v2.customer.DTO.RefusedCareUploadResult;
import mera.mera_v2.repository.ProblemCustomerRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Quản lý danh sách khách "Từ Chối Chăm" (Nhóm D) — nạp từ file Excel chứa SĐT.
 *
 * <p>Luồng: đọc SĐT trong file → khớp 9 số cuối sang mã khách (customer_phone_numbers) →
 * ghi vào bảng {@code refused_care} theo kiểu <b>thay thế toàn bộ</b> (danh sách = file mới nhất).
 * Sau khi ghi thì xoá cache Số thả nổi để nhóm D hiện ngay.</p>
 */
@Service
public class RefusedCareService {

    private static final Logger log = LoggerFactory.getLogger(RefusedCareService.class);
    private static final String DEFAULT_NOTE = "Từ chối chăm";
    private static final int MATCH_BATCH = 500;

    // Lý do SĐT không khớp (hiện trong file tải xuống)
    private static final String REASON_INVALID = "SĐT không hợp lệ (chỉ có 6–8 chữ số)";
    private static final String REASON_ORDER_NO_CUSTOMER = "Có đơn trong hệ thống nhưng đơn chưa gắn mã khách";
    private static final String REASON_NOT_FOUND = "Không tìm thấy trong hệ thống (không có khách/đơn nào dùng 9 số cuối này)";

    /** Bảng tự tạo (ddl-auto=none). */
    private static final String DDL = """
        CREATE TABLE IF NOT EXISTS refused_care (
            customer_id  VARCHAR(64)  NOT NULL PRIMARY KEY,
            phone        VARCHAR(32),
            note         VARCHAR(500),
            uploaded_at  DATETIME
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ProblemCustomerRepository repository;
    private final ProblemCustomerCache cache;

    public RefusedCareService(JdbcTemplate jdbcTemplate,
                              ProblemCustomerRepository repository,
                              ProblemCustomerCache cache) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
        this.cache = cache;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSchema() {
        try {
            jdbcTemplate.execute(DDL);
            log.info("Bảng refused_care (Từ chối chăm) đã sẵn sàng.");
        } catch (Exception e) {
            log.error("Không tạo được bảng refused_care: {}", e.getMessage());
        }
    }

    /** Số khách đang trong danh sách Từ chối chăm. */
    public int count() {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refused_care", Integer.class);
            return c != null ? c : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Đọc file Excel → khớp SĐT → THÊM/GỘP vào danh sách TCC (không xoá khách cũ). */
    @Transactional
    public RefusedCareUploadResult importFromExcel(MultipartFile file) {
        RefusedCareUploadResult r = new RefusedCareUploadResult();
        if (file == null || file.isEmpty()) {
            r.setErrorMessage("Chưa chọn file hoặc file rỗng.");
            return r;
        }
        // 1) Đọc SĐT trong file (9 số cuối, distinct) + giữ dạng gốc để lưu hiển thị
        Set<String> phone9s = new LinkedHashSet<>();
        Map<String, String> p9ToRaw = new HashMap<>();
        Set<String> invalidPhones = new LinkedHashSet<>();
        try {
            extractPhones(file, phone9s, p9ToRaw, invalidPhones);
        } catch (Exception e) {
            log.error("Lỗi đọc file Excel Từ chối chăm", e);
            r.setErrorMessage("Không đọc được file Excel: " + e.getMessage());
            return r;
        }
        r.setTotalPhones(phone9s.size());
        if (phone9s.isEmpty()) {
            r.setErrorMessage("Không tìm thấy số điện thoại hợp lệ nào trong file (SĐT cần ≥ 9 chữ số).");
            return r;
        }

        // 2) Khớp 9 số cuối → mã khách (theo lô để tránh IN quá dài)
        Map<String, String> p9ToCustomer = new HashMap<>();
        List<String> all = new ArrayList<>(phone9s);
        for (int i = 0; i < all.size(); i += MATCH_BATCH) {
            List<String> batch = all.subList(i, Math.min(i + MATCH_BATCH, all.size()));
            for (PhoneMatchView v : repository.matchCustomerIdsByPhone9(batch)) {
                if (v.getCustomerId() != null) p9ToCustomer.put(v.getP9(), v.getCustomerId());
            }
        }
        r.setMatchedPhones(p9ToCustomer.size());

        // SĐT không khớp (để đối chiếu) + lý do cụ thể (để tải xuống)
        List<String> unmatchedP9s = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (String p9 : phone9s) {
            if (!p9ToCustomer.containsKey(p9)) {
                unmatchedP9s.add(p9);
                unmatched.add(p9ToRaw.getOrDefault(p9, p9));
            }
        }
        r.setUnmatchedPhones(unmatched.size());
        r.setUnmatchedSample(unmatched.size() > 20 ? unmatched.subList(0, 20) : unmatched);
        r.setUnmatchedDetails(buildUnmatchedDetails(unmatchedP9s, p9ToRaw, invalidPhones));

        // 3) Khử trùng theo mã khách (1 khách nhiều SĐT trong file)
        Map<String, String> customerToPhone = new LinkedHashMap<>();
        p9ToCustomer.forEach((p9, cid) -> customerToPhone.putIfAbsent(cid, p9ToRaw.getOrDefault(p9, p9)));

        // 4) THÊM/GỘP vào danh sách (upsert) — không xoá khách cũ
        LocalDateTime now = LocalDateTime.now();
        List<Map.Entry<String, String>> entries = new ArrayList<>(customerToPhone.entrySet());
        jdbcTemplate.batchUpdate(
                "INSERT INTO refused_care (customer_id, phone, note, uploaded_at) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE phone = VALUES(phone), note = VALUES(note), uploaded_at = VALUES(uploaded_at)",
                entries, entries.size(),
                (ps, e) -> {
                    ps.setString(1, e.getKey());
                    ps.setString(2, e.getValue());
                    ps.setString(3, DEFAULT_NOTE);
                    ps.setTimestamp(4, java.sql.Timestamp.valueOf(now));
                });
        r.setSavedCustomers(customerToPhone.size());
        r.setTotalAfter(count());
        r.setSuccess(true);

        // 5) Xoá cache để nhóm D hiện ngay
        cache.clear();
        log.info("Cập nhật Từ chối chăm (gộp): {} SĐT trong file, {} khớp, {} thêm/cập nhật, tổng {}.",
                r.getTotalPhones(), r.getMatchedPhones(), r.getSavedCustomers(), r.getTotalAfter());
        return r;
    }

    /** Xoá toàn bộ danh sách Từ chối chăm (thao tác chủ ý). */
    @Transactional
    public void clearAll() {
        jdbcTemplate.update("DELETE FROM refused_care");
        cache.clear();
        log.info("Đã xoá toàn bộ danh sách Từ chối chăm.");
    }

    /** Sinh file Excel mẫu: 1 cột "Số điện thoại" + vài dòng ví dụ. */
    public byte[] sampleWorkbook() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("TuChoiCham");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Số điện thoại");
            String[] examples = {"0901234567", "0912345678", "0987654321"};
            for (int i = 0; i < examples.length; i++) {
                // Lưu dạng chuỗi để giữ số 0 đầu
                sheet.createRow(i + 1).createCell(0).setCellValue(examples[i]);
            }
            sheet.setColumnWidth(0, 6000);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không tạo được file mẫu: " + e.getMessage(), e);
        }
    }

    /**
     * Gán lý do cụ thể cho từng SĐT không khớp: số không hợp lệ (6–8 chữ số) lên đầu,
     * rồi phân biệt "có đơn nhưng đơn chưa gắn mã khách" với "không có trong hệ thống".
     */
    private List<RefusedCareUploadResult.UnmatchedDetail> buildUnmatchedDetails(
            List<String> unmatchedP9s, Map<String, String> p9ToRaw, Set<String> invalidPhones) {
        List<RefusedCareUploadResult.UnmatchedDetail> details = new ArrayList<>();
        for (String raw : invalidPhones) {
            details.add(new RefusedCareUploadResult.UnmatchedDetail(raw, REASON_INVALID));
        }
        if (!unmatchedP9s.isEmpty()) {
            Set<String> hasOrderNoCustomer = new HashSet<>();
            for (int i = 0; i < unmatchedP9s.size(); i += MATCH_BATCH) {
                List<String> batch = unmatchedP9s.subList(i, Math.min(i + MATCH_BATCH, unmatchedP9s.size()));
                try {
                    hasOrderNoCustomer.addAll(repository.findPhone9HavingOrdersWithoutCustomer(batch));
                } catch (Exception e) {
                    log.warn("Không phân loại được lý do 'có đơn chưa gắn khách' cho lô SĐT: {}", e.getMessage());
                }
            }
            for (String p9 : unmatchedP9s) {
                String reason = hasOrderNoCustomer.contains(p9) ? REASON_ORDER_NO_CUSTOMER : REASON_NOT_FOUND;
                details.add(new RefusedCareUploadResult.UnmatchedDetail(p9ToRaw.getOrDefault(p9, p9), reason));
            }
        }
        return details;
    }

    /** Sinh file Excel danh sách SĐT không khớp kèm lý do (STT | Số điện thoại | Lý do). */
    public byte[] unmatchedWorkbook(List<RefusedCareUploadResult.UnmatchedDetail> details) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("KhongKhop");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("STT");
            header.createCell(1).setCellValue("Số điện thoại");
            header.createCell(2).setCellValue("Lý do");
            int rowIdx = 1;
            for (RefusedCareUploadResult.UnmatchedDetail d : details) {
                if (d == null || d.getPhone() == null || d.getPhone().isBlank()) continue;
                Row row = sheet.createRow(rowIdx);
                row.createCell(0).setCellValue(rowIdx);
                // Lưu dạng chuỗi để giữ số 0 đầu
                row.createCell(1).setCellValue(d.getPhone());
                row.createCell(2).setCellValue(d.getReason() != null ? d.getReason() : "");
                rowIdx++;
            }
            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 6000);
            sheet.setColumnWidth(2, 16000);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không tạo được file SĐT không khớp: " + e.getMessage(), e);
        }
    }

    /**
     * Duyệt mọi ô trong các sheet, gom các số ≥ 9 chữ số (lấy 9 số cuối làm khoá).
     * Ô có 6–8 chữ số được coi là SĐT "gãy" → gom vào {@code invalidPhones} để báo lý do.
     */
    private void extractPhones(MultipartFile file, Set<String> phone9s, Map<String, String> p9ToRaw,
                               Set<String> invalidPhones) throws Exception {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            DataFormatter fmt = new DataFormatter();
            int sheets = wb.getNumberOfSheets();
            for (int s = 0; s < sheets; s++) {
                Sheet sheet = wb.getSheetAt(s);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String raw;
                        if (cell.getCellType() == CellType.NUMERIC) {
                            raw = new BigDecimal(cell.getNumericCellValue()).toPlainString();
                        } else {
                            raw = fmt.formatCellValue(cell);
                        }
                        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
                        if (digits.length() >= 9 && digits.length() <= 15) {
                            String p9 = digits.substring(digits.length() - 9);
                            phone9s.add(p9);
                            p9ToRaw.putIfAbsent(p9, digits);
                        } else if (digits.length() >= 6 && digits.length() <= 8) {
                            invalidPhones.add(digits);
                        }
                    }
                }
            }
        }
    }
}
