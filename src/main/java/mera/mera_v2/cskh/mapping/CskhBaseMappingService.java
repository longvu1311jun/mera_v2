package mera.mera_v2.cskh.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.CskhBaseMapping;
import mera.mera_v2.model.BitableTable;
import mera.mera_v2.model.LarkNode;
import mera.mera_v2.model.PosUser;
import mera.mera_v2.repository.CskhBaseMappingRepository;
import mera.mera_v2.customer.Service.BitableService;
import mera.mera_v2.customer.Service.PosService;
import mera.mera_v2.lark.token.LarkTokenService;
import mera.mera_v2.lark.wiki.LarkWikiService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CskhBaseMappingService {

    private final CskhBaseMappingRepository repository;
    private final PosService posService;
    private final LarkWikiService larkWikiService;
    private final LarkTokenService larkTokenService;
    private final BitableService bitableService;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Extract phone number from text (same as /search-info logic)
     */
    private String extractPhoneNumber(String text) {
        if (text == null || text.isEmpty()) return null;
        Pattern pattern = Pattern.compile("(?:\\+84|0)[\\s\\.\\-]*[35789][0-9\\s\\.\\-]{7,10}");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String raw = matcher.group();
            String phone = raw.replaceAll("[^0-9]", "");
            if (phone.startsWith("84") && phone.length() > 9) phone = "0" + phone.substring(2);
            else if (!phone.startsWith("0") && phone.length() == 9) phone = "0" + phone;
            if (phone.length() == 10 && phone.matches("0[35789].*")) return phone;
        }
        Pattern simplePattern = Pattern.compile("[0-9]{10}");
        Matcher simpleMatcher = simplePattern.matcher(text.replaceAll("[^0-9]", ""));
        if (simpleMatcher.find()) return simpleMatcher.group();
        return null;
    }

    /**
     * Load all mappings: POS users → Lark Bases → Table IDs → persist to DB
     */
    @Transactional
    public int loadAndSaveMappings() {
        log.info("=== BẮT ĐẦU LOAD VÀ LƯU CSKH BASE MAPPING ===");
        int saved = 0;

        try {
            // 1. Get POS users
            List<PosUser> posUsers = posService.getUsers();
            log.info("Lấy được {} POS users", posUsers.size());

            // 2. Get Lark nodes (need token)
            List<LarkNode> larkNodes = getLarkNodesWithRetry();
            log.info("Lấy được {} Lark nodes", larkNodes.size());

            // 3. Build phone → PosUser map
            Map<String, PosUser> phoneToPosUser = buildPhoneToPosUserMap(posUsers);
            log.info("Built phone map với {} entries", phoneToPosUser.size());

            // 4. Build phone → LarkNode map
            Map<String, LarkNode> phoneToLarkNode = buildPhoneToLarkNodeMap(larkNodes);
            log.info("Built phone → LarkNode map với {} entries", phoneToLarkNode.size());

            // 5. Deactivate existing mappings
            repository.deactivateAll();

            // 6. Match and save - use upsert logic
            Set<String> processedPhones = new HashSet<>();
            List<CskhBaseMapping> newMappings = new ArrayList<>();

            for (PosUser posUser : posUsers) {
                String posPhone = extractPhoneNumber(posUser.getName());
                if (posPhone == null && posUser.getUser() != null) {
                    posPhone = extractPhoneNumber(posUser.getUser().getPhoneNumber());
                }

                if (posPhone != null && !processedPhones.contains(posPhone)) {
                    LarkNode matchedNode = phoneToLarkNode.get(posPhone);
                    if (matchedNode != null) {
                        processedPhones.add(posPhone);
                        CskhBaseMapping mapping = buildMapping(posUser, matchedNode);
                        if (mapping != null) {
                            newMappings.add(mapping);
                        }
                    }
                }
            }

            // Save all mappings (handle duplicates by using upsert)
            for (CskhBaseMapping mapping : newMappings) {
                try {
                    // Check if exists with same phone
                    List<CskhBaseMapping> existingList = repository.findByPosPhone(mapping.getPosPhone());
                    if (!existingList.isEmpty()) {
                        // Update first existing record
                        CskhBaseMapping existingMapping = existingList.get(0);
                        existingMapping.setPosName(mapping.getPosName());
                        existingMapping.setLarkBaseName(mapping.getLarkBaseName());
                        existingMapping.setLarkBaseId(mapping.getLarkBaseId());
                        existingMapping.setKhachHangTableId(mapping.getKhachHangTableId());
                        existingMapping.setTraoDoiTableId(mapping.getTraoDoiTableId());
                        existingMapping.setLichHenTableId(mapping.getLichHenTableId());
                        existingMapping.setViewId(mapping.getViewId());
                        existingMapping.setIsActive(true);
                        repository.save(existingMapping);
                        log.info("✅ Updated mapping: POS='{}' ({}) → Base='{}'", mapping.getPosName(), mapping.getPosPhone(), mapping.getLarkBaseName());
                    } else {
                        repository.save(mapping);
                        log.info("✅ Saved mapping: POS='{}' ({}) → Base='{}'", mapping.getPosName(), mapping.getPosPhone(), mapping.getLarkBaseName());
                    }
                    saved++;
                } catch (Exception e) {
                    log.warn("Lỗi khi lưu mapping cho '{}': {}", mapping.getPosPhone(), e.getMessage());
                }
            }

            log.info("=== HOÀN THÀNH: Đã lưu {} mappings ===", saved);
        } catch (Exception e) {
            log.error("Lỗi khi load mapping: {}", e.getMessage(), e);
        }

        return saved;
    }

    private List<LarkNode> getLarkNodesWithRetry() {
        int maxRetries = 2;
        for (int i = 0; i < maxRetries; i++) {
            try {
                // getAllNodesWithChildren lấy cả parent + child nodes
                List<LarkNode> nodes = larkWikiService.getAllNodesWithChildren(null);
                
                // Log tất cả nodes có phone
                int nodesWithPhone = 0;
                for (LarkNode node : nodes) {
                    String phone = extractPhoneNumber(node.getTitle());
                    if (phone != null) {
                        nodesWithPhone++;
                        log.debug("📱 Lark Node with phone: '{}' -> {}", node.getTitle(), phone);
                    }
                }
                log.info("Tổng cộng {} Lark nodes, {} có chứa phone", nodes.size(), nodesWithPhone);
                
                return nodes;
            } catch (Exception e) {
                log.warn("Lần {}: Lỗi khi lấy Lark nodes: {}", i + 1, e.getMessage());
                if (i == maxRetries - 1) {
                    log.error("Không thể lấy Lark nodes sau {} lần thử", maxRetries);
                    return Collections.emptyList();
                }
            }
        }
        return Collections.emptyList();
    }

    private Map<String, PosUser> buildPhoneToPosUserMap(List<PosUser> posUsers) {
        Map<String, PosUser> map = new HashMap<>();
        for (PosUser user : posUsers) {
            String phone = extractPhoneNumber(user.getName());
            if (phone == null && user.getUser() != null) {
                phone = extractPhoneNumber(user.getUser().getPhoneNumber());
            }
            if (phone != null) {
                map.put(phone, user);
                log.debug("📱 POS User with phone: '{}' -> {}", user.getName(), phone);
            }
        }
        return map;
    }

    private Map<String, LarkNode> buildPhoneToLarkNodeMap(List<LarkNode> nodes) {
        Map<String, LarkNode> map = new HashMap<>();
        for (LarkNode node : nodes) {
            String phone = extractPhoneNumber(node.getTitle());
            if (phone != null && !map.containsKey(phone)) {
                map.put(phone, node);
            }
            // Cũng check trong child nodes
            if (node.getChildNodes() != null) {
                for (LarkNode child : node.getChildNodes()) {
                    String childPhone = extractPhoneNumber(child.getTitle());
                    if (childPhone != null && !map.containsKey(childPhone)) {
                        map.put(childPhone, child);
                        log.debug("📱 Child node with phone: '{}' -> {} (parent: {})", child.getTitle(), childPhone, node.getTitle());
                    }
                }
            }
        }
        return map;
    }

    private CskhBaseMapping buildMapping(PosUser posUser, LarkNode node) {
        try {
            String posPhone = extractPhoneNumber(posUser.getName());
            if (posPhone == null && posUser.getUser() != null) {
                posPhone = extractPhoneNumber(posUser.getUser().getPhoneNumber());
            }

            CskhBaseMapping.CskhBaseMappingBuilder builder = CskhBaseMapping.builder()
                    .posUserId(posUser.getId() != null ? posUser.getId().toString() : null)
                    .posName(posUser.getName())
                    .posPhone(posPhone)
                    .larkBaseName(node.getTitle())
                    .larkBaseId(node.getObjToken())
                    .isActive(true)
                    .departmentName(posUser.getDepartment() != null ? posUser.getDepartment().getName() : null);

            // Get table IDs and View IDs
            if (node.getObjToken() != null) {
                try {
                    List<BitableTable> tables = bitableService.getTablesByBaseId(null, node.getObjToken());
                    for (BitableTable table : tables) {
                        String tableName = table.getName() != null ? table.getName().toLowerCase() : "";
                        String tableId = table.getTableId();
                        if (tableName.contains("khách hàng")) {
                            builder.khachHangTableId(tableId);
                            // Get view ID for Khách Hàng table
                            String viewId = getFirstViewId(node.getObjToken(), tableId);
                            if (viewId != null) builder.viewId(viewId);
                        }
                        else if (tableName.contains("trao đổi") || tableName.contains("nhật ký")) builder.traoDoiTableId(tableId);
                        else if (tableName.contains("lịch hẹn") || tableName.contains("lịch làm")) builder.lichHenTableId(tableId);
                    }
                } catch (Exception e) {
                    log.warn("Không lấy được tables cho base {}: {}", node.getObjToken(), e.getMessage());
                }
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("Lỗi khi build mapping cho user '{}': {}", posUser.getName(), e.getMessage());
            return null;
        }
    }

    // ==================== READ OPERATIONS ====================

    public List<CskhBaseMapping> getAllMappings() {
        return repository.findAllByOrderByIdAsc();
    }

    public List<CskhBaseMapping> getActiveMappings() {
        return repository.findByIsActiveTrueOrderByIdAsc();
    }

    /**
     * Tìm mapping theo số điện thoại (dùng cho webhook)
     */
    public Optional<CskhBaseMapping> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        String normalized = phone.replaceAll("[^0-9]", "");
        
        // Try active mappings first (to avoid duplicates)
        List<CskhBaseMapping> activeList = repository.findByPosPhoneAndIsActiveTrue(normalized);
        if (!activeList.isEmpty()) {
            log.debug("Found {} active mapping(s) for phone {}", activeList.size(), normalized);
            return Optional.of(activeList.get(0));
        }
        
        // Fallback: try exact match (may have inactive duplicates)
        try {
            List<CskhBaseMapping> all = repository.findByPosPhone(normalized);
            if (!all.isEmpty()) {
                // Return first active one, or first one if no active
                for (CskhBaseMapping m : all) {
                    if (Boolean.TRUE.equals(m.getIsActive())) {
                        return Optional.of(m);
                    }
                }
                return Optional.of(all.get(0));
            }
        } catch (Exception e) {
            log.warn("Multiple mappings found for phone {}, returning first active one", normalized);
            List<CskhBaseMapping> all = repository.findByPosPhone(normalized);
            if (!all.isEmpty()) {
                for (CskhBaseMapping m : all) {
                    if (Boolean.TRUE.equals(m.getIsActive())) {
                        return Optional.of(m);
                    }
                }
                return Optional.of(all.get(0));
            }
        }
        
        // Try 0-prefixed version
        if (normalized.startsWith("84") && normalized.length() > 9) {
            normalized = "0" + normalized.substring(2);
            try {
                List<CskhBaseMapping> activeList2 = repository.findByPosPhoneAndIsActiveTrue(normalized);
                if (!activeList2.isEmpty()) {
                    return Optional.of(activeList2.get(0));
                }
                List<CskhBaseMapping> all2 = repository.findByPosPhone(normalized);
                if (!all2.isEmpty()) {
                    for (CskhBaseMapping m : all2) {
                        if (Boolean.TRUE.equals(m.getIsActive())) {
                            return Optional.of(m);
                        }
                    }
                    return Optional.of(all2.get(0));
                }
            } catch (Exception e) {
                log.warn("Multiple mappings found for phone {}", normalized);
            }
        }
        return Optional.empty();
    }

    /**
     * Tìm mapping theo tên POS
     */
    public Optional<CskhBaseMapping> findByPosName(String posName) {
        if (posName == null || posName.isBlank()) return Optional.empty();
        return repository.findByPosNameIgnoreCase(posName.trim());
    }

    /**
     * Tìm mapping theo Base ID
     */
    public Optional<CskhBaseMapping> findByBaseId(String baseId) {
        if (baseId == null || baseId.isBlank()) return Optional.empty();
        return repository.findByLarkBaseId(baseId);
    }

    /**
     * Tìm Base ID và Table ID theo số điện thoại (dùng cho webhook)
     */
    public CskhMappingResult findMappingResultByPhone(String phone) {
        Optional<CskhBaseMapping> opt = findByPhone(phone);
        return opt.map(m -> new CskhMappingResult(m.getLarkBaseId(), m.getKhachHangTableId(), m.getLarkBaseName(), m.getViewId()))
                .orElse(null);
    }

    // ==================== MAPPING RESULT ====================

    public static class CskhMappingResult {
        private final String baseId;
        private final String khachHangTableId;
        private final String baseName;
        private final String viewId;

        public CskhMappingResult(String baseId, String khachHangTableId, String baseName, String viewId) {
            this.baseId = baseId;
            this.khachHangTableId = khachHangTableId;
            this.baseName = baseName;
            this.viewId = viewId;
        }

        public String getBaseId() { return baseId; }
        public String getKhachHangTableId() { return khachHangTableId; }
        public String getBaseName() { return baseName; }
        public String getViewId() { return viewId; }
    }

    /**
     * Get first view ID from a table
     * API: GET /open-apis/bitable/v1/apps/{baseId}/tables/{tableId}/views
     */
    private String getFirstViewId(String baseId, String tableId) {
        try {
            String token = getAccessToken();
            if (token == null || token.isBlank()) {
                log.warn("No access token available for getting view IDs");
                return null;
            }

            String url = String.format(
                    "https://open.larksuite.com/open-apis/bitable/v1/apps/%s/tables/%s/views?page_size=5",
                    baseId, tableId);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (resp.getBody() == null || resp.getBody().isEmpty()) {
                return null;
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(resp.getBody());

            int code = json.path("code").asInt(-1);
            if (code != 0) {
                log.warn("Lark API error getting views: code={}", code);
                return null;
            }

            com.fasterxml.jackson.databind.JsonNode items = json.path("data").path("items");
            if (items.isArray() && !items.isEmpty()) {
                String viewId = items.get(0).path("view_id").asText(null);
                log.debug("Found view ID {} for table {} in base {}", viewId, tableId, baseId);
                return viewId;
            }
        } catch (Exception e) {
            log.warn("Error getting view ID for base {} table {}: {}", baseId, tableId, e.getMessage());
        }
        return null;
    }

    /**
     * Get access token for API calls
     */
    private String getAccessToken() {
        try {
            return larkTokenService.getAccessToken(false);
        } catch (Exception e) {
            log.warn("Cannot get access token: {}", e.getMessage());
            return null;
        }
    }
}
