package mera.mera_v2.ltkach;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import mera.mera_v2.entity.Combo;
import mera.mera_v2.entity.Product;
import mera.mera_v2.entity.ProductSubstitution;
import mera.mera_v2.entity.ProductVariation;
import mera.mera_v2.repository.ComboRepository;
import mera.mera_v2.repository.ProductRepository;
import mera.mera_v2.repository.ProductSubstitutionRepository;
import mera.mera_v2.repository.ProductVariationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Service cho Cấu hình LT (Combo) và Thuốc thay thế (Substitution groups).
 * - Substitution group: MERA lưu dạng rows trong product_substitutions với group_id, group_name.
 *   Service gom nhóm lại để trả về cấu trúc {groupId, groupName, items:[{productId, productName, variationId, variationName, quantity}]}.
 */
@Service
public class LtKhachConfigService {

    private static final Logger log = LoggerFactory.getLogger(LtKhachConfigService.class);

    @PersistenceContext
    private EntityManager em;

    private final ProductRepository productRepository;
    private final ProductVariationRepository variationRepository;
    private final ProductSubstitutionRepository substitutionRepository;
    private final ComboRepository comboRepository;

    public LtKhachConfigService(ProductRepository productRepository,
                                ProductVariationRepository variationRepository,
                                ProductSubstitutionRepository substitutionRepository,
                                ComboRepository comboRepository) {
        this.productRepository = productRepository;
        this.variationRepository = variationRepository;
        this.substitutionRepository = substitutionRepository;
        this.comboRepository = comboRepository;
    }

