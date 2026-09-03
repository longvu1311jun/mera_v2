package mera.mera_v2.ltkach;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Bảo đảm các cột LT (liệu trình) mà entity/JPA cần đang có trong pos_db.
 *
 * <p><b>Vì sao cần:</b> Flyway không chạy trong dự án; schema LT trước đây được sửa tay trực tiếp
 * trên DB (migration V11–V14 đã bị xoá khỏi repo). Khi DB được khôi phục từ bản dump cũ (sau sự cố
 * TrueNAS 09/2026), bảng {@code customers} không còn {@code lt_count}, bảng {@code orders} không còn
 * {@code lt_count_snapshot} → mọi query JPA đụng entity Customer/Order chết với
 * <i>Unknown column 'c1_0.lt_count'</i>, kéo theo đồng bộ đơn hàng và webhook đều hỏng.</p>
 *
 * <p><b>Cách làm:</b> tra {@code information_schema} trước, chỉ ALTER khi thực sự thiếu (ALTER dù
 * IF NOT EXISTS vẫn xin metadata lock — xem {@code PhoneIndexInitializer}). ADD COLUMN dùng
 * {@code ALGORITHM=INSTANT} (MariaDB 10.3.2+, chỉ đổi metadata, không rebuild bảng orders 3 GB);
 * nếu server không hỗ trợ thì fallback ALTER thường.</p>
 *
 * <p><b>Không</b> đổi kiểu {@code orders.lt_type} (VARCHAR trong dump cũ → TINYINT(1)): MODIFY COLUMN
 * rebuild toàn bộ bảng orders, phải làm tay trong giờ bảo trì. Code LT đã đọc được cả hai kiểu
 * ({@link LtCalculationService#toBoolean}).</p>
 */
@Component
@Order(1)
public class LtSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(LtSchemaInitializer.class);

    /** {table, column, definition} — chỉ ADD COLUMN, không DROP/MODIFY gì trên DB production. */
    private static final String[][] COLUMNS = {
        {"customers", "lt_count", "INT NOT NULL DEFAULT 0 COMMENT 'So don combo thanh cong (LT) — LtCalculationService'"},
        {"orders", "lt_count_snapshot", "INT DEFAULT NULL COMMENT 'customers.lt_count tai thoi diem don duoc tao — bao cao LT theo thang'"},
    };

    private final JdbcTemplate jdbcTemplate;

    public LtSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Chạy đồng bộ ngay khi app sẵn sàng: ADD COLUMN instant chỉ mất mili-giây, và các job/sync cần cột này. */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureSchema() {
        for (String[] c : COLUMNS) {
            String table = c[0], column = c[1], definition = c[2];
            try {
                if (columnExists(table, column)) {
                    continue;
                }
                log.warn("Thiếu cột {}.{} (DB đang ở schema cũ) — đang thêm...", table, column);
                addColumn(table, column, definition);
                log.info("Đã thêm cột {}.{}.", table, column);
            } catch (Exception e) {
                log.error("Không thêm được cột {}.{}: {} — sync/webhook sẽ lỗi 'Unknown column' cho tới khi thêm tay.",
                        table, column, e.getMessage());
            }
        }
        warnIfLtTypeNotBoolean();
    }

    private void addColumn(String table, String column, String definition) {
        String base = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
        try {
            jdbcTemplate.execute(base + ", ALGORITHM=INSTANT");
        } catch (Exception instantNotSupported) {
            log.info("ALGORITHM=INSTANT không dùng được ({}), thử ALTER thường.", instantNotSupported.getMessage());
            jdbcTemplate.execute(base);
        }
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = DATABASE() "
                        + "AND table_name = ? AND column_name = ?", Integer.class, table, column);
        return n != null && n > 0;
    }

    private void warnIfLtTypeNotBoolean() {
        try {
            String type = jdbcTemplate.queryForObject(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE table_schema = DATABASE() "
                            + "AND table_name = 'orders' AND column_name = 'lt_type'", String.class);
            if (type != null && !type.toLowerCase().startsWith("tinyint")) {
                log.warn("orders.lt_type đang là {} (schema cũ). Code vẫn chạy được, nhưng nên đổi sang TINYINT(1) "
                        + "trong giờ bảo trì: ALTER TABLE orders MODIFY COLUMN lt_type TINYINT(1) DEFAULT 0 "
                        + "(rebuild bảng orders, cần ~2x dung lượng bảng trống trên đĩa).", type);
            }
        } catch (Exception e) {
            log.debug("Không đọc được kiểu orders.lt_type: {}", e.getMessage());
        }
    }
}
