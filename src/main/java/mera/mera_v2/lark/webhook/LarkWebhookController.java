package mera.mera_v2.lark.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.dto.BitableRecordResponse;
import mera.mera_v2.lark.webhook.dto.PosOrderWebhook;
import mera.mera_v2.lark.webhook.service.BaseTableMappingService;
import mera.mera_v2.lark.webhook.service.LarkBitableService;
import mera.mera_v2.lark.webhook.service.LarkImService;
import mera.mera_v2.lark.webhook.service.LarkSendMessage;
import mera.mera_v2.lark.webhook.service.PosToBitableMapper;
import mera.mera_v2.lark.webhook.service.SellerBaseMappingService;
import mera.mera_v2.lark.webhook.service.WebhookConfigService;
import mera.mera_v2.lark.webhook.service.WebhookPersistenceService;
import mera.mera_v2.lark.webhook.service.TenantTokenService;
import mera.mera_v2.entity.LarkBitableConfig;
import mera.mera_v2.lark.config.LarkBitableConfigService;
import mera.mera_v2.cskh.mapping.CskhBaseMappingService;
import mera.mera_v2.lark.webhook.config.LarkBaseProperties;
import mera.mera_v2.lark.webhook.config.SalesTablesConfig;
import mera.mera_v2.lark.token.TokenStorageService;
import mera.mera_v2.lark.webhook.scheduler2.TokenRefreshScheduler;
import mera.mera_v2.repository.PendingFollowupNotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;

@Slf4j
@RestController
@RequestMapping("/api/lark")
@RequiredArgsConstructor
public class LarkWebhookController {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectWriter prettyWriter = mapper.writerWithDefaultPrettyPrinter();
    private final LarkBitableService bitableService;
    private final PosToBitableMapper posToBitableMapper;

    @Autowired(required = false)
    private RestTemplate restTemplate;
    
    @Autowired(required = false)
    private LarkImService larkImService;
    
    @Autowired(required = false)
    private LarkSendMessage larkSendMessage;

    @Autowired(required = false)
    private LarkBaseProperties larkBaseProperties;
    
    @Autowired(required = false)
    private TokenStorageService tokenStorageService;
    
    @Autowired(required = false)
    private TokenRefreshScheduler tokenRefreshScheduler;
    
    @Autowired(required = false)
    private BaseTableMappingService baseTableMappingService;

    @Autowired(required = false)
    private SellerBaseMappingService sellerBaseMappingService;
    
    @Autowired(required = false)
    private SalesTablesConfig salesTablesConfig;

    @Autowired(required = false)
    private WebhookConfigService webhookConfigService;

    @Autowired(required = false)
    private TenantTokenService tenantTokenService;

    @Autowired(required = false)
    private LarkBitableConfigService larkBitableConfigService;

    @Autowired(required = false)
    private mera.mera_v2.lark.token.TokenStorageService tokenStorageServiceForTenant;

    @Autowired(required = false)
    private WebhookPersistenceService webhookPersistenceService;

    @Autowired(required = false)
    private PendingFollowupNotificationRepository pendingFollowupNotificationRepository;

    @Autowired(required = false)
    private CskhBaseMappingService cskhBaseMappingService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Value("${pancake.webhook.secret:}")
    private String expectedSecret;
    
    @Value("${lark.bitable.app-token}")
    private String defaultAppToken;
    
    @Value("${lark.bitable.table-id}")
    private String tableId;
    
    @Value("${lark.bitable.auto-create:true}")
    private boolean autoCreateRecord;

