package mera.mera_v2.lark.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.PosTobase.dto.BitableRecordResponse;
import mera.mera_v2.PosTobase.dto.PosOrderWebhook;
import mera.mera_v2.PosTobase.dto.BaseTableMapping;
import mera.mera_v2.PosTobase.service.BaseTableMappingService;
import mera.mera_v2.PosTobase.service.LarkBitableService;
import mera.mera_v2.PosTobase.service.LarkImService;
import mera.mera_v2.PosTobase.service.LarkSendMessage;
import mera.mera_v2.PosTobase.service.PosToBitableMapper;
import mera.mera_v2.PosTobase.service.WebhookConfigService;
import mera.mera_v2.PosTobase.service.TenantTokenService;
import mera.mera_v2.reportstatusmesssale.config.LarkBaseProperties;
import mera.mera_v2.reportstatusmesssale.config.SalesTablesConfig;
import mera.mera_v2.getusertoken.service.TokenStorageService;
import mera.mera_v2.getusertoken.scheduler.TokenRefreshScheduler;
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
    private SalesTablesConfig salesTablesConfig;

    @Autowired(required = false)
    private WebhookConfigService webhookConfigService;

    @Autowired(required = false)
    private TenantTokenService tenantTokenService;

    @Autowired(required = false)
    private org.report.getusertoken.service.TokenStorageService tokenStorageServiceForTenant;

    @Autowired(required = false)
    private org.report.PosTobase.service.WebhookDataStorageService webhookDataStorageService;
    
    // Executor cho parallel processing
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
        log.info("data: "+rawBody);

        // Check secret
        if (expectedSecret != null && !expectedSecret.isBlank()
                && !expectedSecret.equals(secret)) {
            log.error(">>> âŒ Invalid X-Pancake-Secret");
            return ResponseEntity.status(401).body("unauthorized");
        }

        try {
            JsonNode root = mapper.readTree(rawBody);
            PosOrderWebhook orderWebhook = mapper.treeToValue(root, PosOrderWebhook.class);

            // ============ LÆ¯U WEBHOOK DATA VÃ€O FILE ============
            try {
                if (webhookDataStorageService != null) {
                    Integer status = extractStatus(root);
                    boolean hasDongBoDataTag = hasDongBoDataTag(root);
                    String additionalInfo = "status=" + status + ", hasDongBoDataTag=" + hasDongBoDataTag;
//                    webhookDataStorageService.saveWebhookData(rawBody, additionalInfo);
                }
            } catch (Exception e) {
                log.error("âŒ Failed to save webhook data to file: {}", e.getMessage());
            }

            // ============ CHá»¨C NÄ‚NG 1: CHECK Lá»ŠCH Sá»¬ ACCOUNT (Ä‘á»™c láº­p) ============
            try {
//                log.info("ðŸ” [CHá»¨C NÄ‚NG 1] KIá»‚M TRA Lá»ŠCH Sá»¬ ACCOUNT");
                logLatestAccountHistory(orderWebhook);
                if (orderWebhook.hasAccountChanged()) {
                    log.info("ðŸ“¢ Account Ä‘Ã£ thay Ä‘á»•i - gá»­i thÃ´ng bÃ¡o");
                    try {
                        sendAccountChangeNotification(orderWebhook);
                    } catch (Exception ex) {
                        log.error("âŒ Gá»­i thÃ´ng bÃ¡o account change tháº¥t báº¡i: {}", ex.getMessage(), ex);
                    }
                } else {
//                    log.info("â„¹ï¸ Account khÃ´ng thay Ä‘á»•i");
                }
            } catch (Exception e) {
                log.error("âŒ Lá»—i khi check lá»‹ch sá»­ account: {}", e.getMessage(), e);
            }

            // ============ CHá»¨C NÄ‚NG 2: CHECK STATUS (Ä‘á»™c láº­p) ============
            try {
//                log.info("ðŸ” [CHá»¨C NÄ‚NG 2] KIá»‚M TRA STATUS");
                Integer status = extractStatus(root);

                if (status == null) {
//                    log.info("â­ï¸ Status khÃ´ng tÃ¬m tháº¥y, bá» qua xá»­ lÃ½ status");
                } else if (!webhookConfigService.shouldProcess(status)) {
//                    log.info("â­ï¸ Status {} khÃ´ng Ä‘Æ°á»£c báº­t trong config, bá» qua", status);
                } else {
//                    log.info("âœ… Status {} Ä‘Æ°á»£c báº­t, xá»­ lÃ½...", status);
                    processStatusLogic(status, root, orderWebhook);
                }
            } catch (Exception e) {
                log.error("âŒ Lá»—i khi xá»­ lÃ½ status: {}", e.getMessage(), e);
            }

            // ============ CHá»¨C NÄ‚NG 3: CHECK TAGS (Ä‘á»™c láº­p) ============
            try {
//                log.info("ðŸ” [CHá»¨C NÄ‚NG 3] KIá»‚M TRA TAGS");
                boolean hasDongBoDataTag = hasDongBoDataTag(root);
                if (hasDongBoDataTag) {
//                    log.info("âœ… CÃ³ tag 'Äá»“ng bá»™ DATA' - táº¡o báº£n ghi");
                    if (autoCreateRecord) {
                        createBitableRecord(root);
                    }
                } else {
//                    log.info("â„¹ï¸ KhÃ´ng cÃ³ tag 'Äá»“ng bá»™ DATA'");
                }
            } catch (Exception e) {
                log.error("âŒ Lá»—i khi xá»­ lÃ½ tags: {}", e.getMessage(), e);
            }

            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("âŒ Failed to parse webhook JSON: {}", e.getMessage());
            return ResponseEntity.badRequest().body("bad request");
        }
    }

    /**
     * TrÃ­ch xuáº¥t status tá»« webhook
     */
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
     * Xá»­ lÃ½ logic theo status
     */
    private void processStatusLogic(Integer status, JsonNode webhookData, PosOrderWebhook orderWebhook) throws Exception {
        if (status == 1) {
            log.info("ðŸ“‹ Status = 1: Táº¡o báº£n ghi má»›i");
            if (autoCreateRecord) {
                createBitableRecord(webhookData);
            }
        } else if (status == 6) {
            log.info("ðŸ“‹ Status = 6: XÃ³a báº£n ghi");

            // Luá»“ng 1: XÃ³a báº£n ghi vÃ  lÆ°u vÃ o báº£ng Ä‘Ã£ xÃ³a
            CompletableFuture<Void> deleteFuture = CompletableFuture.runAsync(() -> {
                try {
                    processStatus6Delete(webhookData);
                } catch (Exception e) {
                    log.error("âŒ Failed to process status 6 delete: {}", e.getMessage(), e);
                }
            }, executorService);

            // Luá»“ng 2: TÃ¬m vÃ  update "Tráº¡ng thÃ¡i mess" = "Há»§y" trong cÃ¡c table sale
            CompletableFuture<Void> updateFuture = CompletableFuture.runAsync(() -> {
                try {
                    processStatus6UpdateSalesTables(webhookData);
                } catch (Exception e) {
                    log.error("âŒ Failed to process status 6 update sales tables: {}", e.getMessage(), e);
                }
            }, executorService);

            // Äá»£i cáº£ 2 luá»“ng hoÃ n thÃ nh (khÃ´ng block response)
            CompletableFuture.allOf(deleteFuture, updateFuture).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("âŒ Error in status 6 parallel processing: {}", throwable.getMessage());
                } else {
                    log.info("âœ… Status 6 parallel processing completed");
                }
            });
        }
    }

    /**
     * Kiá»ƒm tra xem webhook cÃ³ tag "Äá»“ng bá»™ DATA" (id = 32) khÃ´ng
     */
    private void logLatestAccountHistory(PosOrderWebhook orderWebhook) {
        if (orderWebhook.getHistories() != null && !orderWebhook.getHistories().isEmpty()) {
            log.info("   Total histories: {}", orderWebhook.getHistories().size());
            PosOrderWebhook.HistoryItem latestHistory = orderWebhook.getLatestHistory();
            if (latestHistory != null) {
//                log.info("   Latest history updated_at: {}", latestHistory.getUpdatedAt());
//                log.info("   Latest history fields:");
//                log.info("     - account: {}", latestHistory.getAccount());
//                log.info("     - status: {}", latestHistory.getStatus());
//                log.info("     - orderSources: {}", latestHistory.getOrderSources());
//                log.info("     - pageId: {}", latestHistory.getPageId());
//                log.info("     - assigningCareId: {}", latestHistory.getAssigningCareId());
//                log.info("     - editorId: {}", latestHistory.getEditorId());

                if (latestHistory.getAccount() != null) {
                    Object oldVal = latestHistory.getAccount().getOldValue();
                    Object newVal = latestHistory.getAccount().getNewValue();
                    boolean changed = orderWebhook.hasAccountChanged();
//                    log.info("   Account field present (changed? {})", changed);
//                    log.info("     Old: {}", oldVal);
//                    log.info("     New: {}", newVal);
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
        // TÃ¬m tags á»Ÿ root level
        JsonNode tagsNode = root.get("tags");
        if (tagsNode == null || !tagsNode.isArray()) {
            // Thá»­ trong data wrapper
            if (root.has("data")) {
                JsonNode dataNode = root.get("data");
                tagsNode = dataNode.get("tags");
            }
        }

        if (tagsNode == null || !tagsNode.isArray()) {
            return false;
        }

        // Kiá»ƒm tra xem cÃ³ tag nÃ o cÃ³ id = 32 hoáº·c name = "Äá»“ng bá»™ DATA" khÃ´ng
        for (JsonNode tagNode : tagsNode) {
            JsonNode idNode = tagNode.get("id");
            if (idNode != null && idNode.isNumber() && idNode.asInt() == 32) {
                return true;
            }
            JsonNode nameNode = tagNode.get("name");
            if (nameNode != null && "Äá»“ng bá»™ DATA".equals(nameNode.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gá»­i thÃ´ng bÃ¡o khi account thay Ä‘á»•i
     */
    private void sendAccountChangeNotification(PosOrderWebhook orderWebhook) throws Exception {
        String orderId = orderWebhook.getId() != null ? orderWebhook.getId().toString() : "unknown";
        String oldAccount = orderWebhook.getOldAccountValue();
        String newAccount = orderWebhook.getNewAccountValue();

        log.info("ðŸ“¢ Sending account change notification:");
        log.info("   Order ID: {}", orderId);
        log.info("   Old Account: {}", oldAccount);
        log.info("   New Account: {}", newAccount);

        // Láº¥y tenant token (Æ°u tiÃªn tá»« tenantTokenService, fallback sang storage)
        String tenantToken = null;
        if (tenantTokenService != null) {
            tenantToken = tenantTokenService.getTenantAccessToken();
        }
        if ((tenantToken == null || tenantToken.isBlank()) && tokenStorageServiceForTenant != null) {
            tenantToken = tokenStorageServiceForTenant.getTenantAccessToken();
        }

        if (tenantToken == null || tenantToken.isBlank()) {
            log.error("âŒ Tenant token is not available, cannot send message");
            return;
        }

        if (restTemplate == null) {
            log.error("âŒ RestTemplate is not available, cannot send message");
            return;
        }

        // Táº¡o message content
        String messageContent = String.format(
            "ÄÆ¡n hÃ ng Ä‘Ã£ thay Ä‘á»•i nguá»“n Ä‘Æ¡n: %s",
            orderId
        );

        log.info("   Message content: {}", messageContent);

        // Gá»­i message tá»›i user máº·c Ä‘á»‹nh
        String defaultUserId = null;
        if (larkBaseProperties != null) {
            defaultUserId = larkBaseProperties.getDefaultUserId();
        }
        if (defaultUserId == null || defaultUserId.isBlank()) {
            // fallback cá»‘ Ä‘á»‹nh náº¿u config khÃ´ng cung cáº¥p
            defaultUserId = "ou_4cf48041bec4170651def0c025217097";
            log.warn("âš ï¸ defaultUserId khÃ´ng Ä‘Æ°á»£c cáº¥u hÃ¬nh, dÃ¹ng giÃ¡ trá»‹ máº·c Ä‘á»‹nh {}", defaultUserId);
        }
        log.info("   Sending to user ID: {}", defaultUserId);

        try {
            sendLarkMessage(tenantToken, defaultUserId, messageContent);
            log.info("âœ… Account change notification sent successfully");
        } catch (Exception e) {
            log.error("âŒ Failed to send account change notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Gá»­i message tá»›i Lark IM
     */
    private void sendLarkMessage(String accessToken, String receiveId, String messageContent) throws Exception {
        String url = "https://open.larksuite.com/open-apis/im/v1/messages?receive_id_type=open_id";

        log.info("ðŸ“¤ Calling Lark IM API:");
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

            log.debug("âœ… Message sent to Lark IM: {}", response.getBody());
        } catch (Exception e) {
            log.error("âŒ Failed to send message to Lark IM: {}", e.getMessage());
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
        log.info(">>> TEST WEBHOOK - /api/lark/test");
        log.info(">>> Headers: {}", allHeaders);
        
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

        // User token info
        String userToken = getUserAccessToken();
        health.put("hasUserToken", userToken != null);

        // Tenant token info tá»« storage (Ä‘Æ°á»£c lÆ°u khi user login)
        if (tokenStorageServiceForTenant != null) {
            String tenantToken = tokenStorageServiceForTenant.getTenantAccessToken();
            health.put("hasTenantTokenInStorage", tenantToken != null);
            health.put("tenantTokenValidInStorage", tokenStorageServiceForTenant.isTenantTokenValid());
            health.put("tenantTokenRemainingSecondsInStorage", tokenStorageServiceForTenant.getTenantTokenRemainingSeconds());
        }

        // Tenant token info tá»« API (fallback)
        if (tenantTokenService != null) {
            try {
                String tenantToken = tenantTokenService.getTenantAccessToken();
                health.put("hasTenantTokenFromAPI", tenantToken != null);
                health.put("tenantTokenValidFromAPI", tenantTokenService.isTokenValid());
                health.put("tenantTokenRemainingSecondsFromAPI", tenantTokenService.getTokenRemainingSeconds());
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
            response.put("remainingSeconds", tenantTokenService.getTokenRemainingSeconds());
            response.put("remainingMinutes", tenantTokenService.getTokenRemainingSeconds() / 60);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            log.error("âŒ Failed to get tenant token: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    private void createBitableRecord(JsonNode webhookData) throws Exception {
        log.info("ðŸ”„ Creating Bitable record from webhook data...");
        
        PosOrderWebhook orderWebhook;
        try {
            orderWebhook = mapper.treeToValue(webhookData, PosOrderWebhook.class);
            
            // ============== LOG DATA Tá»ª POS ==============
            log.info("");
            log.info("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
            log.info("â•‘                    DATA NHáº¬N ÄÆ¯á»¢C Tá»ª POS (PANCAKE)                       â•‘");
            log.info("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
            
            log.info("â”Œâ”€â”€â”€ THÃ”NG TIN CÆ  Báº¢N â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”");
            log.info("â”‚ id: {}", orderWebhook.getId());
            log.info("â”‚ type: {}", orderWebhook.getType());
            log.info("â”‚ event_type: {}", orderWebhook.getEventType());
            log.info("â”‚ status: {}", orderWebhook.getStatus());

            // Log tags náº¿u cÃ³
            if (orderWebhook.getTags() != null && !orderWebhook.getTags().isEmpty()) {
                log.info("â”‚ tags: {}", orderWebhook.getTags());
                log.info("â”‚ hasDongBoDataTag: {}", orderWebhook.hasDongBoDataTag());
            }
            log.info("â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜");
            
            log.info("â”Œâ”€â”€â”€ SHIPPING ADDRESS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”");
            if (orderWebhook.getShippingAddress() != null) {
                PosOrderWebhook.ShippingAddress addr = orderWebhook.getShippingAddress();
                log.info("â”‚ full_name: {}", addr.getFullName());
                log.info("â”‚ phone_number: {}", addr.getPhoneNumber());
                log.info("â”‚ full_address: {}", addr.getFullAddress());
                log.info("â”‚ address: {}", addr.getAddress());
                log.info("â”‚ province_name: {}", addr.getProvinceName());
            } else {
                log.info("â”‚ (khÃ´ng cÃ³ shipping_address á»Ÿ root level)");
            }
            log.info("â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜");
            
            log.info("â”Œâ”€â”€â”€ CUSTOMER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”");
            if (orderWebhook.getCustomer() != null) {
                PosOrderWebhook.CustomerInfo cust = orderWebhook.getCustomer();
                log.info("â”‚ name: {}", cust.getName());
                log.info("â”‚ full_name: {}", cust.getFullName());
                log.info("â”‚ phone_number: {}", cust.getPhoneNumber());
                log.info("â”‚ phone_numbers: {}", cust.getPhoneNumbers());
                log.info("â”‚ full_address: {}", cust.getFullAddress());
                log.info("â”‚ Province_name: {}", cust.getProvinceName());
                log.info("â”‚ conversation_link: {}", cust.getConversationLink());
            } else {
                log.info("â”‚ (khÃ´ng cÃ³ customer á»Ÿ root level)");
            }
            log.info("â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜");
            
            log.info("â”Œâ”€â”€â”€ ASSIGNING SELLER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”");
            if (orderWebhook.getAssigningSeller() != null) {
                log.info("â”‚ name: {}", orderWebhook.getAssigningSeller().getName());
            } else {
                log.info("â”‚ (null)");
            }
            log.info("â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜");
            
            log.info("â”Œâ”€â”€â”€ ASSIGNING CARE (CSKH) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”");
            String assigningCareName = null;
            if (orderWebhook.getAssigningCare() != null) {
                assigningCareName = orderWebhook.getAssigningCare().getName();
                log.info("â”‚ name: {}", assigningCareName);
            } else if (orderWebhook.getData() != null && orderWebhook.getData().getAssigningCare() != null) {
                assigningCareName = orderWebhook.getData().getAssigningCare().getName();
                log.info("â”‚ name (from data): {}", assigningCareName);
            } else {
                log.info("â”‚ (null - khÃ´ng cÃ³ CSKH)");
            }
            log.info("â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜");
            
            // TÃ¬m vÃ  log Base ID tá»« tÃªn assigning care
            log.info("â”Œâ”€â”€â”€ BASE ID MAPPING â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”");
            String baseId = posToBitableMapper.findBaseIdFromWebhook(orderWebhook);
            if (assigningCareName != null && !assigningCareName.isBlank()) {
                log.info("â”‚ Assigning Care Name: {}", assigningCareName);
                if (baseId != null && !baseId.isBlank()) {
                    log.info("â”‚ Base ID: {}", baseId);
                    log.info("â”‚ âœ… ÄÃ£ tÃ¬m tháº¥y Base ID trong mapping");
                } else {
                    log.info("â”‚ Base ID: (khÃ´ng tÃ¬m tháº¥y trong mapping)");
                    log.info("â”‚ âš ï¸ Sáº½ sá»­ dá»¥ng Base ID máº·c Ä‘á»‹nh: {}", defaultAppToken);
                }
            } else {
                log.info("â”‚ Assigning Care Name: (null - khÃ´ng cÃ³ tÃªn Ä‘á»ƒ map)");
                log.info("â”‚ Base ID: (khÃ´ng thá»ƒ map - thiáº¿u tÃªn assigning care)");
                log.info("â”‚ âš ï¸ Sáº½ sá»­ dá»¥ng Base ID máº·c Ä‘á»‹nh: {}", defaultAppToken);
            }
            log.info("â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜");
            
            log.info("â”Œâ”€â”€â”€ HISTORIES â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”");
            if (orderWebhook.getHistories() != null && !orderWebhook.getHistories().isEmpty()) {
                log.info("â”‚ count: {}", orderWebhook.getHistories().size());
                for (int i = 0; i < Math.min(3, orderWebhook.getHistories().size()); i++) {
                    PosOrderWebhook.HistoryItem h = orderWebhook.getHistories().get(i);
                    if (h.getShippingAddress() != null && h.getShippingAddress().getNewAddress() != null) {
                        log.info("â”‚ histories[{}].shipping_address.new.full_name: {}", i, 
                                h.getShippingAddress().getNewAddress().getFullName());
                    }
                }
            } else {
                log.info("â”‚ (khÃ´ng cÃ³ histories)");
            }
            log.info("â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜");
            
            log.info("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
            log.info("");
            
        } catch (Exception e) {
            log.error("âŒ Failed to parse webhook data: {}", e.getMessage());
            throw new RuntimeException("Failed to parse webhook data: " + e.getMessage(), e);
        }
        
        // Láº¥y tÃªn CSKH tá»« webhook vÃ  trim() Ä‘á»ƒ loáº¡i bá» khoáº£ng tráº¯ng thá»«a
        PosOrderWebhook.AssigningSeller cskh = posToBitableMapper.getAssigningCare(orderWebhook);
        String cskhName = (cskh != null && cskh.getName() != null) ? cskh.getName().trim() : null;
        
        log.info("ðŸ” DEBUG - CSKH Mapping:");
        log.info("   CSKH Name from webhook (raw): '{}'", cskh != null && cskh.getName() != null ? cskh.getName() : "(null)");
        log.info("   CSKH Name from webhook (trimmed): '{}'", cskhName != null ? cskhName : "(null)");
        if (cskhName != null && cskh != null && cskh.getName() != null) {
            log.info("   CSKH Name length (raw): {}, (trimmed): {}", 
                    cskh.getName().length(), cskhName.length());
        }
        log.info("   BaseTableMappingService available: {}", baseTableMappingService != null);
        if (baseTableMappingService != null) {
            log.info("   Total mappings loaded: {}", baseTableMappingService.getAllMappings().size());
            if (baseTableMappingService.getAllMappings().size() > 0) {
                log.info("   Sample mappings:");
                baseTableMappingService.getAllMappings().stream().limit(5).forEach(m -> 
                    log.info("     - Base Name: '{}' -> Base ID: {}, Table ID: {}", 
                            m.getBaseName(), m.getBaseId(), m.getTableId()));
            }
        }
        
        // TÃ¬m Base ID vÃ  Table ID dá»±a trÃªn tÃªn CSKH tá»« baseTableMappingService
        String appToken = defaultAppToken;
        String targetTableId = tableId;

        log.info("ðŸ” DEBUG - Base/Table Selection:");
        log.info("   CSKH Name: {}", cskhName);
        log.info("   Default Base ID (from config): {}", defaultAppToken);
        log.info("   Default Table ID (from config): {}", tableId);

        if (cskhName != null && !cskhName.isBlank() && baseTableMappingService != null) {
            java.util.Optional<BaseTableMapping> mapping = baseTableMappingService.findMappingByBaseName(cskhName);
            if (mapping.isPresent()) {
                appToken = mapping.get().getBaseId();
                if (mapping.get().getTableId() != null && !mapping.get().getTableId().isBlank()) {
                    targetTableId = mapping.get().getTableId();
                }
                log.info("âœ… Found mapping for CSKH '{}': Base ID={}, Table ID={}",
                        cskhName, appToken, targetTableId);
            } else {
                log.warn("âš ï¸ No mapping found for CSKH name '{}' in BaseTableMappingService, using default", cskhName);
            }
        } else {
            if (cskhName == null || cskhName.isBlank()) {
                log.warn("âš ï¸ CSKH name is null or blank, using default Base/Table ID");
            }
            if (baseTableMappingService == null) {
                log.warn("âš ï¸ BaseTableMappingService is not available, using default Base/Table ID");
            }
        }
        
        log.info("ðŸ” DEBUG - Final Selection:");
        log.info("   Selected Base ID: {}", appToken);
        log.info("   Selected Table ID: {}", targetTableId);
        
        // Map sang Bitable fields
        Map<String, Object> fields;
        try {
            fields = posToBitableMapper.mapToBitableFields(orderWebhook);
        } catch (Exception e) {
            log.error("âŒ Failed to map fields: {}", e.getMessage());
            throw new RuntimeException("Failed to map fields: " + e.getMessage(), e);
        }
        
        // Log data sáº½ Ä‘Æ°á»£c ghi vÃ o Bitable
        String fieldsJson = prettyWriter.writeValueAsString(fields);
        log.info("ðŸ“¤ BITABLE RECORD DATA:");
        log.info("   CSKH Name: {}", cskhName != null ? cskhName : "(null)");
        log.info("   Base ID: {}", appToken);
        log.info("   Table ID: {}", targetTableId);
        log.info("   Fields:\n{}", fieldsJson);
        
        // Láº¥y token má»›i nháº¥t ngay trÆ°á»›c khi gá»i API Ä‘á»ƒ trÃ¡nh race condition vá»›i scheduler
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalStateException("User access token is not available. Please login at /token first.");
        }
        
        // Kiá»ƒm tra sá»‘ Ä‘iá»‡n thoáº¡i Ä‘Ã£ tá»“n táº¡i chÆ°a trÆ°á»›c khi táº¡o báº£n ghi
        String phoneNumber = posToBitableMapper.getDienThoai(orderWebhook);
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            log.info("ðŸ” Checking if phone number already exists: {}", phoneNumber);
            try {
                // View ID máº·c Ä‘á»‹nh (cÃ³ thá»ƒ config sau)
                String viewId = "vew5Ou4Kee"; // CÃ³ thá»ƒ láº¥y tá»« config
                boolean phoneExists = bitableService.checkPhoneExists(
                        appToken, 
                        targetTableId, 
                        userAccessToken, 
                        phoneNumber,
                        viewId
                );
                
                if (phoneExists) {
                    log.warn("âš ï¸ Phone number '{}' already exists in table. Skipping record creation.", phoneNumber);
                    // KhÃ´ng throw exception, chá»‰ log vÃ  return Ä‘á»ƒ khÃ´ng block flow
                    return;
                } else {
                    log.info("âœ… Phone number '{}' does not exist. Proceeding with record creation.", phoneNumber);
                }
            } catch (Exception e) {
                log.error("âŒ Error checking phone existence: {}. Proceeding with record creation.", e.getMessage());
                // Náº¿u cÃ³ lá»—i khi check, váº«n tiáº¿p tá»¥c táº¡o báº£n ghi Ä‘á»ƒ trÃ¡nh block flow
            }
        } else {
            log.warn("âš ï¸ Phone number is null or blank. Cannot check for duplicates. Proceeding with record creation.");
        }
        
        // Táº¡o record
        BitableRecordResponse response;
        try {
            response = bitableService.createRecord(appToken, targetTableId, userAccessToken, fields);
        } catch (Exception e) {
            log.error("âŒ Failed to call Bitable API: {}", e.getMessage());
            throw new RuntimeException("Failed to create record: " + e.getMessage(), e);
        }
        
        if (response.isSuccess() && response.getData() != null) {
            String recordId = response.getData().getRecord() != null 
                    ? response.getData().getRecord().getRecordId() 
                    : "unknown";
            log.info("âœ… Created Bitable record: recordId={}", recordId);

            // Kiá»ƒm tra xem account cÃ³ thay Ä‘á»•i khÃ´ng, náº¿u cÃ³ thÃ¬ gá»­i thÃ´ng bÃ¡o
            try {
                log.info("ðŸ” Checking if account changed...");
                log.info("   hasAccountChanged: {}", orderWebhook.hasAccountChanged());

                // always dump the latest history and account values for debugging
                logLatestAccountHistory(orderWebhook);


                if (orderWebhook.hasAccountChanged()) {
                    log.info("ðŸ“¢ Account Ä‘Ã£ thay Ä‘á»•i, sáº½ gá»­i thÃ´ng bÃ¡o trong bÆ°á»›c Ä‘áº§u (bá» qua á»Ÿ Ä‘Ã¢y)");
                } else {
                    log.info("â„¹ï¸ Account khÃ´ng thay Ä‘á»•i, bá» qua gá»­i thÃ´ng bÃ¡o");
                }
            } catch (Exception e) {
                log.error("âŒ Failed to send account change notification: {}", e.getMessage(), e);
                // KhÃ´ng throw exception Ä‘á»ƒ khÃ´ng lÃ m giÃ¡n Ä‘oáº¡n flow chÃ­nh
            }

            // Gá»­i tin nháº¯n cho CSKH sau khi táº¡o record thÃ nh cÃ´ng
            try {
                PosOrderWebhook.AssigningSeller cskhForIm = posToBitableMapper.getAssigningCare(orderWebhook);
                String cskhOpenIdRaw = (cskhForIm != null) ? cskhForIm.getId() : null;
                String cskhPhoneNumber = posToBitableMapper.getCskhPhoneNumber(orderWebhook);
                
                log.info("ðŸ” Resolving CSKH Open ID:");
                log.info("   CSKH Name: {}", cskhName);
                log.info("   CSKH Phone Number: {}", cskhPhoneNumber);
                log.info("   CSKH Open ID from webhook (cskhForIm.getId()): {}", cskhOpenIdRaw);
                
                // Láº¥y cskhOpenId tá»« API find by department báº±ng sá»‘ Ä‘iá»‡n thoáº¡i
                // receive_id pháº£i lÃ  open_id tá»« API find_by_department, khÃ´ng pháº£i tá»« webhook
                String cskhOpenId = null;
                if (larkSendMessage != null && cskhPhoneNumber != null && !cskhPhoneNumber.isBlank()) {
                    cskhOpenId = larkSendMessage.resolveCskhOpenIdByPhone(
                            "975gbb119944129g", // department_id phÃ²ng CSKH
                            cskhPhoneNumber,
                            cskhOpenIdRaw // Náº¿u Ä‘Ã£ cÃ³ open_id tá»« webhook thÃ¬ dÃ¹ng luÃ´n, khÃ´ng cáº§n gá»i API
                    );
                    log.info("   Resolved CSKH Open ID from API: {}", cskhOpenId);
                } else {
                    // Fallback: chá»‰ dÃ¹ng openId tá»« webhook náº¿u khÃ´ng cÃ³ sá»‘ Ä‘iá»‡n thoáº¡i
                    cskhOpenId = cskhOpenIdRaw;
                    if (cskhPhoneNumber == null || cskhPhoneNumber.isBlank()) {
                        log.warn("âš ï¸ CSKH phone number is null or blank, cannot resolve by phone, using webhook ID: {}", cskhOpenIdRaw);
                    }
                }
                
                if (cskhOpenId != null && !cskhOpenId.isBlank() && larkImService != null) {
                    String tenKhach = posToBitableMapper.getTenKhach(orderWebhook);
                    String dienThoai = posToBitableMapper.getDienThoai(orderWebhook);
                    String diaChi = posToBitableMapper.getDiaChi(orderWebhook);
                    Integer tuoi = posToBitableMapper.getTuoi(orderWebhook);
                    
                    log.info("ðŸ“¤ Sending IM to CSKH:");
                    log.info("   Receive ID (open_id): {}", cskhOpenId);
                    log.info("   CSKH Name: {}", cskhName);
                    
                    larkImService.sendToCskh(cskhOpenId, tenKhach, dienThoai, diaChi, tuoi);
                    log.info("âœ… Sent IM notification to CSKH: {} (name: {})", cskhOpenId, cskhName);
                } else {
                    if (cskhOpenId == null || cskhOpenId.isBlank()) {
                        log.warn("âš ï¸ CSKH Open ID is null or blank, cannot send IM notification (CSKH name: {})", cskhName);
                    }
                    if (larkImService == null) {
                        log.warn("âš ï¸ LarkImService is not available, cannot send IM notification");
                    }
                    if (larkSendMessage == null) {
                        log.warn("âš ï¸ LarkSendMessage is not available, cannot resolve CSKH Open ID");
                    }
                }
            } catch (Exception e) {
                log.error("âŒ Failed to send IM notification to CSKH: {}", e.getMessage(), e);
                // KhÃ´ng throw exception Ä‘á»ƒ khÃ´ng lÃ m giÃ¡n Ä‘oáº¡n flow chÃ­nh
            }
        } else {
            throw new RuntimeException(String.format("Bitable error: code=%d, msg=%s", 
                    response.getCode(), response.getMsg()));
        }
    }
    
    /**
     * Xá»­ lÃ½ webhook status = 6: XÃ³a báº£n ghi vÃ  lÆ°u vÃ o báº£ng Ä‘Ã£ xÃ³a
     */
    private void processStatus6Delete(JsonNode webhookData) throws Exception {
        log.info("ðŸ”„ Processing status = 6: Delete record and save to deleted table...");
        
        PosOrderWebhook orderWebhook;
        try {
            orderWebhook = mapper.treeToValue(webhookData, PosOrderWebhook.class);
        } catch (Exception e) {
            log.error("âŒ Failed to parse webhook data: {}", e.getMessage());
            throw new RuntimeException("Failed to parse webhook data: " + e.getMessage(), e);
        }
        
        // Kiá»ƒm tra order_sources_name: chá»‰ xÃ³a khi = "Facebook"
        String orderSourcesName = orderWebhook.getOrderSourcesName();
        log.info("ðŸ” Checking order_sources_name: {}", orderSourcesName);
        
        if (orderSourcesName == null || !orderSourcesName.equals("Facebook")) {
            log.info("â­ï¸ Skipping delete operation. order_sources_name = '{}' (only delete when = 'Facebook')", 
                    orderSourcesName != null ? orderSourcesName : "null");
            return;
        }
        
        log.info("âœ… order_sources_name = 'Facebook', proceeding with delete operation...");
        
        // Láº¥y base ID vÃ  table ID tá»« webhook dá»±a trÃªn tÃªn CSKH
        String appToken = defaultAppToken;
        String targetTableId = tableId;

        PosOrderWebhook.AssigningSeller cskh = posToBitableMapper.getAssigningCare(orderWebhook);
        String cskhName = (cskh != null && cskh.getName() != null) ? cskh.getName().trim() : null;

        log.info("ðŸ” DEBUG - Base/Table Selection for Status 6:");
        log.info("   CSKH Name: {}", cskhName);
        log.info("   Default Base ID: {}", defaultAppToken);
        log.info("   Default Table ID: {}", tableId);

        if (cskhName != null && !cskhName.isBlank() && baseTableMappingService != null) {
            java.util.Optional<BaseTableMapping> mapping = baseTableMappingService.findMappingByBaseName(cskhName);
            if (mapping.isPresent()) {
                appToken = mapping.get().getBaseId();
                if (mapping.get().getTableId() != null && !mapping.get().getTableId().isBlank()) {
                    targetTableId = mapping.get().getTableId();
                }
                log.info("âœ… Found mapping for CSKH '{}': Base ID={}, Table ID={}",
                        cskhName, appToken, targetTableId);
            } else {
                log.warn("âš ï¸ No mapping found for CSKH name '{}', using default", cskhName);
            }
        } else {
            if (cskhName == null || cskhName.isBlank()) {
                log.warn("âš ï¸ CSKH name is null or blank, using default Base/Table ID");
            }
            if (baseTableMappingService == null) {
                log.warn("âš ï¸ BaseTableMappingService is not available, using default Base/Table ID");
            }
        }
        
        // Láº¥y sá»‘ Ä‘iá»‡n thoáº¡i cá»§a khÃ¡ch hÃ ng
        String phoneNumber = posToBitableMapper.getDienThoai(orderWebhook);
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.error("âŒ Phone number is null or blank, cannot process status 6");
            return;
        }
        
        log.info("ðŸ“ž Phone number to search: {}", phoneNumber);
        
        // Láº¥y token
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalStateException("User access token is not available. Please login at /token first.");
        }
        
        // TÃ¬m record ID báº±ng sá»‘ Ä‘iá»‡n thoáº¡i
        String viewId = "vew5Ou4Kee"; // View ID máº·c Ä‘á»‹nh
        String recordId = bitableService.findRecordIdByPhone(
                appToken, 
                targetTableId, 
                userAccessToken, 
                phoneNumber,
                viewId
        );
        
        if (recordId == null || recordId.isBlank()) {
            log.error("âŒ Cannot find record ID for phone number: {}. Skipping delete operation.", phoneNumber);
            return;
        }
        
        log.info("âœ… Found record ID: {} for phone number: {}", recordId, phoneNumber);
        
        // Táº¡o báº£n ghi má»›i vÃ o base/table cá»‘ Ä‘á»‹nh Ä‘á»ƒ lÆ°u láº¡i khÃ¡ch hÃ ng Ä‘Ã£ xÃ³a
        String deletedBaseId = "WA2wblztIaFfZ8sRG8ElIOJdgXD";
        String deletedTableId = "tblkKixkzwtyzUMP";
        
        log.info("ðŸ’¾ Saving deleted record to backup table: Base ID={}, Table ID={}", 
                deletedBaseId, deletedTableId);
        
        try {
            // Map sang Bitable fields (sá»­ dá»¥ng láº¡i code cá»§a status = 1)
            Map<String, Object> fields = posToBitableMapper.mapToBitableFields(orderWebhook);
            
            // Táº¡o báº£n ghi vÃ o báº£ng Ä‘Ã£ xÃ³a
            BitableRecordResponse backupResponse = bitableService.createRecord(
                    deletedBaseId, 
                    deletedTableId, 
                    userAccessToken, 
                    fields
            );
            
            if (backupResponse.isSuccess() && backupResponse.getData() != null) {
                String backupRecordId = backupResponse.getData().getRecord() != null 
                        ? backupResponse.getData().getRecord().getRecordId() 
                        : "unknown";
                log.info("âœ… Saved deleted record to backup table: recordId={}", backupRecordId);
            } else {
                log.warn("âš ï¸ Failed to save backup record, but continuing with delete operation");
            }
        } catch (Exception e) {
            log.error("âŒ Failed to save backup record: {}", e.getMessage(), e);
            // Váº«n tiáº¿p tá»¥c xÃ³a record dÃ¹ cÃ³ lá»—i khi backup
        }
        
        // XÃ³a record cÅ©
        log.info("ðŸ—‘ï¸ Deleting record: Base ID={}, Table ID={}, Record ID={}", 
                appToken, targetTableId, recordId);
        
        try {
            bitableService.deleteRecord(appToken, targetTableId, recordId, userAccessToken);
            log.info("âœ… Successfully deleted record: recordId={}", recordId);
        } catch (Exception e) {
            log.error("âŒ Failed to delete record: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete record: " + e.getMessage(), e);
        }
    }
    
    /**
     * Xá»­ lÃ½ status = 6: TÃ¬m vÃ  update "Tráº¡ng thÃ¡i mess" = "Há»§y" trong cÃ¡c table sale
     * Cháº¡y song song vá»›i luá»“ng xÃ³a báº£n ghi
     */
    private void processStatus6UpdateSalesTables(JsonNode webhookData) throws Exception {
        log.info("ðŸ”„ Processing status = 6: Update sales tables...");
        
        // Láº¥y sá»‘ Ä‘iá»‡n thoáº¡i cá»§a khÃ¡ch hÃ ng
        PosOrderWebhook orderWebhook;
        try {
            orderWebhook = mapper.treeToValue(webhookData, PosOrderWebhook.class);
        } catch (Exception e) {
            log.error("âŒ Failed to parse webhook data for sales update: {}", e.getMessage());
            return;
        }
        
        String phoneNumber = posToBitableMapper.getDienThoai(orderWebhook);
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("âš ï¸ Phone number is null or blank, cannot update sales tables");
            return;
        }
        
        log.info("ðŸ“ž Searching for phone number in sales tables: {}", phoneNumber);
        
        // Láº¥y danh sÃ¡ch table sale
        if (salesTablesConfig == null) {
            log.warn("âš ï¸ SalesTablesConfig is not available, cannot update sales tables");
            return;
        }
        
        // Láº¥y token trÆ°á»›c Ä‘á»ƒ dÃ¹ng cho cáº£ refresh tables vÃ  search
        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            log.error("âŒ User access token is not available");
            return;
        }
        
        // Base ID cá»‘ Ä‘á»‹nh cho sales tables
        String salesBaseId = "VsLjbnWlfapGXhszsvqlRm6QgIf";
        
        List<SalesTablesConfig.SalesTable> salesTables = salesTablesConfig.getTables();
        if (salesTables == null || salesTables.isEmpty()) {
            log.warn("âš ï¸ No sales tables found, refreshing with token...");
            // Refresh vá»›i token vÃ  base ID Ä‘Ãºng
            salesTablesConfig.refreshTables(userAccessToken, salesBaseId);
            salesTables = salesTablesConfig.getTables();
            if (salesTables == null || salesTables.isEmpty()) {
                log.error("âŒ Still no sales tables found after refresh");
                return;
            }
        }
        
        log.info("ðŸ” Searching in {} sales tables", salesTables.size());
        String viewId = "vewE3Ope6x";
        
        // Chia danh sÃ¡ch table thÃ nh 5 pháº§n Ä‘á»ƒ xá»­ lÃ½ song song
        int totalTables = salesTables.size();
        int chunkSize = Math.max(1, (totalTables + 4) / 5); // Chia thÃ nh 5 chunks, lÃ m trÃ²n lÃªn
        
        List<CompletableFuture<SearchResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            int startIdx = i * chunkSize;
            int endIdx = Math.min(startIdx + chunkSize, totalTables);
            
            if (startIdx >= totalTables) {
                break; // KhÃ´ng cÃ²n table nÃ o Ä‘á»ƒ xá»­ lÃ½
            }
            
            final int chunkIndex = i;
            final int start = startIdx;
            final int end = endIdx;
            
            List<SalesTablesConfig.SalesTable> chunk = salesTables.subList(start, end);
            
            CompletableFuture<SearchResult> future = CompletableFuture.supplyAsync(() -> {
                log.info("ðŸ” Chunk {}: Searching in {} tables (index {} to {})", 
                        chunkIndex + 1, chunk.size(), start, end - 1);
                
                for (SalesTablesConfig.SalesTable table : chunk) {
                    try {
                        String recordId = bitableService.findRecordIdByPhoneInTable(
                                salesBaseId,
                                table.getTableId(),
                                userAccessToken,
                                phoneNumber,
                                viewId
                        );
                        
                        if (recordId != null && !recordId.isBlank()) {
                            log.info("âœ… Found record ID: {} in table {} ({})", 
                                    recordId, table.getTableId(), table.getDisplayName());
                            return new SearchResult(table.getTableId(), recordId, table.getDisplayName());
                        }
                    } catch (Exception e) {
                        log.debug("Error searching in table {}: {}", table.getTableId(), e.getMessage());
                    }
                }
                
                return null; // KhÃ´ng tÃ¬m tháº¥y trong chunk nÃ y
            }, executorService);
            
            futures.add(future);
        }
        
        // Äá»£i táº¥t cáº£ cÃ¡c chunk hoÃ n thÃ nh vÃ  tÃ¬m káº¿t quáº£ Ä‘áº§u tiÃªn
        SearchResult foundResult = null;
        for (CompletableFuture<SearchResult> future : futures) {
            try {
                SearchResult result = future.get(); // Äá»£i chunk nÃ y hoÃ n thÃ nh
                if (result != null && foundResult == null) {
                    foundResult = result;
                    // Há»§y cÃ¡c future cÃ²n láº¡i Ä‘á»ƒ tiáº¿t kiá»‡m tÃ i nguyÃªn (optional)
                    break;
                }
            } catch (Exception e) {
                log.error("âŒ Error waiting for search chunk: {}", e.getMessage());
            }
        }
        
        if (foundResult == null) {
            log.warn("âš ï¸ No record found in any sales table for phone: {}", phoneNumber);
            return;
        }
        
        // Update record vá»›i "Tráº¡ng thÃ¡i mess" = "Há»§y"
        log.info("ðŸ”„ Updating record: Base ID={}, Table ID={}, Record ID={}", 
                salesBaseId, foundResult.tableId, foundResult.recordId);
        
        try {
            org.report.PosTobase.dto.BitableBatchUpdateRequest batchUpdateRequest = 
                    org.report.PosTobase.dto.BitableBatchUpdateRequest.builder()
                    .records(List.of(
                            org.report.PosTobase.dto.BitableBatchUpdateRequest.UpdateRecord.builder()
                                    .recordId(foundResult.recordId)
                                    .fields(Map.of("Tráº¡ng thÃ¡i mess", "Há»§y"))
                                    .build()
                    ))
                    .build();
            
            bitableService.batchUpdateRecords(
                    salesBaseId,
                    foundResult.tableId,
                    userAccessToken,
                    batchUpdateRequest
            );
            
            log.info("âœ… Successfully updated record in table {} ({}): Tráº¡ng thÃ¡i mess = Há»§y", 
                    foundResult.tableId, foundResult.displayName);
            
        } catch (Exception e) {
            log.error("âŒ Failed to update record: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update record: " + e.getMessage(), e);
        }
    }
    
    /**
     * Inner class Ä‘á»ƒ lÆ°u káº¿t quáº£ tÃ¬m kiáº¿m
     */
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
    
    /**
     * Láº¥y user access token vÃ  Ä‘áº£m báº£o token cÃ²n hÆ¡n 30 phÃºt
     * Tá»± Ä‘á»™ng refresh token náº¿u cáº§n
     * Fallback: sá»­ dá»¥ng tenant token náº¿u user token khÃ´ng cÃ³
     */
    private String getUserAccessToken() {
        return getUserAccessToken(true);
    }

    /**
     * Láº¥y user access token vá»›i option kiá»ƒm tra vÃ  refresh
     * @param ensureValid true náº¿u muá»‘n kiá»ƒm tra vÃ  refresh token náº¿u cáº§n
     */
    private String getUserAccessToken(boolean ensureValid) {
        // Kiá»ƒm tra vÃ  refresh token náº¿u cáº§n (cÃ²n < 30 phÃºt)
        if (ensureValid && tokenRefreshScheduler != null) {
            try {
                boolean refreshed = tokenRefreshScheduler.refreshTokenIfNeeded();
                if (refreshed) {
                    log.debug("âœ… Token Ä‘Ã£ Ä‘Æ°á»£c kiá»ƒm tra vÃ  refresh náº¿u cáº§n");
                }
            } catch (Exception e) {
                log.warn("âš ï¸ Failed to refresh token if needed: {}", e.getMessage());
            }
        }

        // Æ¯u tiÃªn láº¥y tá»« TokenStorageService (Ä‘Æ°á»£c cáº­p nháº­t bá»Ÿi scheduler)
        if (tokenStorageService != null) {
            String token = tokenStorageService.getUserAccessToken();
            if (token != null && !token.isBlank()) {
                log.debug("âœ… Using user access token from TokenStorageService");
                return token;
            }
        }

        // Fallback: láº¥y tá»« LarkBaseProperties (náº¿u TokenStorageService khÃ´ng cÃ³)
        if (larkBaseProperties != null && larkBaseProperties.getUserAccessToken() != null) {
            String token = larkBaseProperties.getUserAccessToken().trim();
            if (!token.isBlank()) {
                log.debug("âš ï¸ Using user access token from LarkBaseProperties (fallback)");
                return token;
            }
        }

        // Fallback: láº¥y tenant token tá»« TokenStorageService (Ä‘Æ°á»£c lÆ°u khi user login)
        if (tokenStorageServiceForTenant != null) {
            String tenantToken = tokenStorageServiceForTenant.getTenantAccessToken();
            if (tenantToken != null && !tenantToken.isBlank()) {
                log.warn("âš ï¸ User access token not found, using tenant access token from storage");
                return tenantToken;
            }
        }

        // Fallback cuá»‘i cÃ¹ng: gá»i API Ä‘á»ƒ láº¥y tenant token (náº¿u khÃ´ng cÃ³ trong storage)
        if (tenantTokenService != null) {
            try {
                String tenantToken = tenantTokenService.getTenantAccessToken();
                if (tenantToken != null && !tenantToken.isBlank()) {
                    log.warn("âš ï¸ Using tenant access token from API (no token in storage)");
                    return tenantToken;
                }
            } catch (Exception e) {
                log.error("âŒ Failed to get tenant access token: {}", e.getMessage());
            }
        }

        log.error("âŒ No access token available (neither user token nor tenant token)");
        return null;
    }
}


