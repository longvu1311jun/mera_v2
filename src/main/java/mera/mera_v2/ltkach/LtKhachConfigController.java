package mera.mera_v2.ltkach;

import lombok.RequiredArgsConstructor;
import mera.mera_v2.entity.Combo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ltkach/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LtKhachConfigController {

    private final LtKhachConfigService service;

    // ---- Products ----
    @GetMapping("/products")
    public ResponseEntity<List<Map<String, Object>>> getProducts() {
        return ResponseEntity.ok(service.getProducts());
    }

    // ---- Substitution Groups ----
    @GetMapping("/substitution-groups")
    public ResponseEntity<List<Map<String, Object>>> getSubstitutionGroups() {
        return ResponseEntity.ok(service.getSubstitutionGroups());
    }

    @PostMapping("/substitution-groups")
    public ResponseEntity<Map<String, Object>> createSubstitutionGroup(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        String groupName = (String) body.getOrDefault("groupName", "");
        return ResponseEntity.ok(service.upsertSubstitutionGroup(null, groupName, items));
    }

    @PutMapping("/substitution-groups/{id}")
    public ResponseEntity<Map<String, Object>> updateSubstitutionGroup(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        String groupName = (String) body.getOrDefault("groupName", "");
        return ResponseEntity.ok(service.upsertSubstitutionGroup(id, groupName, items));
    }

    @DeleteMapping("/substitution-groups/{id}")
    public ResponseEntity<Map<String, Object>> deleteSubstitutionGroup(@PathVariable Long id) {
        boolean ok = service.deleteSubstitutionGroup(id);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    // ---- Combos (Cấu hình LT) ----
    @GetMapping("/combos")
    public ResponseEntity<List<Map<String, Object>>> getCombos() {
        return ResponseEntity.ok(service.getCombos());
    }

    /**
     * Lưu combo mới/cập nhật: nhận body grouped.
     * Body: { comboName, comboPrice, items: [{productId, variationId, productName, unitPrice, quantity, amount}, ...] }
     */
    @PostMapping("/combos")
    public ResponseEntity<Map<String, Object>> saveComboGrouped(@RequestBody Map<String, Object> body) {
        // Nếu body có trường "items" → dùng logic nhóm mới
        if (body.containsKey("items")) {
            return ResponseEntity.ok(service.saveComboGrouped(body));
        }
        // Legacy: body là Combo entity đơn lẻ
        Combo c = new Combo();
        c.setId(body.get("id") != null ? ((Number) body.get("id")).longValue() : null);
        c.setComboName((String) body.getOrDefault("comboName", ""));
        c.setProductId((String) body.get("productId"));
        c.setProductName((String) body.getOrDefault("productName", ""));
        c.setUnitPrice(body.get("unitPrice") != null ? new java.math.BigDecimal(body.get("unitPrice").toString()) : java.math.BigDecimal.ZERO);
        c.setQuantity(body.get("quantity") != null ? ((Number) body.get("quantity")).intValue() : 1);
        c.setAmount(body.get("amount") != null ? new java.math.BigDecimal(body.get("amount").toString()) : java.math.BigDecimal.ZERO);
        c.setComboPrice(body.get("comboPrice") != null ? new java.math.BigDecimal(body.get("comboPrice").toString()) : java.math.BigDecimal.ZERO);
        c.setComboType("LT");
        Combo saved = service.saveCombo(c);
        return ResponseEntity.ok(Map.of("success", true, "id", saved.getId()));
    }

    /**
     * Xóa combo theo comboName (xóa tất cả dòng cùng comboName).
     */
    @DeleteMapping("/combos/by-name")
    public ResponseEntity<Map<String, Object>> deleteComboByName(@RequestBody Map<String, Object> body) {
        String comboName = (String) body.getOrDefault("comboName", "");
        boolean ok = service.deleteComboByName(comboName);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    /**
     * Legacy: xóa 1 dòng theo id.
     */
    @DeleteMapping("/combos/{id}")
    public ResponseEntity<Map<String, Object>> deleteCombo(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", service.deleteCombo(id)));
    }

}