    @PostMapping("/orders")
    public ResponseEntity<String> onOrderWebhook(
            @RequestHeader(value = "X-Pancake-Secret", required = false) String secret,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody String rawBody
    ) {
        log.info("data: " + rawBody);

        if (expectedSecret != null && !expectedSecret.isBlank()
                && !expectedSecret.equals(secret)) {
            log.error("Invalid X-Pancake-Secret");
            return ResponseEntity.status(401).body("unauthorized");
        }

        try {
            JsonNode root = mapper.readTree(rawBody);
            PosOrderWebhook orderWebhook = mapper.treeToValue(root, PosOrderWebhook.class);

            // ============ LUU DATA VAO DATABASE ============
            try {
                if (webhookPersistenceService != null) {
                    log.info("=== BAT DAU LUU DATA VAO DB ===");
                    WebhookPersistenceService.PersistenceResult dbResult = 
                            webhookPersistenceService.saveFromWebhook(root);
                    if (dbResult.isSuccess()) {
                        log.info("=== HOAN THANH LUU DB ===");
                        log.info("   Order: saved={}, updated={}", 
                                dbResult.isOrderSaved(), dbResult.isOrderUpdated());
                        log.info("   Customer: saved={}, updated={}", 
                                dbResult.isCustomerSaved(), dbResult.isCustomerUpdated());
                        log.info("   Items: {}, Payments: {}, Histories: {}", 
                                dbResult.getItemsSaved(), dbResult.getPaymentsSaved(), 
                                dbResult.getHistoriesSaved());
                    } else {
                        log.warn("=== LUU DB THAT BAI: {} ===", dbResult.getErrorMessage());
                    }
                } else {
                    log.warn("WebhookPersistenceService khong kha dung, bo qua luu DB");
                }
            } catch (Exception e) {
                log.error("Loi khi luu data vao DB: {}", e.getMessage(), e);
            }

            // ============ CHUC NANG 1: CHECK LICH SU ACCOUNT ============
            try {
                logLatestAccountHistory(orderWebhook);
                if (orderWebhook.hasAccountChanged()) {
                    log.info("Account da thay doi - gui thong bao");
                    try {
                        sendAccountChangeNotification(orderWebhook);
                    } catch (Exception ex) {
                        log.error("Gui thong bao account change that bai: {}", ex.getMessage(), ex);
                    }
                }
            } catch (Exception e) {
                log.error("Loi khi check lich su account: {}", e.getMessage(), e);
            }

            // ============ CHUC NANG 2: CHECK STATUS ============
            try {
                Integer status = extractStatus(root);

                if (status == null) {
                    // Status khong tim thay
                } else if (!webhookConfigService.shouldProcess(status)) {
                    log.info("Status {} khong duoc bat trong config, bo qua", status);
                } else {
                    processStatusLogic(status, root, orderWebhook);
                }
            } catch (Exception e) {
                log.error("Loi khi xu ly status: {}", e.getMessage(), e);
            }

            // ============ CHUC NANG 3: CHECK TAGS ============
            try {
                boolean hasDongBoDataTag = hasDongBoDataTag(root);
                if (hasDongBoDataTag) {
                    if (autoCreateRecord) {
                        createBitableRecord(root);
                    }
                }
            } catch (Exception e) {
                log.error("Loi khi xu ly tags: {}", e.getMessage(), e);
            }

            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Failed to parse webhook JSON: {}", e.getMessage());
            return ResponseEntity.badRequest().body("bad request");
        }
    }

    private Integer extractStatus(JsonNode root) {
        if (root.has("status") && root.get("status").isNumber()) {
            return root.get("status").asInt();
        } else if (root.has("data") && root.get("data").has("status")
                && root.get("data").get("status").isNumber()) {
            return root.get("data").get("status").asInt();
        }
        return null;
    }

    private void processStatusLogic(Integer status, JsonNode webhookData, PosOrderWebhook orderWebhook) throws Exception {
        if (status == 1) {
            log.info("Status = 1: Tao ban ghi moi");
            if (autoCreateRecord) {
                createBitableRecord(webhookData);
            }
        }
        // Status = 6 da duoc bo xu ly
    }

    private void logLatestAccountHistory(PosOrderWebhook orderWebhook) {
        if (orderWebhook.getHistories() != null && !orderWebhook.getHistories().isEmpty()) {
            log.info("   Total histories: {}", orderWebhook.getHistories().size());
            PosOrderWebhook.HistoryItem latestHistory = orderWebhook.getLatestHistory();
            if (latestHistory != null) {
                if (latestHistory.getAccount() != null) {
                    Object oldVal = latestHistory.getAccount().getOldValue();
                    Object newVal = latestHistory.getAccount().getNewValue();
                    boolean changed = orderWebhook.hasAccountChanged();
                    log.info("   Account changed: {}", changed);
                } else {
                    log.info("   No account field in latest history");
                }
            } else {
                log.info("   Latest history is null");
            }
        } else {
            log.info("   No histories found");
        }
    }

