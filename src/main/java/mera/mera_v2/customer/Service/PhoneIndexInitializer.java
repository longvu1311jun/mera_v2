package mera.mera_v2.customer.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tạo cột "9 số cuối SĐT" ({@code phone9}) đã đánh index trên {@code orders} và
 * {@code customer_phone_numbers} để trang "Số thả nổi" khớp đơn theo SĐT bằng index thay vì
 * chạy {@code RIGHT(REGEXP_REPLACE(...))} full-scan mỗi request (nguyên nhân query treo 90s).
 *
 * <p>Cột là <b>VIRTUAL GENERATED</b>: {@code ADD COLUMN} chỉ đổi metadata (tức thì, không rebuild
 * bảng), còn giá trị được vật chất hoá trong index. Vì vậy trên bảng {@code orders} lớn đang chạy
 * production, thao tác build index chạy online (khoá tối thiểu) và chỉ tốn 1 lần.</p>
 *
 * <p>Chạy 1 lần lúc khởi động trên luồng nền (không chặn startup). <b>Quan trọng — thứ tự:</b>
 * tạo CẢ HAI cột trước (thao tác metadata tức thì), rồi mới build index. Nếu build index bảng
 * {@code orders} (lớn, có thể lâu vài phút) chạy trước khi tạo cột {@code customer_phone_numbers.phone9}
 * thì các query dùng {@code cpn.phone9} sẽ lỗi <i>Unknown column</i> suốt thời gian build index.
 * Idempotent nhờ {@code IF NOT EXISTS}, nên khởi động lại nhiều lần vô hại. Xem
 * {@code ProblemCustomerRepository} — các query khớp SĐT join trực tiếp trên {@code phone9}.</p>
 */
@Component
public class PhoneIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(PhoneIndexInitializer.class);

    /**
     * Cột {@code phone9} — tạo TRƯỚC, bắt buộc để query chạy. Giá trị khớp đúng biểu thức cũ:
     *  - orders: lọc ký tự số rồi lấy 9 số cuối, NULL nếu &lt; 9 chữ số.
     *  - customer_phone_numbers: phone_number vốn đã là số nên chỉ lấy 9 số cuối.
     */
    private static final String[] COLUMN_DDL = {
        """
        ALTER TABLE orders
            ADD COLUMN IF NOT EXISTS phone9 CHAR(9)
                GENERATED ALWAYS AS (
                    CASE WHEN customer_phone IS NOT NULL
                          AND LENGTH(REGEXP_REPLACE(customer_phone, '[^0-9]', '')) >= 9
                         THEN RIGHT(REGEXP_REPLACE(customer_phone, '[^0-9]', ''), 9)
                    END
                ) VIRTUAL
        """,
        """
        ALTER TABLE customer_phone_numbers
            ADD COLUMN IF NOT EXISTS phone9 CHAR(9)
                GENERATED ALWAYS AS (
                    CASE WHEN LENGTH(phone_number) >= 9 THEN RIGHT(phone_number, 9) END
                ) VIRTUAL
        """
    };

    /**
     * Index build SAU (chỉ để tăng tốc; query vẫn đúng nếu index chưa xong):
     *  - phone9 trên orders / customer_phone_numbers (khớp SĐT).
     *  - customer_notes.updated_at để incremental facts quét ghi chú vừa đổi (xem ProblemCustomerFactsService).
     */
    private static final String[] INDEX_DDL = {
        "ALTER TABLE orders ADD INDEX IF NOT EXISTS idx_orders_phone9 (phone9)",
        "ALTER TABLE customer_phone_numbers ADD INDEX IF NOT EXISTS idx_cpn_phone9 (phone9)",
        "ALTER TABLE customer_notes ADD INDEX IF NOT EXISTS idx_cn_updated (updated_at)"
    };

    private final JdbcTemplate jdbcTemplate;

    public PhoneIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSchema() {
        Thread t = new Thread(this::run, "phone9-index-init");
        t.setDaemon(true);
        t.start();
    }

    private void run() {
        // 1) Cột trước — mỗi lệnh độc lập, log rõ để chẩn đoán nếu DB không hỗ trợ generated column.
        int cols = exec(COLUMN_DDL, "cột");
        if (cols == COLUMN_DDL.length) {
            log.info("Cột phone9 (khớp SĐT 9 số) trên orders & customer_phone_numbers đã sẵn sàng.");
        } else {
            log.error("phone9: chỉ tạo được {}/{} cột — trang Số thả nổi sẽ lỗi 'Unknown column' cho đến khi tạo đủ.",
                    cols, COLUMN_DDL.length);
            return; // Không build index nếu thiếu cột.
        }
        // 2) Index sau — best-effort, có thể lâu trên bảng orders lớn.
        int idx = exec(INDEX_DDL, "index");
        log.info("Index phone9: {}/{} đã sẵn sàng (query dùng được ngay cả khi index chưa xong).",
                idx, INDEX_DDL.length);
    }

    /** Chạy từng câu DDL độc lập, trả về số câu thành công. */
    private int exec(String[] statements, String kind) {
        int ok = 0;
        for (String ddl : statements) {
            try {
                jdbcTemplate.execute(ddl);
                ok++;
            } catch (Exception e) {
                log.error("Không tạo được {} phone9 [{}]: {}",
                        kind, ddl.strip().split("\\s+", 4)[2], e.getMessage());
            }
        }
        return ok;
    }
}
