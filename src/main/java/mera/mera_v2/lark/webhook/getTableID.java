package mera.mera_v2.lark.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.config.LarkBaseProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class getTableID {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LarkBaseProperties larkBaseProperties;
    
    @Value("${lark.space-id:7553087350184673311}")
    private String defaultSpaceId;
    
    // DTO để lưu thông tin Base ID
    public static class BaseIdInfo {
        private String baseId;        // obj_token (Base ID)
        private String baseName;      // title của base
        private String nodeToken;     // node_token của base (để dùng làm parent_node_token)
        private String path;          // đường dẫn trong Wiki
        
        public BaseIdInfo(String baseId, String baseName, String nodeToken, String path) {
            this.baseId = baseId;
            this.baseName = baseName;
            this.nodeToken = nodeToken;
            this.path = path;
        }
        
        public String getBaseId() { return baseId; }
        public String getBaseName() { return baseName; }
        public String getNodeToken() { return nodeToken; }
        public String getPath() { return path; }
    }
    
    // DTO để lưu thông tin Table ID
    public static class TableIdInfo {
        private String tableId;
        private String tableName;
        private String baseId;        // Base ID cha
        
        public TableIdInfo(String tableId, String tableName, String baseId) {
            this.tableId = tableId;
            this.tableName = tableName;
            this.baseId = baseId;
        }
        
        public String getTableId() { return tableId; }
        public String getTableName() { return tableName; }
        public String getBaseId() { return baseId; }
    }
    
    /**
     * Lấy user access token từ LarkBaseProperties
     */
    private String getUserAccessToken() {
        String token = larkBaseProperties.getUserAccessToken();
        if (token == null || token.isBlank()) {
            log.error("[LARK AUTH] User access token is not available. Please login at /token first.");
            return null;
        }
        return token.trim();
    }
    
    /**
     * Lấy danh sách Base IDs từ space_id (từ config hoặc truyền vào)
     */
    public List<BaseIdInfo> getBaseIdsFromSpace(String spaceId) throws Exception {
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalStateException("User access token is not available. Please login at /token first.");
        }
        
        if (spaceId == null || spaceId.isBlank()) {
            spaceId = defaultSpaceId;
        }
        
        if (spaceId == null || spaceId.isBlank()) {
            log.warn("⚠️ Space ID is not configured, returning empty list");
            return new ArrayList<>();
        }
        
        // Crawl các bitable nodes trong space
        List<BaseIdInfo> baseIds = new ArrayList<>();
        crawlBitableNodesRecursive(userAccessToken, spaceId, null, "", baseIds);
        
        log.info("✅ Found {} Base IDs from space {}", baseIds.size(), spaceId);
        return baseIds;
    }
    
    /**
     * Lấy danh sách Base IDs từ space_id mặc định (từ config)
     */
    public List<BaseIdInfo> getAllBaseIds() throws Exception {
        return getBaseIdsFromSpace(defaultSpaceId);
    }
    
    /**
     * Đệ quy crawl các bitable nodes trong space
     */
    private void crawlBitableNodesRecursive(String userAccessToken, String spaceId,
            String parentNodeToken, String parentPath, List<BaseIdInfo> baseIds) throws Exception {
        String url = String.format(
                "https://open.larksuite.com/open-apis/wiki/v2/spaces/%s/nodes?page_size=50",
                spaceId);
        
        if (parentNodeToken != null && !parentNodeToken.isEmpty()) {
            url += "&parent_node_token=" + URLEncoder.encode(parentNodeToken, StandardCharsets.UTF_8);
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userAccessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        String pageToken = null;
        do {
            String currentUrl = url;
            if (pageToken != null && !pageToken.isEmpty()) {
                currentUrl += "&page_token=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8);
            }
            
            ResponseEntity<String> resp;
            try {
                resp = restTemplate.exchange(currentUrl, HttpMethod.GET, entity, String.class);
            } catch (RestClientException e) {
                log.error("❌ Error calling Wiki API: {}", e.getMessage());
                break;
            }
            
            if (resp.getBody() == null || resp.getBody().isEmpty()) {
                break;
            }
            
            JsonNode json = objectMapper.readTree(resp.getBody());
            JsonNode codeNode = json.path("code");
            int code = codeNode != null && !codeNode.isNull() ? codeNode.asInt() : -1;
            if (code != 0) {
                JsonNode msgNode = json.path("msg");
                String msg = msgNode != null && !msgNode.isNull()
                        ? objectMapper.convertValue(msgNode, String.class) : "";
                log.error("❌ Lark API error: code={}, msg={}", code, msg);
                break;
            }
            
            JsonNode dataNode = json.get("data");
            if (dataNode == null || dataNode.isNull()) {
                break;
            }
            
            JsonNode itemsNode = dataNode.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    JsonNode objTypeNode = item.path("obj_type");
                    String objType = objTypeNode != null && !objTypeNode.isNull()
                            ? objectMapper.convertValue(objTypeNode, String.class) : "";
                    JsonNode objTokenNode = item.path("obj_token");
                    String objToken = objTokenNode != null && !objTokenNode.isNull()
                            ? objectMapper.convertValue(objTokenNode, String.class) : "";
                    JsonNode titleNode = item.path("title");
                    String title = titleNode != null && !titleNode.isNull()
                            ? objectMapper.convertValue(titleNode, String.class) : "";
                    JsonNode nodeTokenNode = item.path("node_token");
                    String nodeToken = nodeTokenNode != null && !nodeTokenNode.isNull()
                            ? objectMapper.convertValue(nodeTokenNode, String.class) : "";
                    boolean hasChild = item.path("has_child").asBoolean(false);
                    
                    String currentPath = (parentPath == null || parentPath.isEmpty())
                            ? title
                            : parentPath + "/" + title;
                    
                    // Nếu là bitable, thêm vào danh sách Base IDs
                    if ("bitable".equalsIgnoreCase(objType) && objToken != null && !objToken.isEmpty()) {
                        baseIds.add(new BaseIdInfo(objToken, title, nodeToken, currentPath));
                        log.info("✅ Found Base ID: {} - {} (node_token: {})", objToken, title, nodeToken);
                    }
                    
                    // Đệ quy nếu có con
                    if (hasChild && nodeToken != null && !nodeToken.isEmpty()) {
                        crawlBitableNodesRecursive(userAccessToken, spaceId, nodeToken, currentPath, baseIds);
                    }
                }
            }
            
            JsonNode hasMoreNode = dataNode.path("has_more");
            boolean hasMore = hasMoreNode != null && !hasMoreNode.isNull() && hasMoreNode.asBoolean(false);
            if (!hasMore) {
                break;
            }
            
            JsonNode pageTokenNode = dataNode.path("page_token");
            pageToken = pageTokenNode != null && !pageTokenNode.isNull()
                    ? objectMapper.convertValue(pageTokenNode, String.class) : "";
            if (pageToken == null || pageToken.isEmpty()) {
                break;
            }
        } while (true);
    }
    
    /**
     * Lấy danh sách Table IDs từ Base ID
     * Sử dụng Bitable API trực tiếp (đơn giản và nhanh nhất)
     */
    public List<TableIdInfo> getTableIdsFromBaseId(String baseId) throws Exception {
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalStateException("User access token is not available. Please login at /token first.");
        }
        
        if (baseId == null || baseId.isBlank()) {
            log.warn("⚠️ Base ID is null, cannot get Table IDs");
            return new ArrayList<>();
        }
        
        return getTableIdsFromBitableApi(userAccessToken, baseId);
    }
    
    /**
     * Lấy tất cả Base IDs và Table IDs từ space_id mặc định
     * Trả về Map: BaseId -> List<TableIdInfo>
     */
    public Map<String, List<TableIdInfo>> getAllBaseIdsAndTableIds() throws Exception {
        List<BaseIdInfo> baseIds = getAllBaseIds();
        Map<String, List<TableIdInfo>> result = new java.util.HashMap<>();
        
        for (BaseIdInfo baseInfo : baseIds) {
            try {
                List<TableIdInfo> tableIds = getTableIdsFromBaseId(baseInfo.getBaseId());
                result.put(baseInfo.getBaseId(), tableIds);
                log.info("✅ Base '{}' ({}): {} tables", baseInfo.getBaseName(), baseInfo.getBaseId(), tableIds.size());
            } catch (Exception e) {
                log.error("❌ Failed to get Table IDs for Base '{}' ({}): {}", 
                        baseInfo.getBaseName(), baseInfo.getBaseId(), e.getMessage());
                result.put(baseInfo.getBaseId(), new ArrayList<>());
            }
        }
        
        return result;
    }
    
    /**
     * Lấy Table IDs từ Bitable API trực tiếp (không cần Wiki)
     */
    private List<TableIdInfo> getTableIdsFromBitableApi(String userAccessToken, String baseId) throws Exception {
        String url = String.format(
                "https://open.larksuite.com/open-apis/bitable/v1/apps/%s/tables?page_size=50",
                baseId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userAccessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        List<TableIdInfo> tableIds = new ArrayList<>();
        String pageToken = null;
        
        do {
            String currentUrl = url;
            if (pageToken != null && !pageToken.isEmpty()) {
                currentUrl += "&page_token=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8);
            }
            
            ResponseEntity<String> resp;
            try {
                resp = restTemplate.exchange(currentUrl, HttpMethod.GET, entity, String.class);
            } catch (RestClientException e) {
                log.error("❌ Error calling Bitable API: {}", e.getMessage());
                break;
            }
            
            if (resp.getBody() == null || resp.getBody().isEmpty()) {
                break;
            }
            
            JsonNode json = objectMapper.readTree(resp.getBody());
            JsonNode codeNode = json.path("code");
            int code = codeNode != null && !codeNode.isNull() ? codeNode.asInt() : -1;
            if (code != 0) {
                JsonNode msgNode = json.path("msg");
                String msg = msgNode != null && !msgNode.isNull()
                        ? objectMapper.convertValue(msgNode, String.class) : "";
                log.error("❌ Bitable API error: code={}, msg={}", code, msg);
                break;
            }
            
            JsonNode dataNode = json.get("data");
            if (dataNode == null || dataNode.isNull()) {
                break;
            }
            
            JsonNode itemsNode = dataNode.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    JsonNode tableIdNode = item.path("table_id");
                    String tableId = tableIdNode != null && !tableIdNode.isNull()
                            ? objectMapper.convertValue(tableIdNode, String.class) : "";
                    JsonNode nameNode = item.path("name");
                    String tableName = nameNode != null && !nameNode.isNull()
                            ? objectMapper.convertValue(nameNode, String.class) : "";
                    
                    if (tableId != null && !tableId.isEmpty()) {
                        tableIds.add(new TableIdInfo(tableId, tableName, baseId));
                        log.info("✅ Found Table ID: {} - {} (Base: {})", tableId, tableName, baseId);
                    }
                }
            }
            
            JsonNode hasMoreNode = dataNode.path("has_more");
            boolean hasMore = hasMoreNode != null && !hasMoreNode.isNull() && hasMoreNode.asBoolean(false);
            if (!hasMore) {
                break;
            }
            
            JsonNode pageTokenNode = dataNode.path("page_token");
            pageToken = pageTokenNode != null && !pageTokenNode.isNull()
                    ? objectMapper.convertValue(pageTokenNode, String.class) : "";
            if (pageToken == null || pageToken.isEmpty()) {
                break;
            }
        } while (true);
        
        return tableIds;
    }
    
    /**
     * Tìm open_id theo tên CSKH trong department (giữ nguyên logic cũ)
     */
    public String resolveCskhOpenIdByName(String departmentId, String cskhName, String openIdRaw) {
        // Nếu đã có sẵn openId thì dùng luôn
        if (openIdRaw != null && !openIdRaw.isBlank()) {
            return openIdRaw;
        }
        if (cskhName == null || cskhName.isBlank()) {
            return null;
        }
        
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            log.error("[LARK CONTACT] Không lấy được user access token");
            return null;
        }
        
        try {
            String pageToken = null;
            String url = "https://open.larksuite.com/open-apis/contact/v3/users/find_by_department";
            
            while (true) {
                StringBuilder urlBuilder = new StringBuilder(url);
                urlBuilder.append("?user_id_type=open_id");
                urlBuilder.append("&department_id_type=department_id");
                urlBuilder.append("&department_id=").append(URLEncoder.encode(departmentId, StandardCharsets.UTF_8));
                urlBuilder.append("&page_size=50");
                if (pageToken != null && !pageToken.isBlank()) {
                    urlBuilder.append("&page_token=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
                }
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(userAccessToken);
                
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                
                ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                        urlBuilder.toString(),
                        HttpMethod.GET,
                        entity,
                        new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
                );
                
                Map<String, Object> body = resp.getBody();
                log.info("[LARK CONTACT] Response: {}", body);
                
                if (body == null || body.get("data") == null) {
                    break;
                }
                
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
                
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        String name = (String) item.get("name");
                        if (name != null && name.equals(cskhName)) {
                            String openId = (String) item.get("open_id");
                            log.info("[LARK CONTACT] Match CSKH '{}' -> open_id={}", cskhName, openId);
                            return openId;
                        }
                    }
                }
                
                Boolean hasMore = data.get("has_more") instanceof Boolean
                        ? (Boolean) data.get("has_more")
                        : Boolean.FALSE;
                
                if (!hasMore) break;
                
                pageToken = (String) data.get("page_token");
                if (pageToken == null || pageToken.isBlank()) break;
            }
            
        } catch (Exception e) {
            log.error("[LARK CONTACT] Error resolveCskhOpenIdByName: {}", e.getMessage(), e);
        }
        
        log.warn("[LARK CONTACT] Không tìm thấy CSKH '{}' trong department {}", cskhName, departmentId);
        return null;
    }
}
