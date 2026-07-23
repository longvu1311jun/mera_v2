package mera.mera_v2.ltkach;

import lombok.RequiredArgsConstructor;
import mera.mera_v2.entity.Combo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ltkach/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LtKhachConfigController {

    private final LtKhachConfigService service;
    private final DataSource dataSource;

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

    @PostMapping("/fix-lt-type")
    public ResponseEntity<Map<String, Object>> fixLtTypeColumn() {
        List<String> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Bước 1: Clear all data to 0
            int updated = stmt.executeUpdate("UPDATE orders SET lt_type = 0 WHERE lt_type IS NOT NULL");
            results.add("Cleared " + updated + " rows");

            // Bước 2: Modify column type
            stmt.execute("ALTER TABLE orders MODIFY COLUMN lt_type TINYINT(1) DEFAULT 0");
            results.add("Modified lt_type to TINYINT(1)");

            // Bước 3: Verify
            try (ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM orders WHERE Field = 'lt_type'")) {
                if (rs.next()) {
                    results.add("Verified: " + rs.getString("Type"));
                }
            }

        } catch (Exception e) {
            errors.add("Error: " + e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", errors.isEmpty());
        response.put("results", results);
        response.put("errors", errors);
        return ResponseEntity.ok(response);
    }

    // ---- DB Migration ----
    @PostMapping("/migrate-lt-columns")
    public ResponseEntity<Map<String, Object>> migrateLtColumns() {
        List<String> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // CUSTOMERS: Drop unused columns
            dropColumnIfExists(stmt, results, errors, "customers", "lt_real");
            dropColumnIfExists(stmt, results, errors, "customers", "lt_tay");
            dropColumnIfExists(stmt, results, errors, "customers", "lt_lark");
            dropColumnIfExists(stmt, results, errors, "customers", "lt_adjustment");
            addColumnIfNotExists(stmt, results, errors, "customers", "lt_count", "INT DEFAULT 0");

            // ORDERS: Drop unused columns
            dropColumnIfExists(stmt, results, errors, "orders", "lt_max");
            dropColumnIfExists(stmt, results, errors, "orders", "lt_type");

        } catch (Exception e) {
            errors.add("Connection error: " + e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", errors.isEmpty());
        response.put("results", results);
        response.put("errors", errors);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/db-columns")
    public ResponseEntity<Map<String, Object>> getDbColumns(@RequestParam String table) {
        List<Map<String, String>> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + table)) {
            while (rs.next()) {
                Map<String, String> col = new HashMap<>();
                col.put("field", rs.getString("Field"));
                col.put("type", rs.getString("Type"));
                columns.add(col);
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("table", table, "columns", columns));
    }

    private void dropColumnIfExists(Statement stmt, List<String> results, List<String> errors,
                                     String table, String column) {
        try {
            stmt.execute("ALTER TABLE " + table + " DROP COLUMN IF EXISTS " + column);
            results.add("Dropped: " + table + "." + column);
        } catch (Exception e) {
            errors.add("Error dropping " + table + "." + column + ": " + e.getMessage());
        }
    }

    private void addColumnIfNotExists(Statement stmt, List<String> results, List<String> errors,
                                       String table, String column, String definition) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + definition);
            results.add("Added: " + table + "." + column + " (" + definition + ")");
        } catch (Exception e) {
            if (!e.getMessage().contains("Duplicate")) {
                errors.add("Error adding " + table + "." + column + ": " + e.getMessage());
            } else {
                results.add("Already exists: " + table + "." + column);
            }
        }
    }
}
