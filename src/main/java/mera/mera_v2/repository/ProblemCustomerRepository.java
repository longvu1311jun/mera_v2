package mera.mera_v2.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.QueryHint;
import mera.mera_v2.customer.DTO.OrderPresenceView;
import mera.mera_v2.customer.DTO.PhoneMatchView;
import mera.mera_v2.customer.DTO.ProblemCustomerView;
import mera.mera_v2.entity.Customer;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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
 *
 * Hiệu năng: danh sách KHÔNG còn tính trực tiếp bằng query nhiều JOIN nặng (từng gây treo 90s).
 * Toàn bộ "facts" theo từng khách (đếm đơn, số ghi chú, mốc gần nhất, có đơn active/đã nhận...)
 * được tính sẵn định kỳ vào bảng {@code problem_customer_facts} bởi
 * {@code ProblemCustomerFactsService}. Đọc danh sách chỉ là 1 SELECT phẳng đã đánh index
 * ({@link #findProblemCustomerFacts}). Điều kiện nhóm A/B/C vẫn áp lúc đọc (theo ngưỡng +
 * thời điểm hiện tại) nên đổi bộ lọc vẫn đúng.
 */
public interface ProblemCustomerRepository extends Repository<Customer, String> {

    /**
     * Đọc danh sách "Số thả nổi" từ bảng facts precompute {@code problem_customer_facts}.
     * Chỉ 1 SELECT phẳng (index theo note_count / inserted_at / has_received) — nhanh, không
     * bao giờ treo. Nhóm A/B/C áp trực tiếp trên facts + ngưỡng hiện tại (giống logic cũ):
     *  - Nhóm A: chưa có đơn đã nhận, note_count &gt;= :minNotes.
     *  - Nhóm B: chưa có đơn đã nhận, tạo trong khoảng [:oldestTime, :thresholdTime), note_count &gt;= 1.
     *  - Nhóm C: đã có đơn đã nhận nhưng lần gần nhất &lt; :staleTime.
     * active_order_count = 0 đảm bảo khách chưa có đơn đang xử lý (đã tính sẵn khi refresh).
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "30000"))
    @Query(value = """
        SELECT customer_id AS customerId,
               name AS name,
               phone AS phone,
               inserted_at AS insertedAt,
               order_count AS orderCount,
               succeed_order_count AS succeedOrderCount,
               last_order_at AS lastOrderAt,
               note_count AS noteCount,
               last_note_at AS lastNoteAt,
               has_received AS hasReceivedOrder,
               last_received_at AS lastReceivedAt
        FROM problem_customer_facts
        WHERE active_order_count = 0
          AND inserted_at >= :fromDate AND inserted_at < :toDate
          AND (
               -- Nhóm A / B: chưa có đơn đã nhận
               (has_received = 0
                AND (note_count >= :minNotes
                     OR (inserted_at < :thresholdTime AND inserted_at >= :oldestTime AND note_count >= 1)))
               -- Nhóm C: có đơn đã nhận nhưng lần gần nhất quá lâu
            OR (has_received = 1 AND last_received_at < :staleTime)
          )
        ORDER BY note_count DESC, inserted_at ASC
        LIMIT :maxRows
        """, nativeQuery = true)
    List<ProblemCustomerView> findProblemCustomerFacts(
        @Param("minNotes") int minNotes,
        @Param("thresholdTime") LocalDateTime thresholdTime,
        @Param("oldestTime") LocalDateTime oldestTime,
        @Param("staleTime") LocalDateTime staleTime,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("maxRows") int maxRows
    );

    /**
     * Kiểm tra một tập khách (đã rời danh sách Số thả nổi) hiện có đơn hay không, để phân biệt
     * "đã tối ưu" (có đơn đang xử lý / đã nhận) với "rời vì lý do khác".
     *
     * <p>Đối chiếu đơn theo mã KH VÀ theo SĐT (9 số cuối), khử trùng phần khớp SĐT với phần đã
     * khớp mã KH — cùng logic với query refresh facts. Chỉ quét trong phạm vi :ids nên
     * nhẹ hơn nhiều so với query danh sách đầy đủ.</p>
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "90000"))
    @Query(value = """
        SELECT c.id AS customerId,
               (COALESCE(byId.act, 0) + COALESCE(byPhone.act, 0)) AS activeCount,
               (COALESCE(byId.rcv, 0) + COALESCE(byPhone.rcv, 0)) AS receivedCount,
               (COALESCE(byId.tot, 0) + COALESCE(byPhone.tot, 0)) AS totalCount
        FROM customers c
        LEFT JOIN (
            SELECT o.customer_id AS cid,
                   SUM(CASE WHEN o.status IN (11, 1, 8, 9, 2) THEN 1 ELSE 0 END) AS act,
                   SUM(CASE WHEN o.status IN (3, 16) THEN 1 ELSE 0 END) AS rcv,
                   COUNT(*) AS tot
            FROM orders o
            WHERE o.customer_id IN (:ids)
            GROUP BY o.customer_id
        ) byId ON byId.cid = c.id
        LEFT JOIN (
            SELECT cpn.customer_id AS cid,
                   SUM(CASE WHEN op.status IN (11, 1, 8, 9, 2) AND NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS act,
                   SUM(CASE WHEN op.status IN (3, 16) AND NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS rcv,
                   SUM(CASE WHEN NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS tot
            FROM customer_phone_numbers cpn
            JOIN orders op ON op.phone9 = cpn.phone9
            WHERE cpn.customer_id IN (:ids)
              AND cpn.phone9 IS NOT NULL
            GROUP BY cpn.customer_id
        ) byPhone ON byPhone.cid = c.id
        WHERE c.id IN (:ids)
        """, nativeQuery = true)
    List<OrderPresenceView> checkOrderPresence(@Param("ids") Collection<String> ids);

    /**
     * Khớp danh sách "9 số cuối SĐT" (từ file Excel) → mã khách hàng.
     *
     * <p>Nhiều khách chỉ được nhận diện qua SĐT trên ĐƠN (orders.customer_phone) chứ chưa có
     * trong customer_phone_numbers. Vì vậy khớp qua CẢ HAI nguồn (giống cách trang số nổi đối
     * chiếu đơn theo 9 số cuối), rồi lấy MIN(customer_id) cho mỗi số. Chỉ nhận customer_id đã
     * tồn tại (orders.customer_id chỉ được gán khi khách có trong bảng customers).</p>
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "90000"))
    @Query(value = """
        SELECT m.p9 AS p9, MIN(m.customer_id) AS customerId
        FROM (
            SELECT cpn.phone9 AS p9, cpn.customer_id AS customer_id
            FROM customer_phone_numbers cpn
            WHERE cpn.phone9 IN (:phones9)
            UNION ALL
            SELECT o.phone9 AS p9, o.customer_id AS customer_id
            FROM orders o
            WHERE o.customer_id IS NOT NULL
              AND o.phone9 IN (:phones9)
        ) m
        WHERE m.customer_id IS NOT NULL
        GROUP BY m.p9
        """, nativeQuery = true)
    List<PhoneMatchView> matchCustomerIdsByPhone9(@Param("phones9") Collection<String> phones9);

    /**
     * Trong các "9 số cuối" không khớp được khách, lọc ra những số CÓ đơn trong hệ thống
     * nhưng đơn chưa được gán mã khách (orders.customer_id IS NULL) — để báo lý do cụ thể
     * khi tải danh sách SĐT không khớp trên trang Từ chối chăm.
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "90000"))
    @Query(value = """
        SELECT DISTINCT o.phone9
        FROM orders o
        WHERE o.phone9 IN (:phones9)
          AND o.customer_id IS NULL
        """, nativeQuery = true)
    List<String> findPhone9HavingOrdersWithoutCustomer(@Param("phones9") Collection<String> phones9);

    /** Mã khách trong danh sách "Từ chối chăm" (để loại khỏi nhóm A/B/C — TCC đè). */
    @Query(value = "SELECT customer_id FROM refused_care", nativeQuery = true)
    List<String> findRefusedCareIds();

    /**
     * Làm giàu thông tin cho nhóm D "Từ chối chăm": mọi khách trong bảng refused_care
     * (không cần điều kiện A/B/C), có lọc theo khoảng Ngày tạo KH. Các phép GROUP BY được
     * giới hạn trong tập refused_care (nhỏ) nên nhẹ. Cấu trúc đếm đơn giống query chính
     * (khớp mã KH + khớp 9 số cuối SĐT) để "tổng đơn" nhất quán với nhóm A/B/C.
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "90000"))
    @Query(value = """
        SELECT c.id AS customerId,
               c.name AS name,
               COALESCE(p.phone_number, rc.phone) AS phone,
               c.inserted_at AS insertedAt,
               (COALESCE(tot.cnt, 0) + COALESCE(oph.total_cnt, 0)) AS orderCount,
               (COALESCE(rcv.cnt, 0) + COALESCE(oph.received_cnt, 0)) AS succeedOrderCount,
               c.last_order_at AS lastOrderAt,
               COALESCE(n.note_count, 0) AS noteCount,
               n.last_note_at AS lastNoteAt,
               CASE WHEN COALESCE(rcv.cnt, 0) > 0 OR COALESCE(oph.has_received, 0) > 0 THEN 1 ELSE 0 END AS hasReceivedOrder,
               GREATEST(COALESCE(rcv.last_received_at, oph.last_received_at),
                        COALESCE(oph.last_received_at, rcv.last_received_at)) AS lastReceivedAt
        FROM refused_care rc
        JOIN customers c ON c.id = rc.customer_id
             AND c.inserted_at >= :fromDate AND c.inserted_at < :toDate
        LEFT JOIN (
            SELECT cn.customer_id, COUNT(*) AS note_count, MAX(cn.created_at) AS last_note_at
            FROM customer_notes cn
            JOIN refused_care r2 ON r2.customer_id = cn.customer_id
            WHERE cn.removed_at IS NULL
            GROUP BY cn.customer_id
        ) n ON n.customer_id = c.id
        LEFT JOIN (
            SELECT o.customer_id, COUNT(*) AS cnt
            FROM orders o JOIN refused_care r2 ON r2.customer_id = o.customer_id
            GROUP BY o.customer_id
        ) tot ON tot.customer_id = c.id
        LEFT JOIN (
            SELECT o.customer_id, COUNT(*) AS cnt, MAX(o.inserted_at) AS last_received_at
            FROM orders o JOIN refused_care r2 ON r2.customer_id = o.customer_id
            WHERE o.status IN (3, 16)
            GROUP BY o.customer_id
        ) rcv ON rcv.customer_id = c.id
        LEFT JOIN (
            SELECT cpn.customer_id,
                   SUBSTRING_INDEX(GROUP_CONCAT(cpn.phone_number ORDER BY cpn.is_primary DESC, cpn.id ASC), ',', 1) AS phone_number
            FROM customer_phone_numbers cpn
            JOIN refused_care r2 ON r2.customer_id = cpn.customer_id
            GROUP BY cpn.customer_id
        ) p ON p.customer_id = c.id
        LEFT JOIN (
            SELECT cpn.customer_id,
                   MAX(CASE WHEN op.status IN (3, 16) THEN 1 ELSE 0 END) AS has_received,
                   MAX(CASE WHEN op.status IN (3, 16) THEN op.inserted_at END) AS last_received_at,
                   SUM(CASE WHEN op.status IN (3, 16) AND NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS received_cnt,
                   SUM(CASE WHEN NOT (op.customer_id <=> cpn.customer_id) THEN 1 ELSE 0 END) AS total_cnt
            FROM customer_phone_numbers cpn
            JOIN refused_care r2 ON r2.customer_id = cpn.customer_id
            JOIN orders op ON op.phone9 = cpn.phone9
            WHERE cpn.phone9 IS NOT NULL
            GROUP BY cpn.customer_id
        ) oph ON oph.customer_id = c.id
        -- Chỉ giữ ở Nhóm D nếu KHÔNG có đơn nhận nào MỚI hơn ngày thêm vào TCC.
        -- Khi khách nhận đơn mới (đơn nhận gần nhất > uploaded_at) = sale tối ưu được
        -- → rời Nhóm D (job đối soát sẽ đưa sang "Khách Đã Tối Ưu").
        WHERE GREATEST(COALESCE(rcv.last_received_at, oph.last_received_at),
                       COALESCE(oph.last_received_at, rcv.last_received_at)) IS NULL
           OR GREATEST(COALESCE(rcv.last_received_at, oph.last_received_at),
                       COALESCE(oph.last_received_at, rcv.last_received_at)) <= rc.uploaded_at
        ORDER BY noteCount DESC, c.inserted_at ASC
        LIMIT :maxRows
        """, nativeQuery = true)
    List<ProblemCustomerView> findRefusedCareCustomers(
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("maxRows") int maxRows
    );
}
