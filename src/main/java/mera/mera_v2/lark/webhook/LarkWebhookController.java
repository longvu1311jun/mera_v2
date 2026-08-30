package mera.mera_v2.lark.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.token.TokenStorageService;
import mera.mera_v2.lark.webhook.config.LarkBaseProperties;
import mera.mera_v2.lark.webhook.dto.BitableRecordResponse;
import mera.mera_v2.lark.webhook.dto.PosOrderWebhook;
import mera.mera_v2.lark.webhook.mapping.CskhMapping;
import mera.mera_v2.lark.webhook.mapping.CskhMappingService;
import mera.mera_v2.lark.webhook.scheduler2.TokenRefreshScheduler;
import mera.mera_v2.lark.webhook.service.LarkBitableService;
import mera.mera_v2.lark.webhook.service.PosToBitableMapper;
import mera.mera_v2.lark.webhook.service.TenantTokenService;
import mera.mera_v2.lark.webhook.service.WebhookConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Nhan webhook don hang tu POS (Pancake) va tao ban ghi trong Lark Bitable.
 *
 * Nhanh nay KHONG dung database: mapping CSKH -> Base/Table doc tu file JSON
 * (xem {@link CskhMappingService}), token Lark giu trong bo nho.
 */
@Slf4j
@RestController
@RequestMapping("/api/lark")
@RequiredArgsConstructor
public class LarkWebhookController {

    private static final String DEFAULT_VIEW_ID = "vew5Ou4Kee";

    private final ObjectMapper mapper = new ObjectMapper();

    private final LarkBitableService bitableService;
    private final PosToBitableMapper posToBitableMapper;
    private final CskhMappingService cskhMappingService;
    private final WebhookConfigService webhookConfigService;
    private final TokenStorageService tokenStorageService;
    private final TokenRefreshScheduler tokenRefreshScheduler;
    private final TenantTokenService tenantTokenService;
    private final LarkBaseProperties larkBaseProperties;

    @Value("${pancake.webhook.secret:}")
    private String expectedSecret;

    @Value("${lark.bitable.auto-create:true}")
    private boolean autoCreateRecord;

    @PostMapping("/orders")
    public ResponseEntity<String> onOrderWebhook(
            @RequestHeader(value = "X-Pancake-Secret", required = false) String secret,
            @RequestBody String rawBody
    ) {
        log.info("data: {}", rawBody);

        if (expectedSecret != null && !expectedSecret.isBlank()
                && !expectedSecret.equals(secret)) {
            log.error("Invalid X-Pancake-Secret");
            return ResponseEntity.status(401).body("unauthorized");
        }

        try {
            JsonNode root = mapper.readTree(rawBody);
            PosOrderWebhook orderWebhook = mapper.treeToValue(root, PosOrderWebhook.class);

            if (!autoCreateRecord) {
                log.info("lark.bitable.auto-create=false, bo qua tao ban ghi");
                return ResponseEntity.ok("ok");
            }

            if (!shouldCreateRecord(root, orderWebhook)) {
                return ResponseEntity.ok("ok");
            }

            try {
                createBitableRecord(orderWebhook);
            } catch (Exception e) {
                log.error("Loi khi tao ban ghi Lark: {}", e.getMessage(), e);
            }

            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Failed to parse webhook JSON: {}", e.getMessage());
            return ResponseEntity.badRequest().body("bad request");
        }
    }

    /**
     * Quyet dinh co tao ban ghi hay khong.
     * Tao khi: don co tag "Dong bo DATA", HOAC status duoc bat trong config va don Facebook
     * chua huy, co status = 1 hien tai hoac trong lich su.
     */
    private boolean shouldCreateRecord(JsonNode root, PosOrderWebhook orderWebhook) {
        if (hasDongBoDataTag(root)) {
            log.info("Don co tag 'Dong bo DATA' - tao ban ghi");
            return true;
        }

        Integer status = extractStatus(root);
        if (status == null) {
            log.info("Bo qua: khong tim thay status trong webhook");
            return false;
        }

        if (!webhookConfigService.shouldProcess(status)) {
            log.info("Status {} khong duoc bat trong config, bo qua", status);
            return false;
        }

        if (!isFacebookSource(orderWebhook)) {
            log.info("Bo qua: don hang khong phai nguon Facebook");
            return false;
        }

        if (isCancelledStatus(status)) {
            log.info("Bo qua: don hang co trang thai huy (status={})", status);
            return false;
        }

        if (status != 1 && !hasStatusInHistory(orderWebhook, 1)) {
            log.info("Bo qua: status hien tai = {} va khong co lich su status = 1", status);
            return false;
        }

        return true;
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

        String orderSourcesName = orderWebhook.getOrderSourcesName();
        if (orderSourcesName != null && !orderSourcesName.isBlank()) {
            String lower = orderSourcesName.toLowerCase().trim();
            if (lower.contains("zalo") || lower.contains("shopee")
                    || lower.contains("lazada") || lower.contains("tiktok")
                    || lower.contains("tiki") || lower.contains("sendo")
                    || lower.contains("website") || lower.contains("tong_dai")) {
                return false;
            }
            if (lower.contains("facebook") || lower.contains("fb") || lower.contains("mess")) {
                return true;
            }
        }

        String pageId = orderWebhook.getPageId();
        if (pageId == null || pageId.isBlank()) return false;
        return pageId.startsWith("pzl_") || pageId.startsWith("fb_");
    }

