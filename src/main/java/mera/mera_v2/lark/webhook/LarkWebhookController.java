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
import mera.mera_v2.customer.Service.SearchConfigService;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private SearchConfigService searchConfigService;

    /**
     * Hai pool TACH RIENG, co chu dich: neu dung chung mot pool thi khi DB ket khoa,
     * cac task luu DB se chiem het thread va bit luon duong tao ban ghi Lark.
     * Hang doi co gioi han + CallerRunsPolicy: qua tai that su thi tra nguoc ap luc
     * ve thread HTTP thay vi phinh queue vo han roi OOM.
     */
    private final ExecutorService dbExecutor = newBoundedPool(4, "webhook-db");
    private final ExecutorService larkExecutor = newBoundedPool(5, "webhook-lark");

    private static ExecutorService newBoundedPool(int size, String namePrefix) {
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                size, size,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, namePrefix + "-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return pool;
    }

    @jakarta.annotation.PreDestroy
    void shutdownExecutors() {
        dbExecutor.shutdown();
        larkExecutor.shutdown();
    }

    @Autowired(required = false)
    private mera.mera_v2.lark.webhook.service.WebhookCreationLogService creationLogs;

    @Value("${lark.bitable.status-one-date-field:Ngày tạo}")
    private String statusOneDateField = "Ngày tạo";

    @Value("${pancake.webhook.history-zone:Asia/Ho_Chi_Minh}")
    private String historyZone = "Asia/Ho_Chi_Minh";

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
        // Body webhook (~2-5KB/lan, moi lan sua don POS ban lai) chi log o DEBUG de file log khong phinh.
        log.debug("data: {}", rawBody);

        if (expectedSecret != null && !expectedSecret.isBlank()
                && !expectedSecret.equals(secret)) {
            log.error("Invalid X-Pancake-Secret");
            return ResponseEntity.status(401).body("unauthorized");
        }

        try {
            JsonNode root = mapper.readTree(rawBody);
            PosOrderWebhook orderWebhook = mapper.treeToValue(root, PosOrderWebhook.class);

            // Hai nhanh nay KHONG phu thuoc nhau (nhanh Lark chi doc payload + search_config),
            // nen chay song song tren 2 pool rieng: DB ket khoa cung khong chan viec tao ban ghi Lark,
            // va Lark API cham cung khong giu thread HTTP.
            var attempt = mera.mera_v2.lark.webhook.service.WebhookCreationLogService.Entry.of(root);
            if (creationLogs != null) {
                try { creationLogs.append(attempt); }
                catch (java.io.IOException logFailure) {
                    log.error("Cannot persist webhook receipt");
                    return ResponseEntity.status(503).body("webhook audit unavailable");
                }
            }
            dbExecutor.execute(() -> persistToDatabase(root));
            larkExecutor.execute(() -> processLarkFlow(root, orderWebhook, attempt));

            // Tra 200 ngay de Pancake khong timeout roi ban lai webhook lam nang them he thong.
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Failed to parse webhook JSON: {}", e.getMessage());
            return ResponseEntity.badRequest().body("bad request");
        }
    }

    /** Nhanh 1: luu data vao pos_db. Loi o day khong duoc anh huong nhanh Lark. */
    private void persistToDatabase(JsonNode root) {
        try {
            if (webhookPersistenceService == null) {
                log.warn("WebhookPersistenceService khong kha dung, bo qua luu DB");
                return;
            }
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
        } catch (Exception e) {
            log.error("Loi khi luu data vao DB: {}", e.getMessage(), e);
        }
    }

    /** Nhanh 2: thong bao doi nguon + tao ban ghi Lark Bitable. */
    private void processLarkFlow(JsonNode root, PosOrderWebhook orderWebhook,
            mera.mera_v2.lark.webhook.service.WebhookCreationLogService.Entry attempt) {
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

        try {
            if (!hasDongBoDataTag(root)) {
                attempt.finish("SKIPPED", "Không có tag Đồng bộ DATA (ID 32)");
                return;
            }
            if (!autoCreateRecord) {
                attempt.finish("SKIPPED", "Đang tắt tự động tạo bản ghi");
                return;
            }
            attempt.finish("PROCESSING", "Đang xử lý");
            if (creationLogs != null) creationLogs.append(attempt);
            // Tag is the only business trigger. Status is used solely to resolve the date.
            createBitableRecord(root, attempt);
        } catch (Exception e) {
            String detail = e.getClass().getSimpleName();
            if (e instanceof org.springframework.web.client.RestClientResponseException responseError) {
                detail = "HTTP " + responseError.getStatusCode().value();
            }
            attempt.finish("FAILED", attempt.getStage() + " thất bại (" + detail
                    + "). Kiểm tra log máy chủ; nếu lỗi lúc tạo, đối soát Lark trước khi gửi lại.");
            log.error("Webhook attempt={} order={} stage={} failed: {}", attempt.getAttemptId(),
                    attempt.getOrderId(), attempt.getStage(), detail);
        } finally {
            if (creationLogs != null) {
                try { creationLogs.append(attempt); }
                catch (java.io.IOException e) {
                    log.error("Cannot persist webhook result attempt={} outcome={}", attempt.getAttemptId(), attempt.getStatus());
                }
            }
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

    /**
     * Kiem tra don hang co nguon Facebook hay khong.
     * Don co page_id bat dau bang "pzl_" la Facebook (Pancake).
     */
    private boolean isFacebookSource(PosOrderWebhook orderWebhook) {
        if (orderWebhook == null) return false;

        // Kiem tra order_sources_name truoc (chinh xac nhat)
        String orderSourcesName = orderWebhook.getOrderSourcesName();
        if (orderSourcesName != null && !orderSourcesName.isBlank()) {
            String lower = orderSourcesName.toLowerCase().trim();
            // Neu ten nguon chua "zalo", "shopee", "lazada"... thi KHONG phai Facebook
            if (lower.contains("zalo") || lower.contains("shopee") 
                    || lower.contains("lazada") || lower.contains("tiktok")
                    || lower.contains("tiki") || lower.contains("sendo")
                    || lower.contains("website") || lower.contains("tong_dai")) {
                return false;
            }
            // Neu ten chua "facebook" hoac "fb" thi la Facebook
            if (lower.contains("facebook") || lower.contains("fb") || lower.contains("mess")) {
                return true;
            }
        }

        // Fallback: kiem tra page_id format
        String pageId = orderWebhook.getPageId();
        if (pageId == null || pageId.isBlank()) return false;
        // Pancake page_id format: pzl_xxxxx (Facebook page)
        return pageId.startsWith("pzl_") || pageId.startsWith("fb_");
    }

    /**
     * Kiem tra trang thai huy don.
     * Status huy: 5 (huy boi khach), 7 (huy he thong), 8 (huy khac), 9 (that bai).
     */
    private boolean isCancelledStatus(Integer status) {
        if (status == null) return false;
        // Status huy thuong gap: 5, 7, 8, 9, -1
        return status == 5 || status == 7 || status == 8 || status == 9 || status == -1;
    }

    /**
     * Kiem tra trong lich su co status = targetStatus khong.
     */
    private boolean hasStatusInHistory(PosOrderWebhook orderWebhook, int targetStatus) {
        if (orderWebhook == null || orderWebhook.getHistories() == null) return false;
        return orderWebhook.getHistories().stream()
                .filter(h -> h.getStatus() != null && h.getStatus().getNewValue() != null)
                .anyMatch(h -> {
                    Object newVal = h.getStatus().getNewValue();
                    if (newVal instanceof Number) {
                        return ((Number) newVal).intValue() == targetStatus;
                    }
                    try {
                        return Integer.parseInt(newVal.toString()) == targetStatus;
                    } catch (Exception e) {
                        return false;
                    }
                });
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
    
    private void createBitableRecord(JsonNode webhookData,
            mera.mera_v2.lark.webhook.service.WebhookCreationLogService.Entry attempt) throws Exception {
        log.info("Creating Bitable record from webhook data...");
        
        PosOrderWebhook orderWebhook = mapper.treeToValue(webhookData, PosOrderWebhook.class);
        
        log.info("Order ID: {}, Status: {}", orderWebhook.getId(), orderWebhook.getStatus());

        attempt.setStage("Tìm mapping CSKH");
        // Lay ten CSKH tu webhook
        PosOrderWebhook.AssigningSeller cskh = posToBitableMapper.getAssigningCare(orderWebhook);
        String cskhName = (cskh != null && cskh.getName() != null) ? cskh.getName().trim() : null;
        
        attempt.setCskh(cskhName == null ? "" : cskhName);
        log.info("CSKH Name: {}", cskhName != null ? cskhName : "(null)");

        // Lay Base ID va Table ID tu mapping CSKH (tu /admin/cskh-mapping)
        String appToken = null;
        String targetTableId = null;
        String viewId = null;

        // Prefer the explicit phone on the assigned care employee. Names may no
        // longer contain a phone, or may still contain an outdated phone.
        String cskhPhone = posToBitableMapper.getCskhPhoneNumber(orderWebhook);
        if (cskhPhone == null || cskhPhone.isBlank()) {
            cskhPhone = extractPhoneFromName(cskhName);
        }

        if (cskhPhone != null && !cskhPhone.isBlank() && searchConfigService != null) {

            SearchConfigService.CskhMappingResult result = searchConfigService.findMappingResultByPhone(cskhPhone);
            // SĐT tài khoản POS có thể khác SĐT trong tên (mapping lưu theo SĐT trong tên) -> thử thêm số trong tên
            String namePhone = extractPhoneFromName(cskhName);
            if (result == null && namePhone != null && !namePhone.equals(cskhPhone)) {
                result = searchConfigService.findMappingResultByPhone(namePhone);
                if (result != null) cskhPhone = namePhone;
            }

            if (result != null && result.getBaseId() != null && !result.getBaseId().isBlank()) {
                appToken = result.getBaseId();
                targetTableId = result.getKhachHangTableId();
                viewId = result.getViewId();
                attempt.setBaseId(appToken);
                attempt.setTableId(targetTableId);
                log.info("✅ Found mapping for CSKH '{}' (phone={}): Base ID={}, Table ID={}, View ID={}, Base Name={}",
                        cskhName, cskhPhone, appToken, targetTableId, viewId, result.getBaseName());
            } else {
                log.error("❌ No mapping found for CSKH '{}' (phone={}) in search_config. Cannot create record without table ID.", cskhName, cskhPhone);
                attempt.finish("WAITING_MAPPING", "Chưa có mapping SĐT CSKH trong search_config");
                return;
            }
        } else {
            log.error("Cannot resolve CSKH mapping: no care phone in payload/name, or SearchConfigService unavailable. Order ID={}", orderWebhook.getId());
            attempt.finish("WAITING_MAPPING", "Thiếu SĐT CSKH hoặc dịch vụ mapping");
            return;
        }

        // Kiem tra table ID hop le
        if (targetTableId == null || targetTableId.isBlank()) {
            log.error("❌ Table ID is null or blank for CSKH '{}'. Cannot create record.", cskhName);
            attempt.finish("WAITING_MAPPING", "Mapping chưa có Table KH");
            return;
        }

        // Map sang Bitable fields
        Map<String, Object> fields = posToBitableMapper.mapToBitableFields(orderWebhook);
        
        attempt.setStage("Lấy token Lark");
        // Lay token
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            attempt.finish("WAITING_TOKEN", "Chưa có token truy cập Lark, kiểm tra trang Token & Base");
            return;
        }
        
        attempt.setStage("Kiểm tra trùng SĐT");
        // Kiem tra so dien thoai trung lap bang filter API
        String phoneNumber = (String) fields.get("Điện thoại");
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            log.info("Checking if phone number already exists in Lark Bitable: {}", phoneNumber);
            
            try {
                String searchViewId = null; // Check the whole destination table, not a filtered view.
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
                    attempt.finish("DUPLICATE", "SĐT khách đã tồn tại trong bảng đích");
                    return;
                } else {
                    log.info("Phone number '{}' does not exist in Lark Bitable. Proceeding with record creation.", phoneNumber);
                }
            } catch (Exception e) {
                throw e;
            }
        } else {
            attempt.finish("WAITING_PHONE", "Thiếu SĐT khách để kiểm tra trùng");
            return;
        }
        
        attempt.setStage("Lấy ngày chuyển status = 1");
        java.time.Instant statusTime;
        try {
            statusTime = mera.mera_v2.lark.webhook.service.WebhookStatusDate.latestStatusOne(
                    webhookData, java.time.ZoneId.of(historyZone));
        } catch (java.time.DateTimeException | NumberFormatException invalidDate) {
            attempt.finish("WAITING_DATE", "Lịch sử chuyển status = 1 có updated_at không hợp lệ");
            return;
        }
        if (statusTime == null) {
            attempt.finish("WAITING_DATE", "Webhook chưa có lịch sử chuyển status = 1; chưa tạo để tránh sai ngày");
            return;
        }
        attempt.setStatusOneAt(statusTime.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime().toString());
        attempt.setStage("Kiểm tra cột ngày Lark");
        String dateIssue = bitableService.checkWritableDateField(appToken, targetTableId, userAccessToken, statusOneDateField);
        if (dateIssue != null) {
            attempt.finish("WAITING_FIELD", dateIssue);
            return;
        }
        fields.put(statusOneDateField, statusTime.toEpochMilli());
        attempt.setStage("Tạo bản ghi Lark");
        // Tao record
        BitableRecordResponse response = bitableService.createRecord(appToken, targetTableId, userAccessToken, fields);
        
        if (response.isSuccess() && response.getData() != null) {
            String recordId = response.getData().getRecord() != null
                    ? response.getData().getRecord().getRecordId()
                    : "unknown";
            if ("unknown".equals(recordId) || recordId == null || recordId.isBlank()) {
                attempt.finish("UNKNOWN", "API chưa trả record_id; kiểm tra Lark trước khi gửi lại webhook");
                return;
            }
            attempt.setRecordId(recordId);
            attempt.finish("CREATED", "Đã tạo bản ghi, ngày lấy từ lần chuyển status = 1 gần nhất");
            log.info("Created Bitable record successfully: recordId={}", recordId);

            // BUOC 2 (ghi pending_followup_notifications) DA BO.
            // Ly do: INSERT vao bang nay treo >60s lam ket ca 5 thread webhook-lark, chan han
            // viec tao ban ghi Lark — trong khi PendingFollowupScheduler (@Scheduled dang bi
            // comment) khong he doc bang do, nen du lieu ghi ra khong duoc dung vao viec gi.
            // Khi bat lai scheduler thi phai them lai loi goi scheduleFollowupIfPossible o day.
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
            // Try search_config first (DB-based mapping)
            if (searchConfigService != null) {
                String cskhPhone = extractPhoneFromName(cskhName);
                SearchConfigService.CskhMappingResult result =
                        cskhPhone != null ? searchConfigService.findMappingResultByPhone(cskhPhone) : null;
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

    // ============== BƯỚC 2: LƯU SDT VÀO PENDING ==============

    /**
     * Bước 2: Tạo bản ghi trong pending_followup_notifications để scheduler check sau 30 phút.
     * Lưu: SĐT, baseId/tableId/viewId của Bảng Liệu trình, recordId vừa tạo, tên khách, scheduledAt.
     *
     * Buoc nay chi la bookkeeping — ban ghi Lark DA tao xong truoc khi vao day. Neu INSERT bi
     * treo (khoa metadata / khoa dong) ma chay tren thread Lark thi ca 5 thread ket cung va
     * KHONG con ban ghi Lark nao duoc tao nua. Vi vay day day sang dbExecutor: Lark chay tiep
     * ngay lap tuc, cung lam bookkeeping cham chu khong mat ban ghi.
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

        // Doc tu payload ngay tren thread hien tai (re, khong cham DB), chi day phan INSERT sang nen.
        String customerName = posToBitableMapper.getTenKhach(orderWebhook);
        String resolvedViewId = viewId != null ? viewId : "vew5Ou4Kee";

        dbExecutor.execute(() -> {
            try {
                mera.mera_v2.entity.PendingFollowupNotification pending =
                        new mera.mera_v2.entity.PendingFollowupNotification();
                pending.setPhoneNumber(phoneNumber);
                pending.setBaseId(baseId);
                pending.setTableId(tableId);
                pending.setViewId(resolvedViewId);
                pending.setCreatedRecordId(createdRecordId);
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
        });
    }

}