    private boolean hasDongBoDataTag(JsonNode root) {
        JsonNode tagsNode = root.get("tags");
        if (tagsNode == null || !tagsNode.isArray()) {
            if (root.has("data")) {
                JsonNode dataNode = root.get("data");
                tagsNode = dataNode.get("tags");
            }
        }

        if (tagsNode == null || !tagsNode.isArray()) {
            return false;
        }

        for (JsonNode tagNode : tagsNode) {
            JsonNode idNode = tagNode.get("id");
            if (idNode != null && idNode.isNumber() && idNode.asInt() == 32) {
                return true;
            }
            JsonNode nameNode = tagNode.get("name");
            if (nameNode != null && "Đồng bộ DATA".equals(nameNode.asText())) {
                return true;
            }
        }
        return false;
    }

    private void sendAccountChangeNotification(PosOrderWebhook orderWebhook) throws Exception {
        String orderId = orderWebhook.getId() != null ? orderWebhook.getId().toString() : "unknown";
        String oldAccount = orderWebhook.getOldAccountValue();
        String newAccount = orderWebhook.getNewAccountValue();

        log.info("Sending account change notification:");
        log.info("   Order ID: {}", orderId);
        log.info("   Old Account: {}", oldAccount);
        log.info("   New Account: {}", newAccount);

        String tenantToken = null;
        if (tenantTokenService != null) {
            tenantToken = tenantTokenService.getTenantAccessToken();
        }
        if ((tenantToken == null || tenantToken.isBlank()) && tokenStorageServiceForTenant != null) {
            tenantToken = tokenStorageServiceForTenant.getTenantAccessToken();
        }

        if (tenantToken == null || tenantToken.isBlank()) {
            log.error("Tenant token is not available, cannot send message");
            return;
        }

        if (restTemplate == null) {
            log.error("RestTemplate is not available, cannot send message");
            return;
        }

        String messageContent = String.format("Đơn hàng đã thay đổi nguồn đơn: %s", orderId);
        log.info("   Message content: {}", messageContent);

        String defaultUserId = null;
        if (larkBaseProperties != null) {
            defaultUserId = larkBaseProperties.getDefaultUserId();
        }
        if (defaultUserId == null || defaultUserId.isBlank()) {
            defaultUserId = "ou_4cf48041bec4170651def0c025217097";
            log.warn("defaultUserId khong duoc cau hinh, dung gia tri mac dinh {}", defaultUserId);
        }
        log.info("   Sending to user ID: {}", defaultUserId);

        try {
            sendLarkMessage(tenantToken, defaultUserId, messageContent);
            log.info("Account change notification sent successfully");
        } catch (Exception e) {
            log.error("Failed to send account change notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendLarkMessage(String accessToken, String receiveId, String messageContent) throws Exception {
        String url = "https://open.larksuite.com/open-apis/im/v1/messages?receive_id_type=open_id";

        log.info("Calling Lark IM API:");
        log.info("   URL: {}", url);
        log.info("   Receive ID: {}", receiveId);
        log.info("   Message: {}", messageContent);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("content", "{\"text\":\"" + messageContent + "\"}");
        body.put("msg_type", "text");
        body.put("receive_id", receiveId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("   Response status: {}", response.getStatusCode());
            log.info("   Response body: {}", response.getBody());

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Lark API error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send message to Lark IM: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testWebhook(
            @RequestHeader(value = "X-Pancake-Secret", required = false) String secret,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody(required = false) String rawBody,
            @RequestHeader java.util.Map<String, String> allHeaders
    ) {
        log.info("TEST WEBHOOK - /api/lark/test");
        log.info("Headers: {}", allHeaders);
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("status", "received");
        response.put("hasBody", rawBody != null);
        response.put("bodyLength", rawBody != null ? rawBody.length() : 0);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new java.util.HashMap<>();
        health.put("status", "UP");
        health.put("endpoint", "/api/lark/orders");
        health.put("autoCreate", autoCreateRecord);

        String userToken = getUserAccessToken();
        health.put("hasUserToken", userToken != null);

        if (tokenStorageServiceForTenant != null) {
            String tenantToken = tokenStorageServiceForTenant.getTenantAccessToken();
            health.put("hasTenantTokenInStorage", tenantToken != null);
            health.put("tenantTokenValidInStorage", tokenStorageServiceForTenant.isTenantTokenValid());
        }

        if (tenantTokenService != null) {
            try {
                String tenantToken = tenantTokenService.getTenantAccessToken();
                health.put("hasTenantTokenFromAPI", tenantToken != null);
                health.put("tenantTokenValidFromAPI", tenantTokenService.isTokenValid());
            } catch (Exception e) {
                health.put("hasTenantTokenFromAPI", false);
                health.put("tenantTokenErrorFromAPI", e.getMessage());
            }
        }

        return ResponseEntity.ok(health);
    }

    @GetMapping("/tenant-token")
    public ResponseEntity<Map<String, Object>> getTenantToken() {
        Map<String, Object> response = new java.util.HashMap<>();

        if (tenantTokenService == null) {
            response.put("status", "error");
            response.put("message", "TenantTokenService is not available");
            return ResponseEntity.status(500).body(response);
        }

        try {
            String tenantToken = tenantTokenService.getTenantAccessToken();
            response.put("status", "success");
            response.put("tenantToken", tenantToken);
            response.put("isValid", tenantTokenService.isTokenValid());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    private void createBitableRecord(JsonNode webhookData) throws Exception {
        log.info("Creating Bitable record from webhook data...");
        
        PosOrderWebhook orderWebhook = mapper.treeToValue(webhookData, PosOrderWebhook.class);
        
        log.info("Order ID: {}, Status: {}", orderWebhook.getId(), orderWebhook.getStatus());

        // Lay ten CSKH tu webhook
        PosOrderWebhook.AssigningSeller cskh = posToBitableMapper.getAssigningCare(orderWebhook);
        String cskhName = (cskh != null && cskh.getName() != null) ? cskh.getName().trim() : null;
        
        log.info("CSKH Name: {}", cskhName != null ? cskhName : "(null)");

        // Lay Base ID va Table ID tu mapping CSKH (tu /admin/cskh-mapping)
        String appToken = null;
        String targetTableId = null;
        String viewId = null;

        if (cskhName != null && !cskhName.isBlank() && cskhBaseMappingService != null) {
            // Trich xuat phone tu ten CSKH (vi du: "Hà Quang Vượng Sale 2 NT 0968420624" -> "0968420624")
            String cskhPhone = extractPhoneFromName(cskhName);
            log.info("Extracted phone from CSKH name '{}': {}", cskhName, cskhPhone);
            
            CskhBaseMappingService.CskhMappingResult result = null;
            if (cskhPhone != null) {
                result = cskhBaseMappingService.findMappingResultByPhone(cskhPhone);
            }
            
            if (result != null && result.getBaseId() != null) {
                appToken = result.getBaseId();
                targetTableId = result.getKhachHangTableId();
                viewId = result.getViewId();
                log.info("✅ Found mapping for CSKH '{}' (phone={}): Base ID={}, Table ID={}, View ID={}, Base Name={}",
                        cskhName, cskhPhone, appToken, targetTableId, viewId, result.getBaseName());
            } else {
                log.error("❌ No mapping found for CSKH '{}' (phone={}). Cannot create record without table ID.", cskhName, cskhPhone);
                return;
            }
        } else {
            log.error("❌ CSKH name is null or CskhBaseMappingService is not available. Cannot create record.");
            return;
        }

        // Kiem tra table ID hop le
        if (targetTableId == null || targetTableId.isBlank()) {
            log.error("❌ Table ID is null or blank for CSKH '{}'. Cannot create record.", cskhName);
            return;
        }

        // Map sang Bitable fields
        Map<String, Object> fields = posToBitableMapper.mapToBitableFields(orderWebhook);
        
        // Lay token
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalStateException("User access token is not available. Please login at /token first.");
        }
        
        // Kiem tra so dien thoai trung lap bang filter API
        String phoneNumber = posToBitableMapper.getDienThoai(orderWebhook);
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            log.info("Checking if phone number already exists in Lark Bitable: {}", phoneNumber);
            
            try {
                String searchViewId = viewId != null ? viewId : "vew5Ou4Kee";
                // Su dung filter API de kiem tra chinh xac hon
                boolean phoneExists = bitableService.checkPhoneExistsWithFilter(
                        appToken, 
                        targetTableId, 
                        userAccessToken, 
                        phoneNumber,
                        searchViewId
                );
                
                if (phoneExists) {
                    log.warn("Phone number '{}' already exists in Lark Bitable table. Skipping record creation.", phoneNumber);
                    return;
                } else {
                    log.info("Phone number '{}' does not exist in Lark Bitable. Proceeding with record creation.", phoneNumber);
                }
            } catch (Exception e) {
                log.error("Error checking phone existence in Lark: {}. Proceeding with record creation as fallback.", e.getMessage());
            }
        } else {
            log.warn("Phone number is null or blank. Cannot check for duplicates in Lark. Proceeding with record creation.");
        }
        
        // Tao record
        BitableRecordResponse response = bitableService.createRecord(appToken, targetTableId, userAccessToken, fields);
        
        if (response.isSuccess() && response.getData() != null) {
            String recordId = response.getData().getRecord() != null
                    ? response.getData().getRecord().getRecordId()
                    : "unknown";
            log.info("Created Bitable record successfully: recordId={}", recordId);

            // === SCHEDULE 30-MIN PENDING NOTIFICATION ===
            // Neu link_record_ids = null → khach hang chua co trong Bang Khach Hang
            // → se dua vao Bang Khao Sat → can check lai sau 30 phut
            List<String> linkRecordIds = response.getLinkRecordIds();
            if (linkRecordIds == null || linkRecordIds.isEmpty()) {
                scheduleFollowupIfPossible(phoneNumber, appToken, targetTableId, viewId, recordId, orderWebhook);
            } else {
                log.info("Record {} da co link sang Bang Khach Hang (link_record_ids={}), bo qua schedule 30-phut",
                        recordId, linkRecordIds.size());
            }
        } else {
            throw new RuntimeException(String.format("Bitable error: code=%d, msg=%s", 
                    response.getCode(), response.getMsg()));
        }
    }
    
    private void processStatus6Delete(JsonNode webhookData) throws Exception {
        log.info("Processing status = 6: Delete record and save to deleted table...");
        
        PosOrderWebhook orderWebhook = mapper.treeToValue(webhookData, PosOrderWebhook.class);
        
        String orderSourcesName = orderWebhook.getOrderSourcesName();
        log.info("order_sources_name: {}", orderSourcesName);
        
        if (orderSourcesName == null || !orderSourcesName.equals("Facebook")) {
            log.info("Skipping delete. order_sources_name = '{}' (only delete when = 'Facebook')", 
                    orderSourcesName != null ? orderSourcesName : "null");
            return;
        }
        
        log.info("order_sources_name = 'Facebook', proceeding with delete...");

        String appToken = getDefaultBaseId();
        String targetTableId = getDefaultTableId();

        PosOrderWebhook.AssigningSeller cskh = posToBitableMapper.getAssigningCare(orderWebhook);
        String cskhName = (cskh != null && cskh.getName() != null) ? cskh.getName().trim() : null;

        if (cskhName != null && !cskhName.isBlank()) {
            // Try CskhBaseMappingService first (DB-based mapping)
            if (cskhBaseMappingService != null) {
                CskhBaseMappingService.CskhMappingResult result = cskhBaseMappingService.findMappingResultByPhone(cskhName);
                if (result != null && result.getBaseId() != null) {
                    appToken = result.getBaseId();
                    if (result.getKhachHangTableId() != null && !result.getKhachHangTableId().isBlank()) {
                        targetTableId = result.getKhachHangTableId();
                    }
                    log.info("✅ Found mapping via DB for CSKH '{}': Base ID={}, Table ID={}", cskhName, appToken, targetTableId);
                } else {
                    log.warn("⚠️ No mapping in DB for CSKH '{}', trying in-memory services", cskhName);
                    if (sellerBaseMappingService != null) {
                        java.util.Optional<String> baseIdOpt = sellerBaseMappingService.findBaseIdBySellerName(cskhName);
                        if (baseIdOpt.isPresent()) {
                            appToken = baseIdOpt.get();
                        } else if (baseTableMappingService != null) {
                            java.util.Optional<mera.mera_v2.lark.webhook.dto.BaseTableMapping> mapping =
                                    baseTableMappingService.findMappingByBaseName(cskhName);
                            if (mapping.isPresent()) {
                                appToken = mapping.get().getBaseId();
                                if (mapping.get().getTableId() != null && !mapping.get().getTableId().isBlank()) {
                                    targetTableId = mapping.get().getTableId();
                                }
                            }
                        }
                    } else if (baseTableMappingService != null) {
                        java.util.Optional<mera.mera_v2.lark.webhook.dto.BaseTableMapping> mapping =
                                baseTableMappingService.findMappingByBaseName(cskhName);
                        if (mapping.isPresent()) {
                            appToken = mapping.get().getBaseId();
                            if (mapping.get().getTableId() != null && !mapping.get().getTableId().isBlank()) {
                                targetTableId = mapping.get().getTableId();
                            }
                        }
                    }
                }
            } else if (sellerBaseMappingService != null) {
                java.util.Optional<String> baseIdOpt = sellerBaseMappingService.findBaseIdBySellerName(cskhName);
                if (baseIdOpt.isPresent()) {
                    appToken = baseIdOpt.get();
                } else if (baseTableMappingService != null) {
                    java.util.Optional<mera.mera_v2.lark.webhook.dto.BaseTableMapping> mapping =
                            baseTableMappingService.findMappingByBaseName(cskhName);
                    if (mapping.isPresent()) {
                        appToken = mapping.get().getBaseId();
                        if (mapping.get().getTableId() != null && !mapping.get().getTableId().isBlank()) {
                            targetTableId = mapping.get().getTableId();
                        }
                    }
                }
            } else if (baseTableMappingService != null) {
                java.util.Optional<mera.mera_v2.lark.webhook.dto.BaseTableMapping> mapping =
                        baseTableMappingService.findMappingByBaseName(cskhName);
                if (mapping.isPresent()) {
                    appToken = mapping.get().getBaseId();
                    if (mapping.get().getTableId() != null && !mapping.get().getTableId().isBlank()) {
                        targetTableId = mapping.get().getTableId();
                    }
                }
            }
        }
        
        String phoneNumber = posToBitableMapper.getDienThoai(orderWebhook);
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.error("Phone number is null, cannot process status 6");
            return;
        }
        
        log.info("Phone number to search: {}", phoneNumber);
        
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalStateException("User access token is not available");
        }
        
        String viewId = "vew5Ou4Kee";
        String recordId = bitableService.findRecordIdByPhone(
                appToken, 
                targetTableId, 
                userAccessToken, 
                phoneNumber,
                viewId
        );
        
        if (recordId == null || recordId.isBlank()) {
            log.error("Cannot find record ID for phone: {}. Skipping delete.", phoneNumber);
            return;
        }
        
        log.info("Found record ID: {} for phone: {}", recordId, phoneNumber);
        
        // Backup to deleted table
        String deletedBaseId = "WA2wblztIaFfZ8sRG8ElIOJdgXD";
        String deletedTableId = "tblkKixkzwtyzUMP";
        
        try {
            Map<String, Object> fields = posToBitableMapper.mapToBitableFields(orderWebhook);
            bitableService.createRecord(deletedBaseId, deletedTableId, userAccessToken, fields);
            log.info("Saved deleted record to backup table");
        } catch (Exception e) {
            log.error("Failed to save backup record: {}", e.getMessage());
        }
        
        // Delete original record
        try {
            bitableService.deleteRecord(appToken, targetTableId, recordId, userAccessToken);
            log.info("Successfully deleted record: recordId={}", recordId);
        } catch (Exception e) {
            log.error("Failed to delete record: {}", e.getMessage());
            throw e;
        }
    }
    
    private void processStatus6UpdateSalesTables(JsonNode webhookData) throws Exception {
        log.info("Processing status = 6: Update sales tables...");
        
        PosOrderWebhook orderWebhook = mapper.treeToValue(webhookData, PosOrderWebhook.class);
        
        String phoneNumber = posToBitableMapper.getDienThoai(orderWebhook);
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("Phone number is null, cannot update sales tables");
            return;
        }
        
        log.info("Searching for phone in sales tables: {}", phoneNumber);
        
        if (salesTablesConfig == null) {
            log.warn("SalesTablesConfig is not available");
            return;
        }
        
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            log.error("User access token is not available");
            return;
        }
        
        String salesBaseId = "VsLjbnWlfapGXhszsvqlRm6QgIf";
        List<SalesTablesConfig.SalesTable> salesTables = salesTablesConfig.getTables();
        
        if (salesTables == null || salesTables.isEmpty()) {
            salesTablesConfig.refreshTables(userAccessToken, salesBaseId);
            salesTables = salesTablesConfig.getTables();
            if (salesTables == null || salesTables.isEmpty()) {
                log.error("Still no sales tables found after refresh");
                return;
            }
        }
        
        log.info("Searching in {} sales tables", salesTables.size());
        String viewId = "vewE3Ope6x";
        
        // Search in tables
        for (SalesTablesConfig.SalesTable table : salesTables) {
            try {
                String recordId = bitableService.findRecordIdByPhoneInTable(
                        salesBaseId,
                        table.getTableId(),
                        userAccessToken,
                        phoneNumber,
                        viewId
                );
                
                if (recordId != null && !recordId.isBlank()) {
                    log.info("Found record in table {} ({}): {}", table.getTableId(), table.getDisplayName(), recordId);
                    
                    // Update with "Trang thai mess" = "Huy"
                    mera.mera_v2.lark.webhook.dto.BitableBatchUpdateRequest batchUpdateRequest = 
                            mera.mera_v2.lark.webhook.dto.BitableBatchUpdateRequest.builder()
                            .records(List.of(
                                    mera.mera_v2.lark.webhook.dto.BitableBatchUpdateRequest.UpdateRecord.builder()
                                            .recordId(recordId)
                                            .fields(Map.of("Trạng thái mess", "Hủy"))
                                            .build()
                            ))
                            .build();
                    
                    bitableService.batchUpdateRecords(salesBaseId, table.getTableId(), userAccessToken, batchUpdateRequest);
                    log.info("Updated record in table {}: Trạng thái mess = Hủy", table.getDisplayName());
                    return;
                }
            } catch (Exception e) {
                log.debug("Error searching in table {}: {}", table.getTableId(), e.getMessage());
            }
        }
        
        log.warn("No record found in any sales table for phone: {}", phoneNumber);
    }
    
