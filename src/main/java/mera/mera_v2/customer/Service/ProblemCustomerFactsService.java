package mera.mera_v2.customer.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tính sẵn "facts" cho trang "Số thả nổi" vào bảng {@code problem_customer_facts}.
 *
 * <p><b>Vì sao:</b> query danh sách gốc JOIN customers/orders/customer_notes/customer_phone_numbers
 * + đếm đơn khớp mã KH lẫn 9 số cuối SĐT → rất nặng, từng treo 90s. Thay vì tính mỗi request, ta
 * lưu facts thô theo TỪNG khách; trang chỉ đọc/phân trang trên bảng phẳng đã đánh index.</p>
 *
 * <p><b>Facts KHÔNG phụ thuộc ngưỡng lọc</b> (chỉ số liệu thô). Phân nhóm A/B/C tính lúc ĐỌC theo
 * ngưỡng + {@code now} (ProblemCustomerService) nên chuyển nhóm do thời gian trôi (cửa sổ 72h nhóm
 * B, mốc stale nhóm C) luôn đúng mà không cần refresh. Nhóm D (Từ chối chăm) nằm chung bảng qua cột
 * {@code refused} / {@code refused_uploaded_at}.</p>
 *
 * <p><b>Chiến lược cập nhật ở quy mô ~300k khách:</b></p>
 * <ul>
 *   <li><b>Incremental mỗi 5 phút:</b> chỉ quét khách có ĐƠN ({@code orders.updated_at}) hoặc GHI CHÚ
 *       ({@code customer_notes.updated_at}) vừa đổi từ mốc trước, cộng toàn bộ TCC; recompute facts
 *       cho riêng tập nhỏ đó (UPSERT/DELETE theo lô). Khách lên đơn active tự rời danh sách ~5 phút.</li>
 *   <li><b>Full reconcile ban đêm:</b> tính lại toàn bộ để sửa lệch (order-sync ghi updated_at quá
 *       khứ có thể lọt incremental, webhook sót, phone re-match).</li>
 * </ul>
 *
 * <p>Phụ thuộc cột {@code phone9} (xem {@link PhoneIndexInitializer}) nên chạy trễ vài giây sau boot.
 * Sau mỗi lần cập nhật gọi {@link ProblemCustomerCache#clear()}.</p>
 */
@Service
public class ProblemCustomerFactsService {

    private static final Logger log = LoggerFactory.getLogger(ProblemCustomerFactsService.class);

    /** Trần thời gian 1 lần refresh (giây) — tránh treo vô hạn khi DB quá tải. */
    private static final int REFRESH_TIMEOUT_S = 300;
    /** Kích thước lô id cho recompute incremental (tránh IN quá dài). */
    private static final int BATCH = 500;
    /** Đệm lùi mốc quét để không sót bản ghi do lệch đồng hồ / giao dịch đang bay (phút). */
    private static final int WATERMARK_BUFFER_MIN = 10;

    private static final String DDL_FACTS = """
        CREATE TABLE IF NOT EXISTS problem_customer_facts (
            customer_id          VARCHAR(64)  NOT NULL PRIMARY KEY,
            name                 VARCHAR(255),
            phone                VARCHAR(32),
            inserted_at          DATETIME,
            order_count          INT      NOT NULL DEFAULT 0,
            succeed_order_count  INT      NOT NULL DEFAULT 0,
            active_order_count   INT      NOT NULL DEFAULT 0,
            note_count           INT      NOT NULL DEFAULT 0,
            last_note_at         DATETIME,
            has_received         TINYINT  NOT NULL DEFAULT 0,
            last_received_at     DATETIME,
            last_order_at        DATETIME,
            refused              TINYINT  NOT NULL DEFAULT 0,
            refused_uploaded_at  DATETIME,
            updated_at           DATETIME,
            INDEX idx_pcf_note (note_count),
            INDEX idx_pcf_inserted (inserted_at),
            INDEX idx_pcf_received (has_received, last_received_at),
            INDEX idx_pcf_refused (refused)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    /** Bảng đè cột cho bảng facts đã tồn tại từ phiên bản trước (idempotent). */
    private static final String[] DDL_FACTS_ALTER = {
        "ALTER TABLE problem_customer_facts ADD COLUMN IF NOT EXISTS refused TINYINT NOT NULL DEFAULT 0",
        "ALTER TABLE problem_customer_facts ADD COLUMN IF NOT EXISTS refused_uploaded_at DATETIME",
        "ALTER TABLE problem_customer_facts ADD INDEX IF NOT EXISTS idx_pcf_refused (refused)"
    };

    private static final String DDL_STATE = """
        CREATE TABLE IF NOT EXISTS problem_customer_facts_state (
            id                   TINYINT  NOT NULL PRIMARY KEY,
            last_incremental_at  DATETIME,
            last_full_at         DATETIME
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    // Danh sách cột INSERT (dùng chung full + scoped).
    private static final String INSERT_COLS =
        "INSERT INTO problem_customer_facts "
        + "(customer_id, name, phone, inserted_at, order_count, succeed_order_count, active_order_count, "
        + "note_count, last_note_at, has_received, last_received_at, last_order_at, refused, "
        + "refused_uploaded_at, updated_at) ";

    // 14 cột facts (không kèm updated_at). FULL nối thêm ", ?" làm updated_at; incremental set updated_at ở Java.
    private static final String SELECT_EXPR = """
        SELECT c.id, c.name, p.phone_number, c.inserted_at,
               (COALESCE(tot.cnt, 0) + COALESCE(oph.total_cnt, 0)),
               (COALESCE(rcv.cnt, 0) + COALESCE(oph.received_cnt, 0)),
               (COALESCE(act.cnt, 0) + COALESCE(oph.active_cnt, 0)),
               COALESCE(n.note_count, 0),
               n.last_note_at,
               CASE WHEN COALESCE(rcv.cnt, 0) > 0 OR COALESCE(oph.has_received, 0) > 0 THEN 1 ELSE 0 END,
               GREATEST(COALESCE(rcv.last_received_at, oph.last_received_at),
                        COALESCE(oph.last_received_at, rcv.last_received_at)),
               c.last_order_at,
               CASE WHEN rc.customer_id IS NOT NULL THEN 1 ELSE 0 END,
               rc.uploaded_at
        """;

    /**
     * Điều kiện ứng viên vào facts: (KHÔNG có đơn active VÀ có ghi chú/đã nhận) HOẶC là khách TCC.
     * TCC được đưa vào bất kể active (theo luật nhóm D).
     */
    private static final String CANDIDATE_COND = """
        ( ( (COALESCE(act.cnt, 0) + COALESCE(oph.active_cnt, 0)) = 0
            AND (COALESCE(n.note_count, 0) >= 1 OR COALESCE(rcv.cnt, 0) > 0 OR COALESCE(oph.has_received, 0) > 0) )
          OR rc.customer_id IS NOT NULL )
        """;

    // Derived tables cho FULL (không giới hạn id).
    private static final String FROM_FULL = """
        FROM customers c
        LEFT JOIN (
            SELECT cn.customer_id, COUNT(*) AS note_count, MAX(cn.created_at) AS last_note_at
            FROM customer_notes cn WHERE cn.removed_at IS NULL
            GROUP BY cn.customer_id
        ) n ON n.customer_id = c.id
        LEFT JOIN (
            SELECT o.customer_id, COUNT(*) AS cnt FROM orders o
            WHERE o.status IN (11, 1, 8, 9, 2) GROUP BY o.customer_id
        ) act ON act.customer_id = c.id
        LEFT JOIN (
            SELECT o.customer_id, COUNT(*) AS cnt, MAX(o.inserted_at) AS last_received_at
            FROM orders o WHERE o.status IN (3, 16) GROUP BY o.customer_id
        ) rcv ON rcv.customer_id = c.id
        LEFT JOIN (
            SELECT o.customer_id, COUNT(*) AS cnt FROM orders o GROUP BY o.customer_id
        ) tot ON tot.customer_id = c.id
        LEFT JOIN (
            SELECT cpn.customer_id,
                   SUBSTRING_INDEX(GROUP_CONCAT(cpn.phone_number ORDER BY cpn.is_primary DESC, cpn.id ASC), ',', 1) AS phone_number
            FROM customer_phone_numbers cpn GROUP BY cpn.customer_id
        ) p ON p.customer_id = c.id
        LEFT JOIN (
            SELECT cpn.customer_id,
                   MAX(CASE WHEN op.status IN (3, 16) THEN 1 ELSE 0 END) AS has_received,
                   MAX(CASE WHEN op.status IN (3, 16) THEN op.inserted_at END) AS last_received_at,
                   SUM(CASE WHEN op.status IN (11, 1, 8, 9, 2) AND NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS active_cnt,
                   SUM(CASE WHEN op.status IN (3, 16) AND NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS received_cnt,
                   SUM(CASE WHEN NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS total_cnt
            FROM customer_phone_numbers cpn
            JOIN orders op ON op.phone9 = cpn.phone9
            WHERE cpn.phone9 IS NOT NULL
            GROUP BY cpn.customer_id
        ) oph ON oph.customer_id = c.id
        LEFT JOIN refused_care rc ON rc.customer_id = c.id
        """;

    // Derived tables cho SCOPED (mỗi subquery giới hạn theo :ids để mỗi lô cực nhẹ). Phải đồng bộ với FROM_FULL.
    private static final String FROM_SCOPED = """
        FROM customers c
        LEFT JOIN (
            SELECT cn.customer_id, COUNT(*) AS note_count, MAX(cn.created_at) AS last_note_at
            FROM customer_notes cn WHERE cn.removed_at IS NULL AND cn.customer_id IN (:ids)
            GROUP BY cn.customer_id
        ) n ON n.customer_id = c.id
        LEFT JOIN (
            SELECT o.customer_id, COUNT(*) AS cnt FROM orders o
            WHERE o.status IN (11, 1, 8, 9, 2) AND o.customer_id IN (:ids) GROUP BY o.customer_id
        ) act ON act.customer_id = c.id
        LEFT JOIN (
            SELECT o.customer_id, COUNT(*) AS cnt, MAX(o.inserted_at) AS last_received_at
            FROM orders o WHERE o.status IN (3, 16) AND o.customer_id IN (:ids) GROUP BY o.customer_id
        ) rcv ON rcv.customer_id = c.id
        LEFT JOIN (
            SELECT o.customer_id, COUNT(*) AS cnt FROM orders o
            WHERE o.customer_id IN (:ids) GROUP BY o.customer_id
        ) tot ON tot.customer_id = c.id
        LEFT JOIN (
            SELECT cpn.customer_id,
                   SUBSTRING_INDEX(GROUP_CONCAT(cpn.phone_number ORDER BY cpn.is_primary DESC, cpn.id ASC), ',', 1) AS phone_number
            FROM customer_phone_numbers cpn WHERE cpn.customer_id IN (:ids) GROUP BY cpn.customer_id
        ) p ON p.customer_id = c.id
        LEFT JOIN (
            SELECT cpn.customer_id,
                   MAX(CASE WHEN op.status IN (3, 16) THEN 1 ELSE 0 END) AS has_received,
                   MAX(CASE WHEN op.status IN (3, 16) THEN op.inserted_at END) AS last_received_at,
                   SUM(CASE WHEN op.status IN (11, 1, 8, 9, 2) AND NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS active_cnt,
                   SUM(CASE WHEN op.status IN (3, 16) AND NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS received_cnt,
                   SUM(CASE WHEN NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS total_cnt
            FROM customer_phone_numbers cpn
            JOIN orders op ON op.phone9 = cpn.phone9
            WHERE cpn.phone9 IS NOT NULL AND cpn.customer_id IN (:ids)
            GROUP BY cpn.customer_id
        ) oph ON oph.customer_id = c.id
        LEFT JOIN refused_care rc ON rc.customer_id = c.id
        """;

    // ĐỌC bằng SELECT thường (autocommit → non-locking consistent read), KHÔNG dùng INSERT...SELECT:
    // MariaDB đặt shared next-key lock lên bảng nguồn của INSERT...SELECT (kể cả READ COMMITTED),
    // từng block ghi orders/customer_notes của sync → Lock wait timeout (1205).
    private static final String FULL_SELECT_SQL =
        SELECT_EXPR + FROM_FULL + " WHERE " + CANDIDATE_COND;

    private static final String SCOPED_SELECT_SQL =
        SELECT_EXPR + FROM_SCOPED + " WHERE c.id IN (:ids) AND " + CANDIDATE_COND;

    // GHI: INSERT literal theo lô các dòng đã tính sẵn (15 cột = 14 + updated_at). Chỉ đụng problem_customer_facts.
    private static final String FACTS_BATCH_INSERT_SQL =
        INSERT_COLS + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate refreshJdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final TransactionTemplate txTemplate;
    private final ProblemCustomerCache cache;

    private final ExecutorService pool = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "pc-facts-refresh");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ProblemCustomerFactsService(JdbcTemplate jdbcTemplate,
                                       PlatformTransactionManager txManager,
                                       ProblemCustomerCache cache) {
        DataSource ds = jdbcTemplate.getDataSource();
        this.refreshJdbc = new JdbcTemplate(ds);
        this.refreshJdbc.setQueryTimeout(REFRESH_TIMEOUT_S);
        this.namedJdbc = new NamedParameterJdbcTemplate(this.refreshJdbc);
        this.txTemplate = new TransactionTemplate(txManager);
        // READ COMMITTED cho transaction GHI facts (chỉ đụng problem_customer_facts). Việc tránh lock
        // trên bảng nguồn được xử lý bằng cách tách pha ĐỌC ra SELECT thường (xem recomputeBatch) —
        // KHÔNG dựa vào READ COMMITTED để bỏ shared lock của INSERT...SELECT (MariaDB vẫn đặt lock đó).
        this.txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.cache = cache;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSchema() {
        try {
            refreshJdbc.execute(DDL_FACTS);
            for (String alter : DDL_FACTS_ALTER) {
                try { refreshJdbc.execute(alter); } catch (Exception e) {
                    log.warn("Bỏ qua ALTER facts [{}]: {}", alter, e.getMessage());
                }
            }
            refreshJdbc.execute(DDL_STATE);
            refreshJdbc.update("INSERT IGNORE INTO problem_customer_facts_state (id) VALUES (1)");
            log.info("Bảng problem_customer_facts + state (Số thả nổi precompute) đã sẵn sàng.");
        } catch (Exception e) {
            log.error("Không tạo được bảng problem_customer_facts: {}", e.getMessage());
        }
    }

    /** Lần đầu sau boot (30s để cột phone9 kịp tạo): catch-up incremental; nếu chưa từng chạy → full. */
    @Scheduled(initialDelayString = "30000", fixedDelayString = "300000")
    public void incrementalTick() {
        submit(() -> {
            if (readWatermark() == null) runFull(); // chưa từng chạy → dựng bảng lần đầu
            else runIncremental();
        });
    }

    /** Full reconcile ban đêm 03:00 để sửa lệch tích luỹ. */
    @Scheduled(cron = "0 0 3 * * *")
    public void nightlyTick() {
        submit(this::runFull);
    }

    private void submit(Runnable task) {
        if (!running.compareAndSet(false, true)) {
            log.info("Bỏ qua lượt cập nhật facts: lượt trước chưa hoàn tất.");
            return;
        }
        pool.submit(() -> {
            try { task.run(); } finally { running.set(false); }
        });
    }

    // ---- FULL ----
    /** Full reconcile 2 pha như {@link #recomputeBatch}: SELECT thường (không lock nguồn) → DELETE + batch INSERT. */
    void runFull() {
        long t0 = System.nanoTime();
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Object[]> rows = selectFacts(FULL_SELECT_SQL, null, Timestamp.valueOf(now));
            txTemplate.executeWithoutResult(s -> {
                refreshJdbc.update("DELETE FROM problem_customer_facts");
                insertFactsBatched(rows);
            });
            writeWatermark(now, now); // set cả last_full_at lẫn last_incremental_at
            cache.clear();
            log.info("Full reconcile facts Số thả nổi: {} khách, {} ms.", rows.size(), ms(t0));
        } catch (Exception e) {
            log.error("Lỗi full reconcile facts (giữ dữ liệu cũ): {}", e.getMessage());
        }
    }

    // ---- INCREMENTAL ----
    void runIncremental() {
        long t0 = System.nanoTime();
        try {
            LocalDateTime since = readWatermark();
            LocalDateTime scanFrom = (since != null) ? since.minusMinutes(WATERMARK_BUFFER_MIN) : LocalDateTime.now().minusDays(1);
            LocalDateTime wnow = LocalDateTime.now();

            Set<String> ids = collectAffectedIds(scanFrom);
            if (ids.isEmpty()) {
                writeWatermark(wnow, null);
                log.info("Incremental facts Số thả nổi: 0 khách thay đổi, {} ms.", ms(t0));
                return;
            }

            List<String> all = new ArrayList<>(ids);
            int upserted = 0;
            for (int i = 0; i < all.size(); i += BATCH) {
                List<String> batch = all.subList(i, Math.min(i + BATCH, all.size()));
                upserted += recomputeBatch(batch, wnow);
            }
            writeWatermark(wnow, null);
            cache.clear();
            log.info("Incremental facts Số thả nổi: {} khách thay đổi, {} dòng đủ điều kiện, {} ms.",
                    ids.size(), upserted, ms(t0));
        } catch (Exception e) {
            log.error("Lỗi incremental facts (giữ watermark cũ để thử lại): {}", e.getMessage());
        }
    }

    /**
     * Recompute 1 lô. Tách 2 pha để tránh Lock wait timeout (1205) với đồng bộ khách hàng:
     * <ol>
     *   <li><b>ĐỌC</b> facts bằng {@code SELECT} thường (autocommit → non-locking consistent read) →
     *       KHÔNG đặt shared lock lên customers/customer_notes/orders/customer_phone_numbers. Trước đây
     *       dùng {@code INSERT ... SELECT}: MariaDB vẫn đặt shared next-key lock trên bảng nguồn dù đã
     *       READ COMMITTED, chặn chéo với giao dịch ghi customer_notes lúc sync.</li>
     *   <li><b>GHI</b> trong transaction ngắn chỉ đụng {@code problem_customer_facts}: DELETE lô + batch
     *       INSERT các dòng đã tính sẵn. Facts hơi trễ 1 nhịp nếu nguồn đổi giữa 2 pha — kỳ refresh sau
     *       đối soát lại, chấp nhận được.</li>
     * </ol>
     */
    private int recomputeBatch(List<String> ids, LocalDateTime now) {
        List<Object[]> rows = selectFacts(SCOPED_SELECT_SQL, new MapSqlParameterSource("ids", ids),
                Timestamp.valueOf(now));
        MapSqlParameterSource delParams = new MapSqlParameterSource("ids", ids);
        txTemplate.executeWithoutResult(s -> {
            namedJdbc.update("DELETE FROM problem_customer_facts WHERE customer_id IN (:ids)", delParams);
            insertFactsBatched(rows);
        });
        return rows.size();
    }

    /** Chạy SELECT facts (14 cột) ngoài transaction, gắn thêm updatedAt thành dòng 15 cột sẵn để insert. */
    private List<Object[]> selectFacts(String sql, MapSqlParameterSource params, Timestamp updatedAt) {
        List<Object[]> rows = new ArrayList<>();
        RowCallbackHandler handler = rs -> rows.add(new Object[]{
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4),
                rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getTimestamp(9),
                rs.getInt(10), rs.getTimestamp(11), rs.getTimestamp(12), rs.getInt(13),
                rs.getTimestamp(14), updatedAt
        });
        if (params != null) namedJdbc.query(sql, params, handler);
        else refreshJdbc.query(sql, handler);
        return rows;
    }

    /** Insert facts theo lô {@link #BATCH} dòng để statement không quá lớn (gọi trong transaction ghi). */
    private void insertFactsBatched(List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i += BATCH) {
            refreshJdbc.batchUpdate(FACTS_BATCH_INSERT_SQL, rows.subList(i, Math.min(i + BATCH, rows.size())));
        }
    }

    /** Tập customer_id bị ảnh hưởng: đơn/ghi chú đổi từ scanFrom + toàn bộ TCC. */
    private Set<String> collectAffectedIds(LocalDateTime scanFrom) {
        Set<String> ids = new LinkedHashSet<>();
        Timestamp from = Timestamp.valueOf(scanFrom);

        // Đơn đổi → customer_id trực tiếp + phone9 (map sang customer_id qua cpn)
        Set<String> phone9s = new LinkedHashSet<>();
        refreshJdbc.query(
                "SELECT DISTINCT customer_id, phone9 FROM orders WHERE updated_at >= ?",
                rs -> {
                    String cid = rs.getString(1);
                    if (cid != null && !cid.isBlank()) ids.add(cid);
                    String p9 = rs.getString(2);
                    if (p9 != null && !p9.isBlank()) phone9s.add(p9);
                }, from);
        if (!phone9s.isEmpty()) {
            List<String> p9list = new ArrayList<>(phone9s);
            for (int i = 0; i < p9list.size(); i += BATCH) {
                List<String> b = p9list.subList(i, Math.min(i + BATCH, p9list.size()));
                ids.addAll(namedJdbc.queryForList(
                        "SELECT DISTINCT customer_id FROM customer_phone_numbers WHERE phone9 IN (:p9)",
                        new MapSqlParameterSource("p9", b), String.class));
            }
        }

        // Ghi chú đổi → customer_id
        ids.addAll(refreshJdbc.queryForList(
                "SELECT DISTINCT customer_id FROM customer_notes WHERE updated_at >= ?",
                String.class, from));

        // TCC hiện tại (nhóm D mới) + khách đang gắn cờ refused trong facts (để bắt trường hợp
        // vừa RỜI TCC → recompute bỏ cờ / xoá khỏi facts). Cả hai tập đều nhỏ.
        ids.addAll(refreshJdbc.queryForList("SELECT customer_id FROM refused_care", String.class));
        ids.addAll(refreshJdbc.queryForList(
                "SELECT customer_id FROM problem_customer_facts WHERE refused = 1", String.class));

        ids.remove(null);
        return ids;
    }

    // ---- watermark ----
    private LocalDateTime readWatermark() {
        try {
            Timestamp ts = refreshJdbc.queryForObject(
                    "SELECT last_incremental_at FROM problem_customer_facts_state WHERE id = 1", Timestamp.class);
            return ts != null ? ts.toLocalDateTime() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Ghi mốc; nếu fullAt != null cập nhật luôn last_full_at. */
    private void writeWatermark(LocalDateTime incrementalAt, LocalDateTime fullAt) {
        if (fullAt != null) {
            refreshJdbc.update("UPDATE problem_customer_facts_state SET last_incremental_at = ?, last_full_at = ? WHERE id = 1",
                    Timestamp.valueOf(incrementalAt), Timestamp.valueOf(fullAt));
        } else {
            refreshJdbc.update("UPDATE problem_customer_facts_state SET last_incremental_at = ? WHERE id = 1",
                    Timestamp.valueOf(incrementalAt));
        }
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
