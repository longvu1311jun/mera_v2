package mera.mera_v2.customer.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import mera.mera_v2.model.BitableRecord;
import mera.mera_v2.model.UserConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import mera.mera_v2.lark.wiki.LarkWikiService;
import mera.mera_v2.model.BitableTable;
import mera.mera_v2.model.PosUser;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final String SESSION_USER_CONFIGS = "SESSION_USER_CONFIGS";
    private static final String TRAO_DOI_VIEW_ID = "vewNXdsB3K";

    @Value("${pos.api.base-url}")
    private String baseUrl;

    @Value("${pos.api.api-key}")
    private String apiKey;

    @Value("${pos.api.shop-id}")
    private String shopId;

    private final ObjectMapper objectMapper;
    private final BitableService bitableService;
    private final LarkWikiService larkWikiService;
    private final PosService posService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public Map<String, Object> searchCustomer360(String phone, HttpSession session) throws Exception {
        // Ensure configs are loaded in session
        ensureUserConfigsStored(session);

        String sanitizedPhone = phone.replaceAll("\\D", "").trim();
        if (sanitizedPhone.isBlank()) {
            throw new IllegalArgumentException("Số điện thoại không hợp lệ");
        }

        // --- GỌI SONG SONG ---
        CompletableFuture<Map<String, Object>> posCustomerFuture = CompletableFuture.supplyAsync(() -> {
            try { return fetchCustomerInfo(sanitizedPhone); } catch (Exception e) { return null; }
        }, executorService);

        CompletableFuture<List<Map<String, Object>>> posOrdersFuture = CompletableFuture.supplyAsync(() -> {
            try { return fetchOrderInfos(sanitizedPhone); } catch (Exception e) { return Collections.emptyList(); }
        }, executorService);

        CompletableFuture<List<Map<String, Object>>> larkExchangesFuture = CompletableFuture.supplyAsync(() -> {
            try { return fetchLarkExchanges(sanitizedPhone, session); } catch (Exception e) { return Collections.emptyList(); }
        }, executorService);

        // Đợi tất cả hoàn thành
        CompletableFuture.allOf(posCustomerFuture, posOrdersFuture, larkExchangesFuture).join();

        Map<String, Object> posCustomer = posCustomerFuture.get();
        List<Map<String, Object>> orders = posOrdersFuture.get();
        List<Map<String, Object>> exchanges = larkExchangesFuture.get();

        // Nếu cả 3 đều rỗng thì mới coi là không thấy
        if (posCustomer == null && orders.isEmpty() && exchanges.isEmpty()) {
            return null;
        }

        Map<String, Object> customer = posCustomer;
        // Tạo customer object giả nếu POS không có thông tin nhưng có dữ liệu khác
        if (customer == null) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("name", "Khách hàng (Lark)");
            fallback.put("phone", phone);
            fallback.put("succeedOrderCount", orders.size());
            fallback.put("posNotes", Collections.emptyList());
            // Thử lấy tên từ node của Lark nếu có thể
            try {
                larkWikiService.getAllNodesWithChildren(session).stream()
                    .filter(node -> node.getTitle() != null && node.getTitle().contains(phone))
                    .findFirst()
                    .ifPresent(matchedNode -> {
                        String cleanName = matchedNode.getTitle().replaceAll("\\d", "").replace("-", "").trim();
                        if (!cleanName.isBlank()) fallback.put("name", cleanName);
                    });
            } catch (Exception e) {}
            customer = fallback;
        }

        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("customer", customer);
        finalResult.put("orders", orders);
        finalResult.put("exchanges", exchanges);
        
        // Thêm tóm tắt sản phẩm
        finalResult.put("productSummary", summarizeProducts(orders));

        return finalResult;
    }

    private List<Map<String, Object>> summarizeProducts(List<Map<String, Object>> orders) {
        Map<String, Integer> summaryMap = new HashMap<>();
        for (Map<String, Object> order : orders) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) order.get("items");
            if (items != null) {
                for (Map<String, Object> item : items) {
                    String name = (String) item.get("name");
                    int qty = (int) item.getOrDefault("quantity", 1);
                    if (name != null) {
                        summaryMap.put(name, summaryMap.getOrDefault(name, 0) + qty);
                    }
                }
            }
        }
        return summaryMap.entrySet().stream()
            .map(e -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", e.getKey());
                m.put("quantity", e.getValue());
                return m;
            })
            .sorted((a, b) -> ((Integer) b.get("quantity")).compareTo((Integer) a.get("quantity")))
            .collect(Collectors.toList());
    }

    private Map<String, Object> fetchCustomerInfo(String phone) throws Exception {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/shops/" + shopId + "/customers")
                .queryParam("page_size", 20)
                .queryParam("search", phone)
                .queryParam("api_key", apiKey)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode dataNode = root.path("data");

        if (!dataNode.isArray() || dataNode.isEmpty()) return null;

        // BƯỚC 1: Tìm ID khách hàng
        JsonNode firstMatch = dataNode.get(0);
        for (JsonNode item : dataNode) {
            JsonNode phones = item.path("phone_numbers");
            if (phones.isArray()) {
                for (JsonNode p : phones) {
                    if (p.asText().replaceAll("\\D", "").contains(phone)) {
                        firstMatch = item;
                        break;
                    }
                }
            }
        }

        String actualId = asText(firstMatch, "customer_id");
        if (actualId == null) actualId = asText(firstMatch, "id");
        
        log.info("🔍 Step 1: Found ID {} for phone {}. Fetching full details...", actualId, phone);

        // BƯỚC 2: Gọi trực tiếp API chi tiết khách hàng để lấy FULL NOTES
        String detailUrl = UriComponentsBuilder.fromUriString(baseUrl + "/shops/" + shopId + "/customers/" + actualId)
                .queryParam("api_key", apiKey)
                .toUriString();
        
        ResponseEntity<String> detailRes = restTemplate.exchange(detailUrl, HttpMethod.GET, entity, String.class);
        JsonNode detailRoot = objectMapper.readTree(detailRes.getBody());
        
        // Pancake trả về bọc trong field "data" cho endpoint chi tiết
        JsonNode c = detailRoot.has("data") ? detailRoot.get("data") : detailRoot;
        
        log.info("📊 Processing detailed customer data: {}", c.toString());

        Map<String, Object> info = new HashMap<>();
        info.put("customerId", asText(c, "customer_id"));
        info.put("name", asText(c, "name"));
        info.put("phone", extractPhone(c.path("phone_numbers"), phone));
        String address = null;
        JsonNode addrNode = c.path("shop_customer_addresses");
        if (addrNode.isArray() && !addrNode.isEmpty()) {
            JsonNode firstAddr = addrNode.get(0);
            address = asText(firstAddr, "full_address");
            if (address == null || address.isBlank()) address = asText(firstAddr, "address");
        }
        
        if (address == null || address.isBlank()) address = asText(c, "full_address");
        if (address == null || address.isBlank()) address = asText(c, "address");
        info.put("fullAddress", address);
        info.put("succeedOrderCount", c.path("succeed_order_count").asInt(0));

        List<Map<String, Object>> posNotes = new ArrayList<>();
        JsonNode notesNode = c.path("notes");
        if (notesNode.isArray()) {
            for (JsonNode n : notesNode) {
                Map<String, Object> note = new HashMap<>();
                note.put("message", asText(n, "message"));
                note.put("createdAt", formatNoteDate(n.get("created_at")));
                
                // Trích xuất tên người tạo từ created_by.fb_name
                JsonNode creator = n.path("created_by");
                note.put("userName", asText(creator, "fb_name"));
                
                posNotes.add(note);
            }
        }
        info.put("posNotes", posNotes);
        log.info("✅ Found POS Customer: {} (ID: {}, Phone: {})", info.get("name"), info.get("customerId"), info.get("phone"));
        log.info("📊 Full Data from POS: {}", c.toString());
        return info;
    }

    private List<Map<String, Object>> fetchOrderInfos(String phone) throws Exception {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/shops/" + shopId + "/orders")
                .queryParam("page_size", 50)
                .queryParam("search", phone)
                .queryParam("api_key", apiKey)
                .toUriString();

        List<Map<String, Object>> ordersList = new ArrayList<>();
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode dataNode = root.path("data");

        if (dataNode.isArray()) {
            for (JsonNode o : dataNode) {
                Map<String, Object> order = new HashMap<>();
                order.put("orderId", extractOrderIdFromLink(asText(o, "order_link")));
                order.put("status", o.path("status").asInt(0));
                order.put("timeAssignSeller", asText(o, "time_assign_seller"));
                
                // Logic phân loại: Nếu nguồn không phải FB thì CSKH = Seller
                String source = asText(o, "order_sources_name");
                String seller = o.path("assigning_seller").path("name").asText("-");
                String care = o.path("assigning_care").path("name").asText("-");
                String sellerPhone = o.path("assigning_seller").path("phone_number").asText("");
                String carePhone = o.path("assigning_care").path("phone_number").asText("");

                if (source != null && !source.toLowerCase().contains("fanpage") && !source.toLowerCase().contains("facebook")) {
                    order.put("cskhName", seller);
                    order.put("cskhPhone", sellerPhone);
                    order.put("saleName", seller);
                } else {
                    order.put("cskhName", care);
                    order.put("cskhPhone", carePhone);
                    order.put("saleName", seller);
                }

                // Items
                List<Map<String, Object>> items = new ArrayList<>();
                for (JsonNode it : o.path("items")) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("quantity", it.path("quantity").asInt(1));
                    String name = asText(it.path("variation_info"), "name");
                    if (name == null) name = asText(it, "product_name");
                    item.put("name", name);
                    items.add(item);
                }
                order.put("items", items);
                ordersList.add(order);
            }
        }
        return ordersList;
    }

    /**
     * Tìm kiếm trao đổi từ Lark Bitable (Search qua tất cả bases được cấu hình)
     */
    private List<Map<String, Object>> fetchLarkExchanges(String phone, HttpSession session) {
        List<UserConfigDto> userConfigs = (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);
        if (userConfigs == null || userConfigs.isEmpty()) {
            log.warn("⚠️ No user configs found in session for exchange search.");
            return Collections.emptyList();
        }

        List<Map<String, Object>> allExchanges = new ArrayList<>();
        log.info("🔍 Searching exchanges for phone {} across {} configs", phone, userConfigs.size());
        
        // Chạy song song search trên từng Base của từng Sale
        List<CompletableFuture<Void>> futures = userConfigs.stream()
            .filter(c -> c.getBaseId() != null && !c.getBaseId().isBlank())
            .map(config -> CompletableFuture.runAsync(() -> {
            try {
                if (config.getKhachHangTableId().isEmpty()) {
                    log.debug("Skipping config for base {} (No Khách Hàng table ID)", config.getBaseId());
                    return;
                }
                
                // 1. Tìm bản ghi khách hàng để lấy record_id
                List<BitableRecord> customers = bitableService.searchCustomerByPhone(session, config.getBaseId(), config.getKhachHangTableId(), phone, null);
                if (customers != null && !customers.isEmpty()) {
                    String recordId = customers.get(0).getRecordId();
                    String larkCustomerName = extractText(customers.get(0).getFields().get("Họ và tên"));
                    if (larkCustomerName.isEmpty()) larkCustomerName = extractText(customers.get(0).getFields().get("Tên khách hàng"));
                    
                    log.info("✅ Found Lark Customer in Base {}: {} (RecordID: {})", config.getLarkName(), larkCustomerName, recordId);
                    log.info("📊 Full Fields from Lark: {}", customers.get(0).getFields());
                    
                    if (config.getTraoDoiTableId().isEmpty()) {
                        log.info("ℹ️ Virtual exchange for Base {}", config.getLarkName());
                        Map<String, Object> virtualEx = new HashMap<>();
                        virtualEx.put("content", "⚠️ Khách hàng nằm trong Base [" + config.getLarkName() + "]");
                        virtualEx.put("date", "-");
                        virtualEx.put("person", "System");
                        virtualEx.put("source", config.getLarkName());
                        synchronized (allExchanges) {
                            allExchanges.add(virtualEx);
                        }
                        return;
                    }

                    // 2. Tìm các bản ghi Trao đổi liên kết với record_id này
                    // Thử tìm với View ID trước, nếu không được (hoặc không có view đó) thì tìm không View
                    List<BitableRecord> exchanges = null;
                    try {
                        exchanges = bitableService.searchRecordsByCustomerId(session, config.getBaseId(), config.getTraoDoiTableId(), recordId, null, TRAO_DOI_VIEW_ID);
                    } catch (Exception e) {
                        log.debug("View ID search failed for base {}, trying without view ID", config.getBaseId());
                    }

                    if (exchanges == null || exchanges.isEmpty()) {
                        exchanges = bitableService.searchRecordsByCustomerId(session, config.getBaseId(), config.getTraoDoiTableId(), recordId, null, null);
                    }
                    
                    if (exchanges != null && !exchanges.isEmpty()) {
                        log.info("📥 Found {} exchanges in base {}", exchanges.size(), config.getBaseId());
                        synchronized (allExchanges) {
                            for (BitableRecord rec : exchanges) {
                                Map<String, Object> f = rec.getFields();
                                Map<String, Object> ex = new HashMap<>();
                                
                                // Tìm kiếm linh hoạt field name
                                Object contentVal = findValue(f, List.of("Nội dung", "Nội dung trao đổi", "Trao đổi", "Content", "Ghi chú", "Lưu ý", "Note"));
                                Object dateVal = findValue(f, List.of("Ngày", "Ngày tạo", "Thời gian", "Date", "Created At"));
                                Object personVal = findValue(f, List.of("Người thực hiện", "Nhân viên", "Người tạo", "Person", "User", "Sale"));
                                
                                ex.put("content", extractText(contentVal));
                                ex.put("date", extractText(dateVal));
                                ex.put("person", extractText(personVal));
                                ex.put("source", config.getLarkName()); // Thêm tên Base nguồn
                                allExchanges.add(ex);
                            }
                        }
                    } else {
                        log.info("ℹ️ Customer exists but no exchanges in Base {}. Adding placeholder.", config.getLarkName());
                        Map<String, Object> virtualEx = new HashMap<>();
                        virtualEx.put("content", "⚠️ Khách hàng nằm trong Base [" + config.getLarkName() + "] (Chưa có nhật ký)");
                        virtualEx.put("date", "-");
                        virtualEx.put("person", "System");
                        virtualEx.put("source", config.getLarkName());
                        synchronized (allExchanges) {
                            allExchanges.add(virtualEx);
                        }
                    }
                } else {
                    log.debug("ℹ️ No customer found with phone {} in base {}", phone, config.getBaseId());
                }
            } catch (Exception e) {
                log.warn("❌ Error fetching exchanges for base {}: {}", config.getBaseId(), e.getMessage());
            }
        }, executorService)).collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Sắp xếp theo ngày mới nhất (parse date để sort chính xác)
        return allExchanges.stream()
                .sorted((a, b) -> parseAndCompareDates((String) b.getOrDefault("date", ""), (String) a.getOrDefault("date", "")))
                .collect(Collectors.toList());
    }

    private int parseAndCompareDates(String d1, String d2) {
        if (d1 == null || d1.isEmpty()) return 1;
        if (d2 == null || d2.isEmpty()) return -1;
        try {
            // Thử parse ISO format trước (2024-03-01...)
            if (d1.contains("-") && d1.indexOf("-") < 5) {
                return d1.compareTo(d2);
            }
            // Thử parse Vietnam format (dd/MM/yyyy...)
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
            return sdf.parse(d1).compareTo(sdf.parse(d2));
        } catch (Exception e) {
            return d1.compareTo(d2);
        }
    }

    /**
     * Tự động nạp cấu hình UserConfigDto vào session nếu chưa có.
     * Copy logic từ authenController.loadAndCacheData
     */
    public void ensureUserConfigsStored(HttpSession session) {
        if (session.getAttribute(SESSION_USER_CONFIGS) != null) return;
        
        log.info("ℹ️ SESSION_USER_CONFIGS missing. Loading in background...");
        try {
            larkWikiService.getAllNodesWithChildren(session);
            List<PosUser> posUsers = posService.getUsers();
            Map<PosUser, mera.mera_v2.model.LarkNode> matchedMap = larkWikiService.matchUsersWithNodes(posUsers, session);

            List<UserConfigDto> userConfigs = new ArrayList<>();
            for (PosUser posUser : posUsers) {
                mera.mera_v2.model.LarkNode matchedNode = matchedMap.get(posUser);
                UserConfigDto userConfig = new UserConfigDto(posUser, matchedNode);
                
                String baseId = userConfig.getBaseId();
                if (baseId != null && !baseId.isBlank()) {
                    loadTableConfigs(session, userConfig);
                }
                userConfigs.add(userConfig);
            }
            
            // --- THÊM 3 BASE ĐẶC BIỆT: TỪ CHỐI CHĂM, ĐƠN HOÀN, HỦY ---
            List<String> specialTitles = List.of("TỪ CHỐI CHĂM", "Đơn hoàn", "Hủy");
            List<mera.mera_v2.model.LarkNode> allNodes = larkWikiService.getAllNodesWithChildren(session);
            for (mera.mera_v2.model.LarkNode node : allNodes) {
                if (node.getObjToken() == null) continue;
                String title = node.getTitle() == null ? "" : node.getTitle().trim().toUpperCase();
                
                // Nếu tiêu đề base chứa 1 trong các từ khóa đặc biệt & chưa có trong list hiện tại
                if (specialTitles.stream().anyMatch(st -> title.contains(st.toUpperCase()))) {
                    boolean alreadyExists = userConfigs.stream().anyMatch(uc -> node.getObjToken().equals(uc.getBaseId()));
                    if (!alreadyExists) {
                        UserConfigDto specialConfig = new UserConfigDto(null, node);
                        loadTableConfigs(session, specialConfig);
                        userConfigs.add(specialConfig);
                    }
                }
            }
            
            session.setAttribute(SESSION_USER_CONFIGS, userConfigs);
            log.info("✅ SESSION_USER_CONFIGS loaded successfully: {} total configs", userConfigs.size());
        } catch (Exception e) {
            log.error("❌ Failed to background-load user configs", e);
        }
    }

    private void loadTableConfigs(HttpSession session, UserConfigDto config) {
        try {
            List<BitableTable> tables = bitableService.getTablesByBaseId(session, config.getBaseId());
            for (BitableTable table : tables) {
                String tableName = table.getName() == null ? "" : table.getName().trim().toLowerCase();
                String tableId = table.getTableId();
                
                if (tableName.contains("khách hàng")) config.setKhachHangTableId(tableId);
                else if (tableName.contains("lịch hẹn") || tableName.contains("lịch làm")) config.setLichHenTableId(tableId);
                else if (tableName.contains("trao đổi") || tableName.contains("nhật ký")) config.setTraoDoiTableId(tableId);
            }
        } catch (Exception e) {
            log.warn("Failed to get tables for baseId {}: {}", config.getBaseId(), e.getMessage());
        }
    }

    private Object findValue(Map<String, Object> fields, List<String> hints) {
        if (fields == null) return null;
        for (String hint : hints) {
            if (fields.containsKey(hint)) return fields.get(hint);
            // Case-insensitive check
            for (String key : fields.keySet()) {
                if (key.equalsIgnoreCase(hint)) return fields.get(key);
            }
        }
        return null;
    }

    private String asText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.get(field) == null || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    private String extractPhone(JsonNode phonesNode, String searchedPhone) {
        if (!phonesNode.isArray() || phonesNode.isEmpty()) return "-";
        
        // Ưu tiên trả về số điện thoại đã search nếu nó nằm trong danh sách
        for (JsonNode p : phonesNode) {
            String pVal = p.asText();
            if (pVal.replaceAll("\\D", "").contains(searchedPhone)) {
                return pVal;
            }
        }
        
        // Fallback về số đầu tiên
        return phonesNode.get(0).asText();
    }

    private String formatNoteDate(JsonNode ca) {
        if (ca == null || ca.isNull()) return "";
        if (ca.isNumber()) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
            return sdf.format(new java.util.Date(ca.asLong()));
        }
        return ca.asText();
    }

    private String extractOrderIdFromLink(String link) {
        if (link == null || !link.contains("order_id=")) return null;
        return link.substring(link.indexOf("order_id=") + 9);
    }

    private String extractText(Object v) {
        if (v == null) return "";
        if (v instanceof String s) return s;
        if (v instanceof List<?> list) {
             return list.stream()
                 .map(this::extractText)
                 .filter(s -> !s.isBlank())
                 .collect(Collectors.joining(", "));
        }
        if (v instanceof Map<?,?> map) {
            if (map.containsKey("text")) return String.valueOf(map.get("text"));
            if (map.containsKey("name")) return String.valueOf(map.get("name"));
            return map.toString();
        }
        return v.toString();
    }
}
