package mera.mera_v2.pos.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dong bo san pham + bien the tu Pancake POS (GET /shops/{id}/products) vao products / product_variations.
 * Upsert theo id; khong xoa ban ghi cu vi order_items / combo dang tham chieu.
 */
@Slf4j
@Service
public class ProductSyncService {

    private static final int PAGE_SIZE = 100;
    private static final String PRODUCT_UPSERT = """
            INSERT INTO products (id, shop_id, display_id, name, brand_id, custom_id, image, is_published,
                measure_group_id, note, note_product, type, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE display_id = VALUES(display_id), name = VALUES(name), brand_id = VALUES(brand_id),
                custom_id = VALUES(custom_id), image = VALUES(image), is_published = VALUES(is_published),
                measure_group_id = VALUES(measure_group_id), note = VALUES(note), note_product = VALUES(note_product),
                type = VALUES(type), updated_at = VALUES(updated_at)""";
    private static final String VARIATION_UPSERT = """
            INSERT INTO product_variations (id, product_id, shop_id, name, display_id, barcode, retail_price,
                last_imported_price, weight, is_hidden, is_locked, is_composite, is_removed, is_sell_negative_variation,
                price_at_counter, remain_quantity, retail_price_after_discount, total_purchase_price,
                average_imported_price, inserted_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), name = VALUES(name), display_id = VALUES(display_id),
                barcode = VALUES(barcode), retail_price = VALUES(retail_price),
                last_imported_price = VALUES(last_imported_price), weight = VALUES(weight),
                is_hidden = VALUES(is_hidden), is_locked = VALUES(is_locked), is_composite = VALUES(is_composite),
                is_removed = VALUES(is_removed), is_sell_negative_variation = VALUES(is_sell_negative_variation),
                price_at_counter = VALUES(price_at_counter), remain_quantity = VALUES(remain_quantity),
                retail_price_after_discount = VALUES(retail_price_after_discount),
                total_purchase_price = VALUES(total_purchase_price),
                average_imported_price = VALUES(average_imported_price), updated_at = VALUES(updated_at)""";

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${pos.api.base-url:https://pos.pages.fm/api/v1}")
    private String baseUrl;
    @Value("${pos.api.api-key:}")
    private String apiKey;
    @Value("${pos.api.shop-id:}")
    private String shopId;

    public ProductSyncService(RestTemplate restTemplate, JdbcTemplate jdbcTemplate) {
        this.restTemplate = restTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> syncAll() throws Exception {
        if (apiKey.isBlank() || shopId.isBlank()) {
            throw new IllegalStateException("pos.api.api-key / pos.api.shop-id chua cau hinh");
        }
        Set<String> knownProducts = new HashSet<>(jdbcTemplate.queryForList("SELECT id FROM products", String.class));
        Set<String> knownVariations = new HashSet<>(
                jdbcTemplate.queryForList("SELECT id FROM product_variations", String.class));
        long shop = Long.parseLong(shopId.trim());
        int inserted = 0, updated = 0, varInserted = 0, varUpdated = 0;

        int totalPages = 1;
        for (int page = 1; page <= totalPages; page++) {
            JsonNode root = fetchPage(page);
            totalPages = root.path("total_pages").asInt(1);
            List<Object[]> productRows = new ArrayList<>();
            List<Object[]> variationRows = new ArrayList<>();
            for (JsonNode p : root.path("data")) {
                String productId = text(p, "id");
                if (productId == null) continue;
                if (knownProducts.add(productId)) inserted++; else updated++;
                LocalDateTime pInserted = time(p, "inserted_at");
                productRows.add(new Object[]{productId, shop, text(p, "display_id"), text(p, "name"),
                        text(p, "brand_id"), text(p, "custom_id"), text(p, "image"), bool(p, "is_published"),
                        text(p, "measure_group_id"), text(p, "note"), text(p, "note_product"), text(p, "type"),
                        orNow(pInserted), LocalDateTime.now()});
                for (JsonNode v : p.path("variations")) {
                    String variationId = text(v, "id");
                    if (variationId == null) continue;
                    if (knownVariations.add(variationId)) varInserted++; else varUpdated++;
                    LocalDateTime vInserted = time(v, "inserted_at");
                    variationRows.add(new Object[]{variationId, productId, shop, variationName(p, v),
                            text(v, "display_id"), text(v, "barcode"), num(v, "retail_price", 0d),
                            num(v, "last_imported_price", 0d), num(v, "weight", null), intBool(v, "is_hidden"),
                            intBool(v, "is_locked"), bool(v, "is_composite"), bool(v, "is_removed"),
                            bool(v, "is_sell_negative_variation"), num(v, "price_at_counter", null),
                            v.path("remain_quantity").isNumber() ? v.get("remain_quantity").asInt() : null,
                            num(v, "retail_price_after_discount", null), num(v, "total_purchase_price", null),
                            num(v, "average_imported_price", null), vInserted, orNow(vInserted),
                            orNow(time(v, "updated_at"))});
                }
            }
            // products truoc vi fk_var_product
            jdbcTemplate.batchUpdate(PRODUCT_UPSERT, productRows);
            jdbcTemplate.batchUpdate(VARIATION_UPSERT, variationRows);
            log.info("[product-sync] page {}/{}: {} products, {} variations", page, totalPages,
                    productRows.size(), variationRows.size());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("variationsInserted", varInserted);
        result.put("variationsUpdated", varUpdated);
        log.info("[product-sync] done: {}", result);
        return result;
    }

    private JsonNode fetchPage(int page) throws Exception {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/shops/" + shopId.trim() + "/products")
                .queryParam("api_key", apiKey)
                .queryParam("page_size", PAGE_SIZE)
                .queryParam("page_number", page)
                .build().toUriString();
        JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));
        if (!root.path("success").asBoolean(false)) {
            throw new IllegalStateException("POS products API loi: " + root.path("message").asText("unknown"));
        }
        return root;
    }

    /** Ten bien the = ten san pham + gia tri thuoc tinh (VD "Ao - Do - XL"); khong co thuoc tinh thi = ten SP. */
    private static String variationName(JsonNode product, JsonNode variation) {
        StringBuilder sb = new StringBuilder(product.path("name").asText(""));
        for (JsonNode f : variation.path("fields")) {
            String value = text(f, "value");
            if (value != null && !value.isBlank()) sb.append(" - ").append(value);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Double num(JsonNode n, String f, Double def) {
        JsonNode v = n.get(f);
        return v != null && v.isNumber() ? v.asDouble() : def;
    }

    private static Boolean bool(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v != null && v.isBoolean() ? v.asBoolean() : null;
    }

    private static Integer intBool(JsonNode n, String f) {
        Boolean b = bool(n, f);
        return b == null ? null : (b ? 1 : 0);
    }

    // Luu nguyen gia tri POS tra ve (giong OrderSyncService.parseDateTime)
    private static LocalDateTime time(JsonNode n, String f) {
        String s = text(n, f);
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime orNow(LocalDateTime t) {
        return t != null ? t : LocalDateTime.now();
    }
}
