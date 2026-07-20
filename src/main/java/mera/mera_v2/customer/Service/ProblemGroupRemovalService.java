package mera.mera_v2.customer.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xóa 1 khách khỏi 1 nhóm cảnh báo trên trang admin {@code /khach-hang-canh-bao}.
 *
 * <p><b>Nhóm A/B/C</b> được tính động từ dữ liệu khách (không có "dòng" để xóa) nên việc xóa được
 * lưu bền vào bảng loại trừ {@code problem_customer_group_removed} theo cặp (customer_id, grp). Trang
 * đọc facts sẽ bỏ qua các cặp này (xem {@link ProblemCustomerService}), nên khách không hiện lại ở
 * nhóm đó dù facts được refresh lại mỗi 5 phút. Muốn cho khách hiện lại: xóa dòng tương ứng trong
 * bảng loại trừ.</p>
 *
 * <p><b>Nhóm D</b> (Từ chối chăm) có nguồn thật là bảng {@code refused_care} nên xóa nhóm D = xóa
 * khỏi {@code refused_care}; đồng thời gỡ cờ {@code refused} ngay trong facts để mất khỏi nhóm D tức
 * thì (không phải chờ refresh). Khách sau đó có thể quay lại A/B/C nếu đủ điều kiện.</p>
 */
@Service
public class ProblemGroupRemovalService {

    private static final Logger log = LoggerFactory.getLogger(ProblemGroupRemovalService.class);

    /** Bảng tự tạo (ddl-auto=none, Flyway không chạy). */
    private static final String DDL = """
        CREATE TABLE IF NOT EXISTS problem_customer_group_removed (
            customer_id  VARCHAR(64) NOT NULL,
            grp          CHAR(1)     NOT NULL,
            removed_at   DATETIME,
            PRIMARY KEY (customer_id, grp)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ProblemCustomerCache cache;

    public ProblemGroupRemovalService(JdbcTemplate jdbcTemplate, ProblemCustomerCache cache) {
        this.jdbcTemplate = jdbcTemplate;
        this.cache = cache;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSchema() {
        try {
            jdbcTemplate.execute(DDL);
            log.info("Bảng problem_customer_group_removed (loại trừ nhóm A/B/C) đã sẵn sàng.");
        } catch (Exception e) {
            log.error("Không tạo được bảng problem_customer_group_removed: {}", e.getMessage());
        }
    }

    /**
     * Xóa khách khỏi 1 nhóm. A/B/C → ghi bản loại trừ; D → xóa khỏi refused_care.
     *
     * @param customerId mã khách hàng
     * @param group      nhóm cần xóa: A | B | C | D (không phân biệt hoa/thường)
     * @throws IllegalArgumentException nếu thiếu mã khách hoặc nhóm không hợp lệ
     */
    @Transactional
    public void removeGroup(String customerId, String group) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã khách hàng.");
        }
        String g = (group == null) ? "" : group.trim().toUpperCase();
        switch (g) {
            case "A", "B", "C" -> jdbcTemplate.update(
                    "INSERT INTO problem_customer_group_removed (customer_id, grp, removed_at) VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE removed_at = VALUES(removed_at)",
                    customerId, g, Timestamp.valueOf(LocalDateTime.now()));
            case "D" -> {
                jdbcTemplate.update("DELETE FROM refused_care WHERE customer_id = ?", customerId);
                // Gỡ cờ ngay trong facts để mất khỏi nhóm D tức thì (refresh nền sẽ đối soát lại sau).
                jdbcTemplate.update(
                        "UPDATE problem_customer_facts SET refused = 0, refused_uploaded_at = NULL WHERE customer_id = ?",
                        customerId);
            }
            default -> throw new IllegalArgumentException("Nhóm không hợp lệ: " + group);
        }
        cache.clear();
        log.info("Đã xóa khách {} khỏi nhóm {}.", customerId, g);
    }
}
