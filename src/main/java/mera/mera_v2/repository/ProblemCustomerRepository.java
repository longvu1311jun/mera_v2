package mera.mera_v2.repository;

import java.time.LocalDateTime;
import java.util.List;
import mera.mera_v2.customer.DTO.ProblemCustomerView;
import mera.mera_v2.entity.Customer;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Query "Số thả nổi" — khách hàng cần chú ý.
 *
 * Loại trừ chung: khách có >= 1 đơn ở trạng thái đang xử lý / thành công thì KHÔNG lên số nổi:
 * 11 (Chờ hàng), 1 (Đã xác nhận), 8 (Đang đóng hàng), 9 (Chờ chuyển hàng),
 * 2 (Đã gửi hàng), 3 (Đã nhận), 16 (Đã thu tiền).
 * Đơn được khớp theo mã KH HOẶC theo SĐT (so 9 số cuối), vì đơn từ webhook lưu
 * customer_id dạng CUST_&lt;sđt&gt; còn khách từ sync POS mang mã UUID — cùng một người
 * nhưng hai mã khác nhau, chỉ nối được qua số điện thoại.
 *
 * Trong số còn lại, lấy khách thuộc một trong hai nhóm (OR):
 *  - Nhóm A: có >= :minNotes ghi chú còn hiệu lực.
 *  - Nhóm B: tạo trước :thresholdTime (quá Xh) và có >= 1 ghi chú,
 *            giới hạn tạo sau :oldestTime để tránh quét toàn DB.
 */
public interface ProblemCustomerRepository extends Repository<Customer, String> {

    @Query(value = """
        SELECT c.id AS customerId,
               c.name AS name,
               p.phone_number AS phone,
               c.inserted_at AS insertedAt,
               c.order_count AS orderCount,
               c.succeed_order_count AS succeedOrderCount,
               c.last_order_at AS lastOrderAt,
               COALESCE(n.note_count, 0) AS noteCount,
               n.last_note_at AS lastNoteAt
        FROM customers c
        LEFT JOIN (
            SELECT customer_id, COUNT(*) AS note_count, MAX(created_at) AS last_note_at
            FROM customer_notes
            WHERE removed_at IS NULL
            GROUP BY customer_id
        ) n ON n.customer_id = c.id
        LEFT JOIN (
            SELECT customer_id, COUNT(*) AS cnt
            FROM orders
            WHERE status IN (11, 1, 8, 9, 2, 3, 16)
            GROUP BY customer_id
        ) oa ON oa.customer_id = c.id
        LEFT JOIN (
            SELECT customer_id,
                   SUBSTRING_INDEX(GROUP_CONCAT(phone_number ORDER BY is_primary DESC, id ASC), ',', 1) AS phone_number
            FROM customer_phone_numbers
            GROUP BY customer_id
        ) p ON p.customer_id = c.id
        LEFT JOIN (
            SELECT DISTINCT cpn.customer_id
            FROM customer_phone_numbers cpn
            JOIN (
                SELECT DISTINCT RIGHT(REGEXP_REPLACE(customer_phone, '[^0-9]', ''), 9) AS p9
                FROM orders
                WHERE status IN (11, 1, 8, 9, 2, 3, 16)
                  AND customer_phone IS NOT NULL
                  AND LENGTH(REGEXP_REPLACE(customer_phone, '[^0-9]', '')) >= 9
            ) op ON RIGHT(cpn.phone_number, 9) = op.p9
            WHERE LENGTH(cpn.phone_number) >= 9
        ) oph ON oph.customer_id = c.id
        WHERE COALESCE(oa.cnt, 0) = 0
          AND oph.customer_id IS NULL
          AND (
               COALESCE(n.note_count, 0) >= :minNotes
            OR (c.inserted_at < :thresholdTime
                AND c.inserted_at >= :oldestTime
                AND COALESCE(n.note_count, 0) >= 1)
          )
        ORDER BY noteCount DESC, c.inserted_at ASC
        LIMIT :maxRows
        """, nativeQuery = true)
    List<ProblemCustomerView> findProblemCustomers(
        @Param("minNotes") int minNotes,
        @Param("thresholdTime") LocalDateTime thresholdTime,
        @Param("oldestTime") LocalDateTime oldestTime,
        @Param("maxRows") int maxRows
    );
}
