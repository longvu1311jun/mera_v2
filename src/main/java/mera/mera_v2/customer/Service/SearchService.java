package mera.mera_v2.customer.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.SearchConfig;
import mera.mera_v2.model.BitableRecord;
import mera.mera_v2.model.BitableTable;
import mera.mera_v2.model.UserConfigDto;
import mera.mera_v2.repository.SearchConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    @Value("${pos.api.base-url}")
    private String baseUrl;

    @Value("${pos.api.api-key}")
    private String apiKey;

    @Value("${pos.api.shop-id}")
    private String shopId;

    private final ObjectMapper objectMapper;
    private final BitableService bitableService;
    private final SearchConfigRepository searchConfigRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // Cache danh sách bảng KH / trao đổi của các base hệ thống (TỪ CHỐI CHĂM, Đơn hoàn, Hủy...)
    // — các base này có thể có nhiều bảng (Khách Hàng, Khách Hàng 2...), phải quét hết để không sót data.
    private static final long SYSTEM_BASE_TABLES_TTL_MS = 10 * 60 * 1000L;
    private final Map<String, SystemBaseTables> systemBaseTablesCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class SystemBaseTables {
        final List<String> khachHangTableIds = new ArrayList<>();
        final List<String> traoDoiTableIds = new ArrayList<>();
        final long fetchedAt = System.currentTimeMillis();
    }

    /**
     * Liệt kê tất cả bảng khách hàng + trao đổi của một base hệ thống (cache 10 phút).
     * Base hệ thống dùng tenant token (bot) — xem chú thích ở BitableService.
     */
    private SystemBaseTables resolveSystemBaseTables(String baseId) {
        SystemBaseTables cached = systemBaseTablesCache.get(baseId);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < SYSTEM_BASE_TABLES_TTL_MS) {
            return cached;
        }

        SystemBaseTables result = new SystemBaseTables();
        try {
            List<BitableTable> tables = bitableService.getTablesByBaseId(null, baseId, true);
            for (BitableTable t : tables) {
                String name = t.getName() != null ? t.getName().trim().toLowerCase() : "";
                if (name.contains("trao đổi") || name.contains("trao doi")
                        || name.contains("nhật ký") || name.contains("nhat ky")
                        || name.contains("interaction") || name.contains("log")) {
                    result.traoDoiTableIds.add(t.getTableId());
                } else if (name.contains("khách hàng") || name.contains("khach hang")
                        || name.contains("customer") || name.contains("kh")) {
                    result.khachHangTableIds.add(t.getTableId());
                }
            }
            log.info("BaseId={} - System base tables: KH={}, TD={}", baseId,
                    result.khachHangTableIds, result.traoDoiTableIds);
        } catch (Exception e) {
            log.warn("BaseId={} - Khong liet ke duoc tables cua base he thong: {}", baseId, e.getMessage());
        }

        if (!result.khachHangTableIds.isEmpty() || !result.traoDoiTableIds.isEmpty()) {
            systemBaseTablesCache.put(baseId, result);
        }
        return result;
    }

    @PostConstruct
    public void init() {
        log.info("SearchService initialized - using DB-backed search config");
    }

    public Map<String, Object> searchCustomer360(String phone) throws Exception {
        String sanitizedPhone = phone.replaceAll("\\D", "").trim();
        if (sanitizedPhone.isBlank()) {
            throw new IllegalArgumentException("So dien thoai khong hop le");
        }

        List<UserConfigDto> userConfigs = loadConfigsFromDB();
        if (userConfigs == null || userConfigs.isEmpty()) {
            log.warn("Khong co config nao trong DB (sync_status=COMPLETED)");
        }

        // --- GOI SONG SONG ---
        CompletableFuture<Map<String, Object>> posCustomerFuture = CompletableFuture.supplyAsync(() -> {
            try { return fetchCustomerInfo(sanitizedPhone); } catch (Exception e) { return null; }
        }, executorService);

        CompletableFuture<List<Map<String, Object>>> posOrdersFuture = CompletableFuture.supplyAsync(() -> {
            try { return fetchOrderInfos(sanitizedPhone); } catch (Exception e) { return Collections.emptyList(); }
        }, executorService);

        CompletableFuture<List<Map<String, Object>>> larkExchangesFuture = CompletableFuture.supplyAsync(() -> {
            try { return fetchLarkExchanges(sanitizedPhone, userConfigs); } catch (Exception e) { return Collections.emptyList(); }
        }, executorService);

        // Doi tat ca hoan thanh
        CompletableFuture.allOf(posCustomerFuture, posOrdersFuture, larkExchangesFuture).join();

        Map<String, Object> posCustomer = posCustomerFuture.get();
        List<Map<String, Object>> orders = posOrdersFuture.get();
        List<Map<String, Object>> exchanges = larkExchangesFuture.get();

        // Neu ca 3 deu rong thi moi coi la khong thay
        if (posCustomer == null && orders.isEmpty() && exchanges.isEmpty()) {
            return null;
        }

        Map<String, Object> customer = posCustomer;
        if (customer == null) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("name", "Khach hang (Lark)");
            fallback.put("phone", phone);
            fallback.put("succeedOrderCount", orders.size());
            fallback.put("posNotes", Collections.emptyList());
            customer = fallback;
        }

        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("customer", customer);
        finalResult.put("orders", orders);
        finalResult.put("exchanges", exchanges);

        // Them tom tat san pham
        finalResult.put("productSummary", summarizeProducts(orders));

        return finalResult;
    }

    private List<UserConfigDto> loadConfigsFromDB() {
        List<SearchConfig> dbConfigs = searchConfigRepository.findBySyncStatusOrderByUpdatedAtDesc(2);
        if (dbConfigs == null || dbConfigs.isEmpty()) {
            return Collections.emptyList();
        }
        return dbConfigs.stream()
                .map(this::toUserConfigDto)
                .collect(Collectors.toList());
    }

    private UserConfigDto toUserConfigDto(SearchConfig cfg) {
        UserConfigDto dto = new UserConfigDto(null, null);
        dto.setBaseId(cfg.getLarkBaseId());
        dto.setLarkName(cfg.getLarkBaseName());
        dto.setPosName(cfg.getPosName());
        dto.setKhachHangTableId(cfg.getKhachHangTableId());
        dto.setLichHenTableId(cfg.getLichHenTableId());
        dto.setTraoDoiTableId(cfg.getTraoDoiTableId());
        dto.setKhachHangViewId(cfg.getKhachHangViewId());
        dto.setLichHenViewId(cfg.getLichHenViewId());
        dto.setTraoDoiViewId(cfg.getTraoDoiViewId());
        log.debug("Config: baseId={}, khTable={}, lhTable={}, tdTable={}, posName={}",
                cfg.getLarkBaseId(), cfg.getKhachHangTableId(), cfg.getLichHenTableId(),
                cfg.getTraoDoiTableId(), cfg.getPosName());
        return dto;
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

        log.info("Step 1: Found ID {} for phone {}. Fetching full details...", actualId, phone);

        String detailUrl = UriComponentsBuilder.fromUriString(baseUrl + "/shops/" + shopId + "/customers/" + actualId)
                .queryParam("api_key", apiKey)
                .toUriString();

        ResponseEntity<String> detailRes = restTemplate.exchange(detailUrl, HttpMethod.GET, entity, String.class);
        JsonNode detailRoot = objectMapper.readTree(detailRes.getBody());

        JsonNode c = detailRoot.has("data") ? detailRoot.get("data") : detailRoot;

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

                JsonNode creator = n.path("created_by");
                note.put("userName", asText(creator, "fb_name"));

                posNotes.add(note);
            }
        }
        info.put("posNotes", posNotes);
        log.info("Found POS Customer: {} (ID: {}, Phone: {})", info.get("name"), info.get("customerId"), info.get("phone"));
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

    private List<Map<String, Object>> fetchLarkExchanges(String phone, List<UserConfigDto> userConfigs) {
        if (userConfigs == null || userConfigs.isEmpty()) {
            log.warn("Khong co user configs de search exchanges.");
            return Collections.emptyList();
        }

        List<Map<String, Object>> allExchanges = new ArrayList<>();
        log.info("Searching exchanges for phone {} across {} configs", phone, userConfigs.size());

        List<CompletableFuture<Void>> futures = userConfigs.stream()
            .filter(c -> c.getBaseId() != null && !c.getBaseId().isBlank())
            .map(config -> CompletableFuture.runAsync(() -> {
            try {
                String posName = config.getPosName() != null ? config.getPosName() : "";
                boolean isSpecialBase = posName.startsWith("__SPECIAL__:");
                String specialTableName = isSpecialBase ? posName.substring("__SPECIAL__:".length()) : null;

                // Base hệ thống (không gắn POS user: TỪ CHỐI CHĂM, ĐANG CHĂM, Đơn hoàn...) → bot có quyền,
                // user token có thể bị giới hạn role → ưu tiên tenant token cho các base này.
                boolean systemBase = isSpecialBase || posName.isBlank();

                // Base hệ thống có thể có NHIỀU bảng khách hàng / trao đổi (Khách Hàng, Khách Hàng 2...)
                // → liệt kê trực tiếp từ Lark (cache 10 phút) và quét tất cả để không sót data.
                List<String> khTableIds = new ArrayList<>();
                List<String> tdTableIds = new ArrayList<>();
                if (systemBase) {
                    SystemBaseTables sysTables = resolveSystemBaseTables(config.getBaseId());
                    khTableIds.addAll(sysTables.khachHangTableIds);
                    tdTableIds.addAll(sysTables.traoDoiTableIds);
                }
                if (khTableIds.isEmpty() && !config.getKhachHangTableId().isEmpty()) {
                    khTableIds.add(config.getKhachHangTableId());
                }
                if (tdTableIds.isEmpty() && !config.getTraoDoiTableId().isEmpty()) {
                    tdTableIds.add(config.getTraoDoiTableId());
                }

                if (khTableIds.isEmpty()) {
                    log.debug("Skipping config for base {} (No Khach Hang table ID)", config.getBaseId());
                    return;
                }

                List<BitableRecord> customers = null;
                for (String khTableId : khTableIds) {
                    List<BitableRecord> found = bitableService.searchCustomerByPhone(null, config.getBaseId(), khTableId, phone, null, systemBase);
                    if (found != null && !found.isEmpty()) {
                        customers = found;
                        log.info("BaseId={} - Customer matched in KH table {}", config.getBaseId(), khTableId);
                        break;
                    }
                }
                if (customers != null && !customers.isEmpty()) {
                    String recordId = customers.get(0).getRecordId();
                    log.info("BaseId={} - Found customer recordId='{}', fields={}", config.getBaseId(), recordId,
                            customers.get(0).getFields().keySet());

                    if (isSpecialBase) {
                        log.info("Customer found in SPECIAL base: {}", specialTableName);
                        Map<String, Object> warningEx = new HashMap<>();
                        warningEx.put("type", "special_warning");
                        warningEx.put("tableName", specialTableName);
                        warningEx.put("content", "Khach hang nam trong bang " + specialTableName);
                        warningEx.put("date", "-");
                        warningEx.put("person", "System");
                        warningEx.put("source", config.getLarkName());
                        synchronized (allExchanges) {
                            allExchanges.add(warningEx);
                        }
                        return;
                    }

                    if (tdTableIds.isEmpty()) {
                        log.info("Virtual exchange for Base {}", config.getLarkName());
                        Map<String, Object> virtualEx = new HashMap<>();
                        virtualEx.put("content", "Khách hàng nằm trong Base [" + config.getLarkName() + "]");
                        virtualEx.put("date", "-");
                        virtualEx.put("person", "System");
                        virtualEx.put("source", config.getLarkName());
                        synchronized (allExchanges) {
                            allExchanges.add(virtualEx);
                        }
                        return;
                    }

                    // Quét tất cả bảng trao đổi, gộp kết quả. ViewId chỉ áp cho bảng đã cấu hình sẵn.
                    List<BitableRecord> exchanges = new ArrayList<>();
                    for (String tdTableId : tdTableIds) {
                        String tdViewId = tdTableId.equals(config.getTraoDoiTableId()) ? config.getTraoDoiViewId() : null;
                        List<BitableRecord> exs = null;
                        try {
                            exs = bitableService.searchRecordsByCustomerId(null, config.getBaseId(), tdTableId, recordId, null, tdViewId, systemBase, phone);
                        } catch (Exception e) {
                            log.warn("BaseId={} - TD search (viewId={}) loi tren table {}: {}, thu lai khong view",
                                    config.getBaseId(), tdViewId, tdTableId, e.getMessage());
                        }
                        if ((exs == null || exs.isEmpty()) && tdViewId != null && !tdViewId.isBlank()) {
                            try {
                                exs = bitableService.searchRecordsByCustomerId(null, config.getBaseId(), tdTableId, recordId, null, null, systemBase, phone);
                            } catch (Exception e) {
                                // TD lỗi vẫn phải báo "khách nằm trong base" thay vì mất cả config
                                log.warn("BaseId={} - TD search failed hoàn toàn tren table {}: {}", config.getBaseId(), tdTableId, e.getMessage());
                            }
                        }
                        if (exs != null && !exs.isEmpty()) {
                            exchanges.addAll(exs);
                        }
                    }

                    if (exchanges != null && !exchanges.isEmpty()) {
                        log.info("Found {} exchanges in base {}", exchanges.size(), config.getBaseId());
                        synchronized (allExchanges) {
                            for (BitableRecord rec : exchanges) {
                                Map<String, Object> f = rec.getFields();
                                if (f == null) f = new HashMap<>();
                                Map<String, Object> ex = new HashMap<>();

                                // Log field names from first record to debug
                                if (allExchanges.size() == 0) {
                                    log.info("BaseId={} - TD record sample fields: {}", config.getBaseId(), f.keySet());
                                }

                                Object contentVal = findValue(f, List.of("Nội dung", "Noi dung", "Nội dung trao đổi", "Noi dung trao doi", "Content", "Ghi chú", "Ghi chu", "Lưu ý", "Luu y", "Note"));
                                Object dateVal = findValue(f, List.of("Ngày", "Ngay", "Ngày tạo", "Ngay tao", "Thời gian", "Thoi gian", "Date", "Created At", "Created time"));
                                Object personVal = findValue(f, List.of("Người thực hiện", "Nguoi thuc hien", "Nhân viên", "Nhan vien", "Người tạo", "Nguoi tao", "Person", "User", "Sale", "CSKH"));

                                ex.put("content", extractText(contentVal));
                                ex.put("date", extractText(dateVal));
                                ex.put("person", extractText(personVal));
                                ex.put("source", config.getLarkName());
                                log.info("BaseId={} - Parsed exchange {}: content='{}', date='{}', person='{}'",
                                        config.getBaseId(), allExchanges.size(),
                                        ex.get("content"), ex.get("date"), ex.get("person"));
                                allExchanges.add(ex);
                            }
                        }
                    } else {
                        log.info("Customer exists but no exchanges in Base {}. Adding placeholder.", config.getLarkName());
                        Map<String, Object> virtualEx = new HashMap<>();
                        virtualEx.put("content", "Khách hàng nằm trong Base [" + config.getLarkName() + "] (Chưa có trao đổi)");
                        virtualEx.put("date", "-");
                        virtualEx.put("person", "System");
                        virtualEx.put("source", config.getLarkName());
                        synchronized (allExchanges) {
                            allExchanges.add(virtualEx);
                        }
                    }
                } else {
                    log.debug("No customer found with phone {} in base {}", phone, config.getBaseId());
                }
            } catch (Exception e) {
                log.warn("Error fetching exchanges for base {}: {}", config.getBaseId(), e.getMessage());
            }
        }, executorService)).collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return allExchanges.stream()
                .sorted((a, b) -> parseAndCompareDates((String) b.getOrDefault("date", ""), (String) a.getOrDefault("date", "")))
                .collect(Collectors.toList());
    }

    private int parseAndCompareDates(String d1, String d2) {
        if (d1 == null || d1.isEmpty()) return 1;
        if (d2 == null || d2.isEmpty()) return -1;
        try {
            if (d1.contains("-") && d1.indexOf("-") < 5) {
                return d1.compareTo(d2);
            }
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
            return sdf.parse(d1).compareTo(sdf.parse(d2));
        } catch (Exception e) {
            return d1.compareTo(d2);
        }
    }

    private Object findValue(Map<String, Object> fields, List<String> hints) {
        if (fields == null) return null;
        for (String hint : hints) {
            if (fields.containsKey(hint)) return fields.get(hint);
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

        for (JsonNode p : phonesNode) {
            String pVal = p.asText();
            if (pVal.replaceAll("\\D", "").contains(searchedPhone)) {
                return pVal;
            }
        }

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
