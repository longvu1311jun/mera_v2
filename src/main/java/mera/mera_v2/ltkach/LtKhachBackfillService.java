package mera.mera_v2.ltkach;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LtKhachBackfillService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * KHÔNG bọc @Transactional ở mức method. Trước đây `@Transactional(timeout = 3600)` gom toàn bộ
     * vòng lặp (hàng trăm nghìn UPDATE orders) vào MỘT transaction: undo log phình, X-lock giữ trên
     * mọi dòng đã update suốt cả giờ, mọi webhook/sync đụng orders đều "Lock wait timeout", và nếu
     * DB bị kill giữa chừng thì InnoDB phải rollback rất lâu lúc khởi động lại.
     * Mỗi UPDATE giờ tự commit (autocommit) — idempotent nhờ điều kiện `lt_count_snapshot IS NULL`
     * nên chạy dở rồi chạy lại vẫn đúng.
     */
    public Map<String, Object> backfillSnapshot() {
        log.info("=== BẮT ĐẦU BACKFILL lt_count_snapshot ===");

        // 1. Đếm đơn chưa có snapshot
        Long totalOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE lt_count_snapshot IS NULL", Long.class);
        if (totalOrders == null) totalOrders = 0L;
        log.info("Tổng orders cần backfill: {}", totalOrders);

        if (totalOrders == 0) {
            return Map.of(
                "total", 0, "updated", 0,
                "message", "Tất cả orders đã có lt_count_snapshot!"
            );
        }

        // 2. Chunk-based batch: xử lý từng batch, mỗi batch dùng temp table để tránh correlated subquery
        long updated = 0;
        long skipped = 0;
        long errors = 0;
        int chunkSize = 5000;
        int maxRetries = 3;

        while (true) {
            // Lấy batch order cần xử lý
            List<Map<String, Object>> batch = jdbcTemplate.queryForList(
                "SELECT o.id, o.customer_id, o.inserted_at FROM orders o " +
                "WHERE o.lt_count_snapshot IS NULL LIMIT " + chunkSize
            );

            if (batch.isEmpty()) {
                break;
            }

            log.info("Xử lý batch {} orders...", batch.size());
            long progressBefore = updated + skipped;

            // Với mỗi order trong batch: đếm số đơn combo thành công của cùng khách
            for (Map<String, Object> row : batch) {
                Long orderId = ((Number) row.get("id")).longValue();
                String customerId = (String) row.get("customer_id");
                Timestamp insertedAt = (Timestamp) row.get("inserted_at");

                if (customerId == null || insertedAt == null) {
                    // Vẫn phải ghi snapshot = 0, nếu để NULL thì vòng while sẽ
                    // fetch lại đúng các dòng này mãi mãi và không bao giờ thoát
                    jdbcTemplate.update(
                        "UPDATE orders SET lt_count_snapshot = 0 WHERE id = ? AND lt_count_snapshot IS NULL",
                        orderId
                    );
                    skipped++;
                    continue;
                }

                // Đếm số đơn combo thành công trước đó của khách này
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE customer_id = ? AND status IN (3, 16) AND CAST(lt_type AS UNSIGNED) = 1 AND inserted_at < ?",
                    Integer.class, customerId, insertedAt
                );

                int snapshot = (count != null) ? count : 0;

                // Cập nhật với retry
                int rowsUpdated = 0;
                for (int retry = 0; retry < maxRetries; retry++) {
                    try {
                        rowsUpdated = jdbcTemplate.update(
                            "UPDATE orders SET lt_count_snapshot = ? WHERE id = ? AND lt_count_snapshot IS NULL",
                            snapshot, orderId
                        );
                        break;
                    } catch (CannotAcquireLockException | org.springframework.dao.QueryTimeoutException e) {
                        log.warn("Retry {}/{} for order {} due to lock/timeout", retry + 1, maxRetries, orderId);
                        if (retry == maxRetries - 1) {
                            errors++;
                        } else {
                            try { Thread.sleep(100L * (retry + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        }
                    } catch (Exception e) {
                        log.warn("Error updating order {}: {}", orderId, e.getMessage());
                        errors++;
                        break;
                    }
                }

                updated += rowsUpdated;
            }

            log.info("Đã xử lý {} orders, tổng updated: {}, skipped: {}, errors: {}", batch.size(), updated, skipped, errors);

            // Cả batch không ghi được dòng nào (lỗi lặp lại) → query kế tiếp sẽ trả về đúng
            // các dòng này mãi mãi. Dừng thay vì lặp vô hạn; gọi lại endpoint sau khi DB ổn.
            if (updated + skipped == progressBefore) {
                log.error("Batch không tiến triển (errors={}), dừng backfill để tránh lặp vô hạn.", errors);
                break;
            }
        }

        log.info("=== HOÀN THÀNH BACKFILL ===");
        log.info("Updated: {}, Skipped: {}, Errors: {}", updated, skipped, errors);

        return Map.of(
            "total", totalOrders,
            "updated", updated,
            "skipped", skipped,
            "errors", errors,
            "message", "Backfill lt_count_snapshot hoàn tất!"
        );
    }
}
