package mera.mera_v2.repository;

import java.time.LocalDateTime;
import java.util.List;
import mera.mera_v2.customer.DTO.ProblemCustomerView;
import mera.mera_v2.entity.Customer;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Query "Số thả nổi" — khách hàng cần chú ý, gồm 3 nhóm (OR):
 *
 * Định nghĩa trạng thái đơn:
 *  - Đang xử lý: 11 (Chờ hàng), 1 (Đã xác nhận), 8 (Đang đóng hàng), 9 (Chờ chuyển hàng), 2 (Đã gửi hàng).
 *  - Đã nhận:    3 (Đã nhận), 16 (Đã thu tiền — đã qua bước nhận).
 * Đơn được khớp theo mã KH HOẶC theo SĐT (so 9 số cuối) vì đơn webhook lưu customer_id
 * dạng CUST_&lt;sđt&gt; còn khách sync POS mang mã UUID.
 *
 *  - Nhóm A: >= :minNotes ghi chú còn hiệu lực, chưa có đơn đang xử lý / đã nhận.
 *  - Nhóm B: tạo trước :thresholdTime (quá Xh), có >= 1 ghi chú, chưa có đơn đang xử lý / đã nhận
 *            (giới hạn tạo sau :oldestTime để tránh quét toàn DB).
 *  - Nhóm C: có đơn đã nhận nhưng lần gần nhất trước :staleTime (quá X tháng),
 *            và hiện KHÔNG có đơn nào đang xử lý.
 *
 * lastReceivedAt = MAX của 2 nhánh (khớp mã KH / khớp SĐT); NULL khi chưa có đơn đã nhận.
 * Dùng GREATEST(COALESCE(a,b), COALESCE(b,a)) để giữ đúng kiểu DATETIME (tránh sentinel chuỗi).
 */
public interface ProblemCustomerRepository extends Repository<Customer, String> {

    @Query(value = """
        SELECT c.id AS customerId,
               c.name AS name,
               p.phone_number AS phone,
               c.inserted_at AS insertedAt,
               (COALESCE(tot.cnt, 0) + COALESCE(oph.total_cnt, 0)) AS orderCount,
               (COALESCE(rcv.cnt, 0) + COALESCE(oph.received_cnt, 0)) AS succeedOrderCount,
               c.last_order_at AS lastOrderAt,
               COALESCE(n.note_count, 0) AS noteCount,
               n.last_note_at AS lastNoteAt,
               CASE WHEN COALESCE(rcv.cnt, 0) > 0 OR COALESCE(oph.has_received, 0) > 0 THEN 1 ELSE 0 END AS hasReceivedOrder,
               GREATEST(COALESCE(rcv.last_received_at, oph.last_received_at),
                        COALESCE(oph.last_received_at, rcv.last_received_at)) AS lastReceivedAt
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
            WHERE status IN (11, 1, 8, 9, 2)
            GROUP BY customer_id
        ) act ON act.customer_id = c.id
        LEFT JOIN (
            SELECT customer_id, COUNT(*) AS cnt, MAX(inserted_at) AS last_received_at
            FROM orders
            WHERE status IN (3, 16)
            GROUP BY customer_id
        ) rcv ON rcv.customer_id = c.id
        -- Tổng đơn (mọi trạng thái) khớp theo mã KH
        LEFT JOIN (
            SELECT customer_id, COUNT(*) AS cnt
            FROM orders
            GROUP BY customer_id
        ) tot ON tot.customer_id = c.id
        LEFT JOIN (
            SELECT customer_id,
                   SUBSTRING_INDEX(GROUP_CONCAT(phone_number ORDER BY is_primary DESC, id ASC), ',', 1) AS phone_number
            FROM customer_phone_numbers
            GROUP BY customer_id
        ) p ON p.customer_id = c.id
        LEFT JOIN (
            SELECT cpn.customer_id,
                   MAX(CASE WHEN op.status IN (11, 1, 8, 9, 2) THEN 1 ELSE 0 END) AS has_active,
                   MAX(CASE WHEN op.status IN (3, 16) THEN 1 ELSE 0 END) AS has_received,
                   MAX(CASE WHEN op.status IN (3, 16) THEN op.inserted_at END) AS last_received_at,
                   -- Đếm đơn khớp qua SĐT, loại đơn đã đếm ở nhánh khớp mã KH (tránh đếm trùng)
                   SUM(CASE WHEN op.status IN (3, 16) AND NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS received_cnt,
                   SUM(CASE WHEN NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS total_cnt
            FROM customer_phone_numbers cpn
            JOIN (
                SELECT customer_id, status, inserted_at,
                       RIGHT(REGEXP_REPLACE(customer_phone, '[^0-9]', ''), 9) AS p9
                FROM orders
                WHERE customer_phone IS NOT NULL
                  AND LENGTH(REGEXP_REPLACE(customer_phone, '[^0-9]', '')) >= 9
            ) op ON RIGHT(cpn.phone_number, 9) = op.p9
            WHERE LENGTH(cpn.phone_number) >= 9
            GROUP BY cpn.customer_id
        ) oph ON oph.customer_id = c.id
        WHERE COALESCE(act.cnt, 0) = 0
          AND COALESCE(oph.has_active, 0) = 0
          -- Lọc theo khoảng Ngày tạo KH (mặc định mở rộng khi không truyền)
          AND c.inserted_at >= :fromDate
          AND c.inserted_at < :toDate
          AND (
               -- Nhóm A / B: chưa có đơn đã nhận
               (COALESCE(rcv.cnt, 0) = 0 AND COALESCE(oph.has_received, 0) = 0
                AND (
                     COALESCE(n.note_count, 0) >= :minNotes
                  OR (c.inserted_at < :thresholdTime
                      AND c.inserted_at >= :oldestTime
                      AND COALESCE(n.note_count, 0) >= 1)
                ))
               -- Nhóm C: có đơn đã nhận nhưng lần gần nhất quá lâu
            OR ((COALESCE(rcv.cnt, 0) > 0 OR COALESCE(oph.has_received, 0) > 0)
                AND GREATEST(COALESCE(rcv.last_received_at, oph.last_received_at),
                             COALESCE(oph.last_received_at, rcv.last_received_at)) < :staleTime)
          )
        ORDER BY noteCount DESC, c.inserted_at ASC
        LIMIT :maxRows
        """, nativeQuery = true)
    List<ProblemCustomerView> findProblemCustomers(
        @Param("minNotes") int minNotes,
        @Param("thresholdTime") LocalDateTime thresholdTime,
        @Param("oldestTime") LocalDateTime oldestTime,
        @Param("staleTime") LocalDateTime staleTime,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("maxRows") int maxRows
    );
}
