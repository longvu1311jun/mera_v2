package mera.mera_v2.ltkach;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.Combo;
import mera.mera_v2.entity.ProductSubstitution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service tính LT (Liệu Trình) cho orders và customers.
 * 
 * Logic:
 * - Đơn hàng có trạng thái thành công (status = 3 hoặc 16) mới được tính LT.
 * - Items trong order match combo HOẶC substitution group → lt_type = true → +1 vào customers.lt_count.
 * - Items không match → lt_type = false → không cộng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LtCalculationService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Tính LT cho một đơn hàng.
     * 
     * Logic:
     * 1. Đơn hàng mua B, C, D
     * 2. Combo = {A, B, C}, Substitution = {A -> D}
     * 3. Đủ combo (D thay A) → lt_type = TRUE (chẵn)
     * 4. Không đủ combo → lt_type = FALSE (lẻ)
     * 5. Nếu lt_type = TRUE VÀ status = 3 → +1 vào customers.lt_count
     * 
     * @param orderId ID của order cần tính
     * @param isCompletedOrder đã completed chưa (status = 3)
     * @return LtResult chứa ltType và ltCount
     */
    @Transactional
    public LtResult calculateForOrder(Long orderId, boolean isCompletedOrder) {
        // 1. Lấy order và customer
        Object[] orderRow = (Object[]) em.createNativeQuery(
            "SELECT o.id, o.customer_id, o.status, o.lt_type, c.lt_count " +
            "FROM orders o LEFT JOIN customers c ON o.customer_id = c.id " +
            "WHERE o.id = :orderId"
        ).setParameter("orderId", orderId).getSingleResult();

        String customerId = (String) orderRow[1];
        Integer status = ((Number) orderRow[2]).intValue();
        Boolean currentLtType = orderRow[3] != null ? (((Number) orderRow[3]).intValue() == 1) : null;
        Integer oldLtCount = orderRow[4] != null ? ((Number) orderRow[4]).intValue() : 0;

        // 2. Lấy items của order
        List<Object[]> items = em.createNativeQuery(
            "SELECT oi.product_id, oi.variation_id, oi.quantity FROM order_items oi WHERE oi.order_id = :orderId"
        ).setParameter("orderId", orderId).getResultList();

        // Map: productKey -> quantity (key = productId|variationId hoặc productId)
        Map<String, Integer> orderProductMap = new HashMap<>();
        for (Object[] row : items) {
            String key = buildKey((String) row[0], (String) row[1]);
            if (key != null) {
                int qty = row[2] != null ? ((Number) row[2]).intValue() : 1;
                orderProductMap.merge(key, qty, Integer::sum);
            }
        }

        // 3. Load combo groups và substitution groups (cache để tối ưu)
        Map<String, Set<String>> comboGroups = loadComboGroups();
        Map<String, Set<String>> substitutionGroups = loadSubstitutionGroups();

        // 4. Check đơn có đủ combo không (có thể thay thế)
        boolean isFullCombo = checkIsFullCombo(orderProductMap, comboGroups, substitutionGroups);

        // 5. Ghi orders.lt_type
        em.createNativeQuery(
            "UPDATE orders SET lt_type = :ltType WHERE id = :orderId"
        ).setParameter("ltType", isFullCombo ? 1 : 0)
         .setParameter("orderId", orderId).executeUpdate();

        // 6. Ghi orders.lt_count_snapshot = customers.lt_count HIỆN TẠI
        // Đây là lt_count tại thời điểm đơn này được tạo, dùng cho báo cáo theo tháng
        em.createNativeQuery(
            "UPDATE orders SET lt_count_snapshot = :ltCount WHERE id = :orderId"
        ).setParameter("ltCount", oldLtCount)
         .setParameter("orderId", orderId).executeUpdate();

        // 7. Nếu lt_type = TRUE VÀ status = 3 → +1 vào lt_count
        if (isFullCombo && status == 3 && customerId != null) {
            em.createNativeQuery(
                "UPDATE customers SET lt_count = lt_count + 1 WHERE id = :customerId"
            ).setParameter("customerId", customerId).executeUpdate();
            oldLtCount++;
            log.info("Order {}: LT chẵn, +1 cho customer {}, lt_count={}", orderId, customerId, oldLtCount);
        }

        return new LtResult(isFullCombo, oldLtCount, !Objects.equals(currentLtType, isFullCombo));
    }

    /**
     * Check xem đơn hàng có đủ combo không.
     * 
     * Combo = {A, B, C}
     * Substitution = {A -> D} (A có thể thay bằng D)
     * Đơn mua B, C, D → Đủ combo (D thay A) → TRUE
     * 
     * @param orderProductMap products trong order (key -> quantity)
     * @param comboGroups Map<groupName, Set<productKey>>
     * @param substitutionGroups Map<productKey, Set<substituteKey>>
     * @return true nếu đủ combo
     */
    public boolean checkIsFullCombo(
            Map<String, Integer> orderProductMap,
            Map<String, Set<String>> comboGroups,
            Map<String, Set<String>> substitutionGroups) {

        // Với mỗi combo group, check xem order có đủ không
        for (Map.Entry<String, Set<String>> comboEntry : comboGroups.entrySet()) {
            Set<String> requiredProducts = comboEntry.getValue();
            boolean hasAllRequired = true;

            for (String requiredProduct : requiredProducts) {
                boolean hasProduct = false;

                // Check trực tiếp
                if (orderProductMap.containsKey(requiredProduct)) {
                    hasProduct = true;
                } else {
                    // Check substitution: có thể thay bằng sản phẩm khác
                    Set<String> substitutes = substitutionGroups.get(requiredProduct);
                    if (substitutes != null) {
                        for (String substitute : substitutes) {
                            if (orderProductMap.containsKey(substitute)) {
                                hasProduct = true;
                                break;
                            }
                        }
                    }
                }

                if (!hasProduct) {
                    hasAllRequired = false;
                    break;
                }
            }

            // Nếu đơn đủ combo → return true
            if (hasAllRequired) {
                return true;
            }
        }

        // Không đủ combo nào
        return false;
    }

    /**
     * Batch calculate LT cho nhiều orders.
     * @param orderIds danh sách order IDs
     * @param completedOrderIds set các order có status=3 (completed)
     */
    @Transactional
    public List<LtResult> calculateForOrders(List<Long> orderIds, Set<Long> completedOrderIds) {
        List<LtResult> results = new ArrayList<>();
        for (Long orderId : orderIds) {
            boolean isCompleted = completedOrderIds != null && completedOrderIds.contains(orderId);
            results.add(calculateForOrder(orderId, isCompleted));
        }
        return results;
    }

    /**
     * Recalculate tất cả LT cho một customer (chạy lại từ đầu).
     */
    @Transactional
    public int recalculateForCustomer(String customerId) {
        // Đếm số order thành công có lt_type = true
        Integer count = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM orders WHERE customer_id = :cid AND status IN (3, 16) AND lt_type = 1"
        ).setParameter("cid", customerId).getSingleResult()).intValue();

        em.createNativeQuery(
            "UPDATE customers SET lt_count = :count WHERE id = :cid"
        ).setParameter("count", count)
         .setParameter("cid", customerId).executeUpdate();

        log.info("Recalculated customer {}: lt_count = {}", customerId, count);
        return count;
    }

    // ============ Private helpers ============

    /**
     * Load combo groups từ bảng combo.
     * Combo: group_name = tên combo, mỗi row là 1 product trong combo.
     * 
     * @return Map<groupName, Set<productKey>>
     */
    public Map<String, Set<String>> loadComboGroups() {
        Map<String, Set<String>> result = new HashMap<>();
        List<Object[]> rows = em.createNativeQuery(
            "SELECT combo_name, product_id, variation_id FROM combo WHERE combo_type = 'LT'"
        ).getResultList();

        for (Object[] row : rows) {
            String comboName = (String) row[0];
            String key = buildKey((String) row[1], (String) row[2]);
            if (key != null) {
                result.computeIfAbsent(comboName, k -> new HashSet<>()).add(key);
            }
        }
        return result;
    }

    /**
     * Load substitution groups từ bảng product_substitutions.
     * Substitution: các sản phẩm cùng group_id có thể thay thế nhau.
     * 
     * @return Map<productKey, Set<substituteKey>>
     */
    public Map<String, Set<String>> loadSubstitutionGroups() {
        // Lấy tất cả sản phẩm trong substitution, group lại theo group_id
        Map<Long, Set<String>> groupProducts = new HashMap<>();
        
        List<Object[]> rows = em.createNativeQuery(
            "SELECT group_id, product_id, variation_id FROM product_substitutions"
        ).getResultList();

        for (Object[] row : rows) {
            Long groupId = ((Number) row[0]).longValue();
            String key = buildKey((String) row[1], (String) row[2]);
            if (key != null) {
                groupProducts.computeIfAbsent(groupId, k -> new HashSet<>()).add(key);
            }
        }

        // Build map: productKey -> các productKey có thể thay thế (cùng group)
        Map<String, Set<String>> result = new HashMap<>();
        for (Set<String> products : groupProducts.values()) {
            for (String product : products) {
                // Các sản phẩm khác trong cùng group đều có thể thay thế nhau
                for (String other : products) {
                    if (!product.equals(other)) {
                        result.computeIfAbsent(product, k -> new HashSet<>()).add(other);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Build key: "productId" hoặc "productId|variationId"
     */
    public String buildKey(String productId, String variationId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        if (variationId != null && !variationId.isBlank()) {
            return productId + "|" + variationId;
        }
        return productId;
    }

    // ============ Result DTO ============

    public record LtResult(
        boolean ltType,       // true = LT chẵn (được +1)
        int ltCount,          // lt_count hiện tại của customer
        boolean changed       // có thay đổi không
    ) {}
}
