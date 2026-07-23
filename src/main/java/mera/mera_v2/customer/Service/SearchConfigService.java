package mera.mera_v2.customer.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.SearchConfig;
import mera.mera_v2.lark.wiki.LarkWikiService;
import mera.mera_v2.model.BitableTable;
import mera.mera_v2.model.LarkNode;
import mera.mera_v2.model.PosUser;
import mera.mera_v2.repository.SearchConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchConfigService {
    private static final Logger log = LoggerFactory.getLogger(SearchConfigService.class);

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_LOADING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = 3;

    private final SearchConfigRepository searchConfigRepository;
    private final PosService posService;
    private final LarkWikiService larkWikiService;
    private final BitableService bitableService;

    public List<SearchConfig> getActiveConfigs() {
        return searchConfigRepository.findBySyncStatusOrderByUpdatedAtDesc(STATUS_COMPLETED);
    }

    public List<SearchConfig> getConfigsByStatus(int status) {
        return searchConfigRepository.findBySyncStatusOrderByUpdatedAtDesc(status);
    }

    public List<SearchConfig> getAllConfigs() {
        return searchConfigRepository.findBySyncStatusAndLarkBaseNameIsNotNull(STATUS_COMPLETED);
    }

    /**
     * Tim config theo so dien thoai CSKH (dung cho webhook).
     * Uu tien config da sync xong (COMPLETED), fallback sang moi status.
     */
    public Optional<SearchConfig> findByPosPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        String normalized = phone.replaceAll("[^0-9]", "");
        if (normalized.startsWith("84") && normalized.length() > 10) {
            normalized = "0" + normalized.substring(2);
        }

        List<SearchConfig> completed = searchConfigRepository
                .findByPosPhoneAndSyncStatusOrderByUpdatedAtDesc(normalized, STATUS_COMPLETED);
        if (!completed.isEmpty()) {
            return Optional.of(completed.get(0));
        }

        List<SearchConfig> any = searchConfigRepository.findByPosPhoneOrderByUpdatedAtDesc(normalized);
        return any.isEmpty() ? Optional.empty() : Optional.of(any.get(0));
    }

    /**
     * Tim Base ID va Table ID theo so dien thoai (dung cho webhook)
     */
    public CskhMappingResult findMappingResultByPhone(String phone) {
        return findByPosPhone(phone)
                .map(c -> new CskhMappingResult(c.getLarkBaseId(), c.getKhachHangTableId(),
                        c.getLarkBaseName(), c.getKhachHangViewId()))
                .orElse(null);
    }

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

    @Transactional
    public int reloadAll() {
        log.info("=== BAT DAU RELOAD SEARCH CONFIG ===");

        int resetCount = searchConfigRepository.resetAllToPending();
        log.info("Reset {} rows ve PENDING", resetCount);

        List<SearchConfig> existing = searchConfigRepository.findBySyncStatusOrderByUpdatedAtDesc(STATUS_PENDING);
        Set<String> existingBaseIds = existing.stream()
                .map(SearchConfig::getLarkBaseId)
                .collect(Collectors.toSet());

        int completed = 0;
        int failed = 0;

        try {
            List<PosUser> posUsers = posService.getUsers();
            log.info("Lay duoc {} POS users", posUsers.size());

            List<LarkNode> larkNodes = getLarkNodesWithRetry();
            log.info("Lay duoc {} Lark nodes", larkNodes.size());

            Map<String, PosUser> phoneToPosUser = buildPhoneToPosUserMap(posUsers);
            Map<String, PosUser> nameToPosUser = buildNameToPosUserMap(posUsers);
            Map<String, LarkNode> nameToLarkNode = buildNameToLarkNodeMap(larkNodes);

            // Group configs by baseId, merge all viewIds into one config per baseId
            Map<String, List<SearchConfig>> grouped = existing.stream()
                    .collect(Collectors.groupingBy(SearchConfig::getLarkBaseId));

            for (Map.Entry<String, List<SearchConfig>> entry : grouped.entrySet()) {
                String baseId = entry.getKey();
                List<SearchConfig> group = entry.getValue();

                LarkNode node = larkNodes.stream()
                        .filter(n -> baseId.equals(n.getObjToken()))
                        .findFirst()
                        .orElse(null);

                if (node == null) {
                    for (SearchConfig c : group) {
                        updateStatus(baseId, STATUS_FAILED, "Khong tim thay Lark node trong danh sach");
                        failed++;
                    }
                    continue;
                }

                try {
                    // Pick primary config (first in group) as the one to save
                    SearchConfig primary = group.get(0);

                    // Merge pos info: use first non-null posName/posPhone from group
                    for (SearchConfig c : group) {
                        if (primary.getPosName() == null && c.getPosName() != null) {
                            primary.setPosName(c.getPosName());
                        }
                        if (primary.getPosPhone() == null && c.getPosPhone() != null) {
                            primary.setPosPhone(c.getPosPhone());
                        }
                        if (primary.getPosUserId() == null && c.getPosUserId() != null) {
                            primary.setPosUserId(c.getPosUserId());
                        }
                        if (primary.getDepartmentName() == null && c.getDepartmentName() != null) {
                            primary.setDepartmentName(c.getDepartmentName());
                        }
                    }

                    // Re-match POS user from merged pos info
                    String posName = primary.getPosName();
                    String posPhone = primary.getPosPhone();

                    PosUser matchedUser = null;
                    if (posPhone != null && phoneToPosUser.containsKey(posPhone)) {
                        matchedUser = phoneToPosUser.get(posPhone);
                    } else if (posName != null) {
                        String normalized = normalizeName(posName.toLowerCase().trim());
                        matchedUser = nameToPosUser.get(normalized);
                        if (matchedUser == null) {
                            matchedUser = nameToPosUser.get(posName.toLowerCase().trim());
                        }
                    }

                    // Fallback: match theo tiêu đề base Lark — cần cho trường hợp config đã tồn tại
                    // từ trước (posName/posPhone NULL) nhưng nhân viên mới được thêm vào POS sau này.
                    if (matchedUser == null && node.getTitle() != null && !node.getTitle().isBlank()) {
                        String nodePhone = extractPhoneNumber(node.getTitle());
                        if (nodePhone != null) {
                            matchedUser = phoneToPosUser.get(nodePhone);
                        }
                        if (matchedUser == null) {
                            String title = node.getTitle().toLowerCase().trim();
                            matchedUser = nameToPosUser.get(title);
                            if (matchedUser == null) {
                                matchedUser = nameToPosUser.get(normalizeName(title));
                            }
                        }
                        if (matchedUser != null) {
                            log.info("BaseId={} - Match POS user '{}' tu tieu de base '{}'",
                                    baseId, matchedUser.getName(), node.getTitle());
                        }
                    }

                    if (matchedUser != null) {
                        primary.setPosUserId(matchedUser.getId() != null ? matchedUser.getId().toString() : null);
                        primary.setPosName(matchedUser.getName());
                        String phone = extractPhoneNumber(matchedUser.getName());
                        if (phone == null && matchedUser.getUser() != null) {
                            phone = extractPhoneNumber(matchedUser.getUser().getPhoneNumber());
                        }
                        primary.setPosPhone(phone);
                        if (matchedUser.getDepartment() != null) {
                            primary.setDepartmentName(matchedUser.getDepartment().getName());
                        }
                    }

                    primary.setLarkBaseName(node.getTitle());
                    primary.setLarkObjType(node.getObjType());

                    loadTableConfigs(primary, node.getObjToken());
                    searchConfigRepository.save(primary);
                    searchConfigRepository.flush();
                    // Force persist view_id columns via native SQL (Hibernate co the bo qua NULL cols trong UPDATE)
                    searchConfigRepository.updateViewIds(
                            primary.getLarkBaseId(),
                            primary.getKhachHangViewId(),
                            primary.getLichHenViewId(),
                            primary.getTraoDoiViewId()
                    );
                    log.info("BaseId={} - DA SAVE: KH_viewId={}, LH_viewId={}, TD_viewId={}",
                            primary.getLarkBaseId(),
                            primary.getKhachHangViewId(),
                            primary.getLichHenViewId(),
                            primary.getTraoDoiViewId());

                    updateStatus(baseId, STATUS_COMPLETED, null);
                    completed++;
                } catch (Exception e) {
                    log.warn("Loi khi reload config cho base {}: {}", baseId, e.getMessage());
                    updateStatus(baseId, STATUS_FAILED, e.getMessage());
                    failed++;
                }
            }

            Set<String> processedBaseIds = new HashSet<>(existingBaseIds);
            for (LarkNode node : larkNodes) {
                if (processedBaseIds.contains(node.getObjToken())) continue;
                if (node.getObjToken() == null || node.getObjToken().isBlank()) continue;

                try {
                    SearchConfig config = new SearchConfig();
                    config.setLarkBaseId(node.getObjToken());
                    config.setLarkBaseName(node.getTitle());
                    config.setLarkObjType(node.getObjType());
                    config.setSyncStatus(STATUS_COMPLETED);

                    String nodePhone = extractPhoneNumber(node.getTitle());
                    PosUser matchedUser = null;
                    if (nodePhone != null) {
                        matchedUser = phoneToPosUser.get(nodePhone);
                    }
                    if (matchedUser == null) {
                        String title = node.getTitle().toLowerCase().trim();
                        matchedUser = nameToPosUser.get(title);
                        if (matchedUser == null) {
                            matchedUser = nameToPosUser.get(normalizeName(title));
                        }
                    }

                    if (matchedUser != null) {
                        config.setPosUserId(matchedUser.getId() != null ? matchedUser.getId().toString() : null);
                        config.setPosName(matchedUser.getName());
                        String phone = extractPhoneNumber(matchedUser.getName());
                        if (phone == null && matchedUser.getUser() != null) {
                            phone = extractPhoneNumber(matchedUser.getUser().getPhoneNumber());
                        }
                        config.setPosPhone(phone);
                        if (matchedUser.getDepartment() != null) {
                            config.setDepartmentName(matchedUser.getDepartment().getName());
                        }
                    }

                    loadTableConfigs(config, node.getObjToken());
                    searchConfigRepository.save(config);
                    searchConfigRepository.flush();
                    completed++;
                } catch (Exception e) {
                    log.warn("Loi khi them config moi cho base {}: {}", node.getObjToken(), e.getMessage());
                    failed++;
                }
            }

            log.info("=== HOAN THANH RELOAD: completed={}, failed={} ===", completed, failed);
            return completed;
        } catch (Exception e) {
            log.error("Loi khi reload search config: {}", e.getMessage(), e);
            return completed;
        }
    }

    private List<LarkNode> getLarkNodesWithRetry() {
        int maxRetries = 2;
        Exception lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                List<LarkNode> topNodes = larkWikiService.getAllNodes();
                List<LarkNode> allNodes = new ArrayList<>(topNodes);

                // Flatten child nodes into the flat list (child nodes = base con)
                for (LarkNode node : topNodes) {
                    try {
                        List<LarkNode> childNodes = larkWikiService.getChildNodesByNodeToken(node.getNodeToken());
                        if (childNodes != null) {
                            allNodes.addAll(childNodes);
                        }
                    } catch (Exception e) {
                        log.debug("Khong lay child nodes cho {}: {}", node.getNodeToken(), e.getMessage());
                    }
                }
                log.info("Total Lark nodes (with children): topNodes={}, childNodes={}, total={}",
                        topNodes.size(), allNodes.size() - topNodes.size(), allNodes.size());
                return allNodes;
            } catch (Exception e) {
                lastException = e;
                log.warn("Lan {}: Loi khi lay Lark nodes: {}", i + 1, e.getMessage());
            }
        }
        log.error("Khong the lay Lark nodes sau {} lan thu: {}", maxRetries, lastException != null ? lastException.getMessage() : "");
        return Collections.emptyList();
    }

    private void loadTableConfigs(SearchConfig config, String baseId) {
        try {
            List<BitableTable> tables = bitableService.getTablesByBaseId(null, baseId);
            if (tables.isEmpty()) {
                log.warn("BaseId={} - Khong lay duoc table nao (base chua co bitable hoac loi token)", baseId);
                return;
            }
            log.info("BaseId={} - Tim thay {} tables", baseId, tables.size());
            for (BitableTable table : tables) {
                String tableName = table.getName() != null ? table.getName().trim().toLowerCase() : "";
                String tableId = table.getTableId();
                log.info("BaseId={} - Xet table: '{}' (id={})", baseId, tableName, tableId);

                // Lấy view_id cho table này
                String viewId = null;
                try {
                    viewId = bitableService.getDefaultViewId(null, baseId, tableId);
                } catch (Exception e) {
                    log.warn("BaseId={} - LOI khi lay view_id cho table '{}': {}", baseId, tableName, e.getMessage());
                }
                log.info("BaseId={} - Table '{}' -> viewId={}", baseId, tableName, viewId);

                // Khách hàng
                if ((tableName.contains("khách hàng") || tableName.contains("khach hang") ||
                     tableName.contains("customer") || tableName.contains("kh") || tableName.equals("kh"))) {
                    config.setKhachHangTableId(tableId);
                    if (viewId != null) {
                        config.setKhachHangViewId(viewId);
                        log.info("BaseId={} - ✓ SET khachHangViewId={}", baseId, viewId);
                    } else {
                        log.warn("BaseId={} - ⚠ Table '{}' matched KH nhưng viewId=null!", baseId, tableName);
                    }
                }
                // Lịch hẹn
                if ((tableName.contains("lịch hẹn") || tableName.contains("lich hen") ||
                     tableName.contains("lịch làm") || tableName.contains("lich lam") ||
                     tableName.contains("calendar") || tableName.contains("schedule"))) {
                    config.setLichHenTableId(tableId);
                    if (viewId != null) {
                        config.setLichHenViewId(viewId);
                        log.info("BaseId={} - ✓ SET lichHenViewId={}", baseId, viewId);
                    } else {
                        log.warn("BaseId={} - ⚠ Table '{}' matched LH nhưng viewId=null!", baseId, tableName);
                    }
                }
                // Trao đổi
                if ((tableName.contains("trao đổi") || tableName.contains("trao doi") ||
                     tableName.contains("nhật ký") || tableName.contains("nhat ky") ||
                     tableName.contains("interaction") || tableName.contains("log"))) {
                    config.setTraoDoiTableId(tableId);
                    if (viewId != null) {
                        config.setTraoDoiViewId(viewId);
                        log.info("BaseId={} - ✓ SET traoDoiViewId={}", baseId, viewId);
                    } else {
                        log.warn("BaseId={} - ⚠ Table '{}' matched TD nhưng viewId=null!", baseId, tableName);
                    }
                }
            }
            log.info("BaseId={} - KET QUA: KH_table={}, KH_view={}, LH_table={}, LH_view={}, TD_table={}, TD_view={}",
                    baseId,
                    config.getKhachHangTableId(), config.getKhachHangViewId(),
                    config.getLichHenTableId(), config.getLichHenViewId(),
                    config.getTraoDoiTableId(), config.getTraoDoiViewId());
        } catch (IllegalStateException e) {
            log.warn("BaseId={} - Token chua duoc luu, can login truoc: {}", baseId, e.getMessage());
        } catch (Exception e) {
            log.warn("Khong lay duoc tables cho baseId {}: {}", baseId, e.getMessage());
        }
    }

    @Transactional
    protected void updateStatus(String baseId, int status, String error) {
        searchConfigRepository.updateStatus(baseId, status, error);
    }

    private Map<String, PosUser> buildPhoneToPosUserMap(List<PosUser> posUsers) {
        Map<String, PosUser> map = new HashMap<>();
        for (PosUser user : posUsers) {
            String phone = extractPhoneNumber(user.getName());
            if (phone == null && user.getUser() != null) {
                phone = extractPhoneNumber(user.getUser().getPhoneNumber());
            }
            if (phone != null) map.put(phone, user);
        }
        return map;
    }

    private Map<String, PosUser> buildNameToPosUserMap(List<PosUser> posUsers) {
        Map<String, PosUser> map = new HashMap<>();
        for (PosUser user : posUsers) {
            String name = user.getName() != null ? user.getName().trim().toLowerCase() : null;
            if (name != null && !map.containsKey(name)) {
                map.put(name, user);
                String normalized = normalizeName(name);
                if (!normalized.equals(name)) map.put(normalized, user);
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
            if (node.getChildNodes() != null) {
                for (LarkNode child : node.getChildNodes()) {
                    String childPhone = extractPhoneNumber(child.getTitle());
                    if (childPhone != null && !map.containsKey(childPhone)) {
                        map.put(childPhone, child);
                    }
                }
            }
        }
        return map;
    }

    private Map<String, LarkNode> buildNameToLarkNodeMap(List<LarkNode> nodes) {
        Map<String, LarkNode> map = new HashMap<>();
        for (LarkNode node : nodes) {
            String name = node.getTitle() != null ? node.getTitle().trim().toLowerCase() : null;
            if (name != null && !map.containsKey(name)) {
                map.put(name, node);
                String normalized = normalizeName(name);
                if (!normalized.equals(name)) map.put(normalized, node);
            }
            if (node.getChildNodes() != null) {
                for (LarkNode child : node.getChildNodes()) {
                    String childName = child.getTitle() != null ? child.getTitle().trim().toLowerCase() : null;
                    if (childName != null && !map.containsKey(childName)) {
                        map.put(childName, child);
                        String normalized = normalizeName(childName);
                        if (!normalized.equals(childName)) map.put(normalized, child);
                    }
                }
            }
        }
        return map;
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        return name
            .replaceAll("[àáạảã]", "a")
            .replaceAll("[ăằắặẳẵ]", "a")
            .replaceAll("[âầấậẩẫ]", "a")
            .replaceAll("[èéẹẻẽ]", "e")
            .replaceAll("[êềếệểễ]", "e")
            .replaceAll("[ìíịỉĩ]", "i")
            .replaceAll("[òóọỏõ]", "o")
            .replaceAll("[ôồốộổỗ]", "o")
            .replaceAll("[ơờớợởỡ]", "o")
            .replaceAll("[ùúụủũ]", "u")
            .replaceAll("[ưừứựửữ]", "u")
            .replaceAll("[ỳýỵỷỹ]", "y")
            .replaceAll("đ", "d");
    }

    private String extractPhoneNumber(String text) {
        if (text == null || text.isBlank()) return null;
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
}