    // ============ Substitution Groups ============

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSubstitutionGroups() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // Lấy tất cả rows, gom theo group_id
            List<ProductSubstitution> rows = substitutionRepository.findAll();
            // Build product/variation lookup
            Map<String, String> productNameMap = new HashMap<>();
            for (Product p : productRepository.findAll()) {
                productNameMap.put(p.getId(), p.getName());
            }
            Map<String, String> variationNameMap = new HashMap<>();
            for (ProductVariation v : variationRepository.findAll()) {
                variationNameMap.put(v.getId(), v.getName() != null ? v.getName() : v.getId());
            }
            Map<Long, Map<String, Object>> groupMap = new LinkedHashMap<>();
            for (ProductSubstitution r : rows) {
                long gid = r.getGroupId() != null ? r.getGroupId() : 0L;
                Map<String, Object> grp = groupMap.computeIfAbsent(gid, k -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("groupId", gid);
                    g.put("groupName", r.getGroupName());
                    g.put("items", new ArrayList<Map<String, Object>>());
                    return g;
                });
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) grp.get("items");
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("id", r.getId());
                it.put("productId", r.getProductId());
                it.put("productName", productNameMap.getOrDefault(r.getProductId(), r.getProductId()));
                it.put("variationId", r.getVariationId());
                it.put("variationName", r.getVariationId() != null ? variationNameMap.getOrDefault(r.getVariationId(), "") : "");
                it.put("quantity", r.getQuantity());
                items.add(it);
            }
            result.addAll(groupMap.values());
        } catch (Exception e) {
            log.error("Error getting substitution groups", e);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> upsertSubstitutionGroup(Long groupId, String groupName, List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            long gid = groupId != null ? groupId : 0L;
            if (groupId == null || groupId == 0L) {
                // Tạo groupId mới
                Number maxId = (Number) em.createNativeQuery("SELECT COALESCE(MAX(group_id), 0) FROM product_substitutions").getSingleResult();
                gid = (maxId == null ? 0 : maxId.longValue()) + 1L;
            } else {
                // Xóa các rows cũ trong group trước khi update
                em.createNativeQuery("DELETE FROM product_substitutions WHERE group_id = :gid")
                  .setParameter("gid", gid).executeUpdate();
            }
            for (Map<String, Object> it : items) {
                ProductSubstitution ps = new ProductSubstitution();
                ps.setGroupId(gid);
                ps.setGroupName(groupName);
                ps.setProductId((String) it.get("productId"));
                ps.setVariationId((String) it.get("variationId"));
                ps.setQuantity(it.get("quantity") != null ? ((Number) it.get("quantity")).intValue() : 1);
                ps.setCreatedAt(java.time.LocalDateTime.now());
                ps.setUpdatedAt(java.time.LocalDateTime.now());
                em.persist(ps);
            }
            result.put("success", true);
            result.put("groupId", gid);
        } catch (Exception e) {
            log.error("Error upserting substitution group", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Transactional
    public boolean deleteSubstitutionGroup(Long groupId) {
        try {
            int n = em.createNativeQuery("DELETE FROM product_substitutions WHERE group_id = :gid")
                     .setParameter("gid", groupId).executeUpdate();
            return n > 0;
        } catch (Exception e) {
            log.error("Error deleting substitution group", e);
            return false;
        }
    }

    // ============ Products ============

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProducts() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            for (Product p : productRepository.findAll()) {
                if (p.getIsComposite() != null && p.getIsComposite() == 1) continue;
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", p.getId());
                dto.put("name", p.getName());
                dto.put("code", p.getDisplayId());
                List<Map<String, Object>> variations = new ArrayList<>();
                for (ProductVariation v : p.getVariations()) {
                    Map<String, Object> vDto = new LinkedHashMap<>();
                    vDto.put("id", v.getId());
                    vDto.put("name", v.getName());
                    vDto.put("productId", p.getId());
                    vDto.put("retailPrice", v.getRetailPrice());
                    variations.add(vDto);
                }
                dto.put("variations", variations);
                result.add(dto);
            }
        } catch (Exception e) {
            log.error("Error getting products", e);
        }
        return result;
    }

    // ============ Combos (Cấu hình LT) ============
    // DB: 1 dòng = 1 sản phẩm trong combo. Nhiều dòng cùng comboName = 1 combo.
    // combo_price chỉ lưu ở dòng đầu tiên (id nhỏ nhất) của mỗi comboName.

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCombos() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<Object[]> rows = em.createNativeQuery(
                "SELECT id, combo_name, product_id, variation_id, product_name, " +
                "       unit_price, quantity, amount, combo_price " +
                "FROM combo ORDER BY combo_name, id"
            ).getResultList();

            // Gom nhóm theo comboName
            Map<String, Map<String, Object>> groupMap = new LinkedHashMap<>();
            for (Object[] r : rows) {
                String comboName = r[1] != null ? (String) r[1] : "";
                Map<String, Object> combo = groupMap.computeIfAbsent(comboName, k -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("comboName", comboName);
                    c.put("comboPrice", r[8]); // combo_price (chỉ dòng đầu có giá trị)
                    c.put("items", new ArrayList<Map<String, Object>>());
                    return c;
                });
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) combo.get("items");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r[0]);
                item.put("productId", r[2]);
                item.put("variationId", r[3]);
                item.put("productName", r[4]);
                item.put("unitPrice", r[5]);
                item.put("quantity", r[6] != null ? ((Number) r[6]).intValue() : 1);
                item.put("amount", r[7]);
                items.add(item);
            }
            result.addAll(groupMap.values());
        } catch (Exception e) {
            log.error("Error getting combos", e);
        }
        return result;
    }

    /**
     * Lưu combo mới hoặc cập nhật combo theo comboName.
     * Xóa tất cả dòng cũ có cùng comboName, rồi insert lại từng sản phẩm.
     * comboPrice chỉ ghi vào dòng đầu tiên (id nhỏ nhất sẽ được tạo).
     *
     * Body: {
     *   "comboName": "...",
     *   "comboPrice": 500000,
     *   "items": [{ productId, variationId, productName, unitPrice, quantity, amount }, ...]
     * }
     * Hoặc body cũ (legacy): Combo entity đơn lẻ (dùng cho tương thích ngược).
     */
    @Transactional
    public Map<String, Object> saveComboGrouped(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String comboName = (String) body.getOrDefault("comboName", "");
            if (comboName == null || comboName.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "comboName is required");
                return result;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
            BigDecimal comboPrice = body.get("comboPrice") != null
                ? new BigDecimal(body.get("comboPrice").toString()) : BigDecimal.ZERO;

            // Xóa dòng cũ có cùng comboName
            em.createNativeQuery("DELETE FROM combo WHERE combo_name = :cn")
              .setParameter("cn", comboName).executeUpdate();

            // Insert từng sản phẩm
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> it = items.get(i);
                Combo c = new Combo();
                c.setComboName(comboName);
                c.setProductId((String) it.get("productId"));
                c.setVariationId((String) it.get("variationId"));
                c.setProductName((String) it.getOrDefault("productName", ""));
                c.setUnitPrice(it.get("unitPrice") != null
                    ? new BigDecimal(it.get("unitPrice").toString()) : BigDecimal.ZERO);
                c.setQuantity(it.get("quantity") != null
                    ? ((Number) it.get("quantity")).intValue() : 1);
                c.setAmount(it.get("amount") != null
                    ? new BigDecimal(it.get("amount").toString()) : BigDecimal.ZERO);
                // combo_price chỉ ghi ở dòng đầu tiên
                c.setComboPrice(i == 0 ? comboPrice : BigDecimal.ZERO);
                c.setComboType("LT");
                c.setCreatedAt(java.time.LocalDateTime.now());
                c.setUpdatedAt(java.time.LocalDateTime.now());
                em.persist(c);
            }

            result.put("success", true);
            result.put("comboName", comboName);
            result.put("itemCount", items.size());
        } catch (Exception e) {
            log.error("Error saving combo grouped", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Xóa combo theo comboName (xóa tất cả dòng cùng comboName).
     */
    @Transactional
    public boolean deleteComboByName(String comboName) {
        try {
            int n = em.createNativeQuery("DELETE FROM combo WHERE combo_name = :cn")
                     .setParameter("cn", comboName).executeUpdate();
            return n > 0;
        } catch (Exception e) {
            log.error("Error deleting combo by name", e);
            return false;
        }
    }

    // Legacy: lưu 1 dòng Combo đơn lẻ (giữ lại cho tương thích)
    @Transactional
    public Combo saveCombo(Combo combo) {
        if (combo.getCreatedAt() == null) combo.setCreatedAt(java.time.LocalDateTime.now());
        combo.setUpdatedAt(java.time.LocalDateTime.now());
        return comboRepository.save(combo);
    }

    // Legacy: xóa 1 dòng theo id
    @Transactional
    public boolean deleteCombo(Long id) {
        try {
            comboRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            log.error("Error deleting combo", e);
            return false;
        }
    }
}