    private static class SearchResult {
        final String tableId;
        final String recordId;
        final String displayName;
        
        SearchResult(String tableId, String recordId, String displayName) {
            this.tableId = tableId;
            this.recordId = recordId;
            this.displayName = displayName;
        }
    }
    
    private String getUserAccessToken() {
        return getUserAccessToken(true);
    }

    private String getUserAccessToken(boolean ensureValid) {
        if (ensureValid && tokenRefreshScheduler != null) {
            try {
                tokenRefreshScheduler.refreshTokenIfNeeded();
            } catch (Exception e) {
                log.warn("Failed to refresh token if needed: {}", e.getMessage());
            }
        }

        if (tokenStorageService != null) {
            String token = tokenStorageService.getUserAccessToken();
            if (token != null && !token.isBlank()) {
                return token;
            }
        }

        if (larkBaseProperties != null && larkBaseProperties.getUserAccessToken() != null) {
            String token = larkBaseProperties.getUserAccessToken().trim();
            if (!token.isBlank()) {
                return token;
            }
        }

        if (tokenStorageServiceForTenant != null) {
            String tenantToken = tokenStorageServiceForTenant.getTenantAccessToken();
            if (tenantToken != null && !tenantToken.isBlank()) {
                log.warn("Using tenant access token from storage");
                return tenantToken;
            }
        }

        if (tenantTokenService != null) {
            try {
                String tenantToken = tenantTokenService.getTenantAccessToken();
                if (tenantToken != null && !tenantToken.isBlank()) {
                    log.warn("Using tenant access token from API");
                    return tenantToken;
                }
            } catch (Exception e) {
                log.error("Failed to get tenant access token: {}", e.getMessage());
            }
        }

        log.error("No access token available");
        return null;
    }