    /**
     * Kiem tra trang thai huy don.
     * Status huy: 5 (huy boi khach), 7 (huy he thong), 8 (huy khac), 9 (that bai).
     */
    private boolean isCancelledStatus(Integer status) {
        if (status == null) return false;
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

    private boolean hasDongBoDataTag(JsonNode root) {
        JsonNode tagsNode = root.get("tags");
        if ((tagsNode == null || !tagsNode.isArray()) && root.has("data")) {
            tagsNode = root.get("data").get("tags");
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

    /**
     * Tao ban ghi trong Lark Bitable tu du lieu don hang POS.
     * Base/Table lay tu file mapping theo SDT cua CSKH (assigning_care).
     */
    private void createBitableRecord(PosOrderWebhook orderWebhook) throws Exception {
        log.info("Order ID: {}, Status: {}", orderWebhook.getId(), orderWebhook.getStatus());

        PosOrderWebhook.AssigningSeller cskh = posToBitableMapper.getAssigningCare(orderWebhook);
        String cskhName = (cskh != null && cskh.getName() != null) ? cskh.getName().trim() : null;

        String cskhPhone = posToBitableMapper.getCskhPhoneNumber(orderWebhook);
        if (cskhPhone == null) {
            cskhPhone = extractPhoneFromName(cskhName);
        }

        if (cskhPhone == null) {
            log.error("Khong xac dinh duoc SDT CSKH (name='{}'), khong tao duoc ban ghi", cskhName);
            return;
        }

        Optional<CskhMapping> mappingOpt = cskhMappingService.findByPhone(cskhPhone);
        if (mappingOpt.isEmpty()) {
            log.error("Khong tim thay mapping cho CSKH '{}' (phone={}) trong file mapping. "
                    + "Them entry roi goi POST /api/lark/cskh-mapping/reload", cskhName, cskhPhone);
            return;
        }

        CskhMapping mapping = mappingOpt.get();
        String appToken = mapping.getBaseId();
        String targetTableId = mapping.getKhachHangTableId();
        String viewId = (mapping.getKhachHangViewId() != null && !mapping.getKhachHangViewId().isBlank())
                ? mapping.getKhachHangViewId()
                : DEFAULT_VIEW_ID;

        log.info("Mapping CSKH '{}' (phone={}): baseId={}, tableId={}, viewId={}, baseName={}",
                cskhName, cskhPhone, appToken, targetTableId, viewId, mapping.getBaseName());

        String userAccessToken = getUserAccessToken();
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new IllegalStateException("User access token is not available. Please login at /lark first.");
        }

        // Kiem tra SDT khach da ton tai chua de tranh tao trung
        String phoneNumber = posToBitableMapper.getDienThoai(orderWebhook);
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            try {
                boolean phoneExists = bitableService.checkPhoneExistsWithFilter(
                        appToken, targetTableId, userAccessToken, phoneNumber, viewId);

                if (phoneExists) {
                    log.warn("SDT '{}' da ton tai trong bang Lark, bo qua tao ban ghi", phoneNumber);
                    return;
                }
            } catch (Exception e) {
                log.error("Loi khi kiem tra trung SDT: {}. Van tiep tuc tao ban ghi.", e.getMessage());
            }
        } else {
            log.warn("SDT khach hang rong, khong kiem tra duoc trung lap. Van tao ban ghi.");
        }

        Map<String, Object> fields = posToBitableMapper.mapToBitableFields(orderWebhook);
        BitableRecordResponse response = bitableService.createRecord(
                appToken, targetTableId, userAccessToken, fields);

        if (response.isSuccess() && response.getData() != null) {
            String recordId = response.getData().getRecord() != null
                    ? response.getData().getRecord().getRecordId()
                    : "unknown";
            log.info("Tao ban ghi Lark thanh cong: recordId={}", recordId);
        } else {
            throw new RuntimeException(String.format("Bitable error: code=%d, msg=%s",
                    response.getCode(), response.getMsg()));
        }
    }

    private String getUserAccessToken() {
        try {
            tokenRefreshScheduler.refreshTokenIfNeeded();
        } catch (Exception e) {
            log.warn("Failed to refresh token if needed: {}", e.getMessage());
        }

        String token = tokenStorageService.getUserAccessToken();
        if (token != null && !token.isBlank()) {
            return token;
        }

        if (larkBaseProperties.getUserAccessToken() != null
                && !larkBaseProperties.getUserAccessToken().isBlank()) {
            return larkBaseProperties.getUserAccessToken().trim();
        }

        String tenantToken = tokenStorageService.getTenantAccessToken();
        if (tenantToken != null && !tenantToken.isBlank()) {
            log.warn("Using tenant access token from storage");
            return tenantToken;
        }

        try {
            tenantToken = tenantTokenService.getTenantAccessToken();
            if (tenantToken != null && !tenantToken.isBlank()) {
                log.warn("Using tenant access token from API");
                return tenantToken;
            }
        } catch (Exception e) {
            log.error("Failed to get tenant access token: {}", e.getMessage());
        }

        log.error("No access token available");
        return null;
    }

    /**
     * Trich xuat so dien thoai tu ten CSKH.
     * Vi du: "Ha Quang Vuong Sale 2 NT 0968420624" -> "0968420624"
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
        java.util.regex.Pattern simplePattern = java.util.regex.Pattern.compile("[0-9]{10}");
        java.util.regex.Matcher simpleMatcher = simplePattern.matcher(text.replaceAll("[^0-9]", ""));
        if (simpleMatcher.find()) return simpleMatcher.group();
        return null;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("endpoint", "/api/lark/orders");
        health.put("autoCreate", autoCreateRecord);
        health.put("hasUserToken", tokenStorageService.hasToken());
        health.put("userTokenRemainingSeconds", tokenStorageService.getTokenRemainingSeconds());
        health.put("cskhMappings", cskhMappingService.size());
        health.put("cskhMappingSource", cskhMappingService.getLoadedFrom());
        return ResponseEntity.ok(health);
    }
}