    /**
     * Lay Base ID tu DB config hoac fallback sang properties
     */
    private String getDefaultBaseId() {
        if (larkBitableConfigService != null) {
            try {
                return larkBitableConfigService.getDefaultConfig()
                        .map(LarkBitableConfig -> LarkBitableConfig.getBaseId())
                        .orElse(defaultAppToken);
            } catch (Exception e) {
                log.warn("Error getting Base ID from DB config: {}", e.getMessage());
            }
        }
        return defaultAppToken;
    }

    /**
     * Lay Table ID tu DB config hoac fallback sang properties
     */
    private String getDefaultTableId() {
        if (larkBitableConfigService != null) {
            try {
                return larkBitableConfigService.getDefaultConfig()
                        .map(LarkBitableConfig -> LarkBitableConfig.getTableId())
                        .orElse(tableId);
            } catch (Exception e) {
                log.warn("Error getting Table ID from DB config: {}", e.getMessage());
            }
        }
        return tableId;
    }

    /**
     * Lay View ID tu DB config
     */
    private String getDefaultViewId() {
        if (larkBitableConfigService != null) {
            try {
                return larkBitableConfigService.getDefaultConfig()
                        .map(LarkBitableConfig -> LarkBitableConfig.getViewId())
                        .orElse(null);
            } catch (Exception e) {
                log.warn("Error getting View ID from DB config: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * Trich xuat so dien thoai tu ten CSKH
     * Ví dụ: "Hà Quang Vượng Sale 2 NT 0968420624" -> "0968420624"
     */
    private String extractPhoneFromName(String text) {
        if (text == null || text.isEmpty()) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:\\+84|0)[\\s\\.\\-]*[35789][0-9\\s\\.\\-]{7,10}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String raw = matcher.group();
            String phone = raw.replaceAll("[^0-9]", "");
            if (phone.startsWith("84") && phone.length() > 9) phone = "0" + phone.substring(2);
            else if (!phone.startsWith("0") && phone.length() == 9) phone = "0" + phone;
            if (phone.length() == 10 && phone.matches("0[35789].*")) return phone;
        }
        // Fallback: tim 10 so lien tiep
        java.util.regex.Pattern simplePattern = java.util.regex.Pattern.compile("[0-9]{10}");
        java.util.regex.Matcher simpleMatcher = simplePattern.matcher(text.replaceAll("[^0-9]", ""));
        if (simpleMatcher.find()) return simpleMatcher.group();
        return null;
    }

    // ============== PENDING FOLLOWUP NOTIFICATION ==============

    /**
     * Tao ban ghi PendingFollowupNotification de scheduler check sau 30 phut.
     * Chi tao khi link_record_ids = null (khach hang chua co trong Bang Khach Hang).
     */
    private void scheduleFollowupIfPossible(
            String phoneNumber,
            String baseId,
            String tableId,
            String viewId,
            String createdRecordId,
            PosOrderWebhook orderWebhook
    ) {
        if (pendingFollowupNotificationRepository == null) {
            log.warn("[PendingFollowup] Repository not available, skip scheduling notification");
            return;
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("[PendingFollowup] Phone number is blank, skip scheduling notification");
            return;
        }

        try {
            mera.mera_v2.entity.PendingFollowupNotification pending =
                    new mera.mera_v2.entity.PendingFollowupNotification();
            pending.setPhoneNumber(phoneNumber);
            pending.setBaseId(baseId);
            pending.setTableId(tableId);
            pending.setViewId(viewId != null ? viewId : "vew5Ou4Kee");
            pending.setCreatedRecordId(createdRecordId);

            // Lay ten khach hang
            String customerName = posToBitableMapper.getTenKhach(orderWebhook);
            pending.setCustomerName(customerName);

            // Dat thoi gian can check = 30 phut sau
            pending.setScheduledAt(java.time.LocalDateTime.now().plusMinutes(30));
            pending.setProcessed(false);
            pending.setRetryCount(0);
            pending.setCreatedAt(java.time.LocalDateTime.now());

            pendingFollowupNotificationRepository.save(pending);
            log.info("[PendingFollowup] Scheduled notification for phone={} at {} (recordId={})",
                    phoneNumber, pending.getScheduledAt(), createdRecordId);
        } catch (Exception e) {
            log.error("[PendingFollowup] Failed to schedule notification for phone={}: {}",
                    phoneNumber, e.getMessage());
        }
    }
}
