package mera.mera_v2.lark.webhook.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.PendingFollowupNotification;
import mera.mera_v2.lark.token.TokenStorageService;
import mera.mera_v2.lark.webhook.dto.BitableSearchRequest;
import mera.mera_v2.lark.webhook.dto.BitableSearchResponse;
import mera.mera_v2.lark.webhook.service.LarkBitableService;
import mera.mera_v2.repository.PendingFollowupNotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Scheduler chay deu de xu ly PendingFollowupNotification.
 * Moi 5 phut, doc cac ban ghi da den gio, search theo SDT trong Bang Khach Hang.
 * Neu link_record_ids van null → gui tin nhan Lark cho CSKH.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingFollowupScheduler {

    private static final String LARK_IM_URL =
            "https://open.larksuite.com/open-apis/im/v1/messages?receive_id_type=open_id";
    private static final String PHONE_FIELD = "Điện thoại";
    private static final String CSKH_FIELD = "Người CSKH";
    private static final int MAX_RETRIES = 2;

    private final PendingFollowupNotificationRepository pendingRepo;
    private final LarkBitableService bitableService;

    @Autowired(required = false)
    private TokenStorageService tokenStorageService;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    // ========== SCHEDULER: chạy mỗi 5 phút ==========
    // Test: @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    // Prod: @Scheduled(cron = "0 */5 * * * *") // mỗi 5 phút
    @Scheduled(cron = "0 */5 * * * *")
    public void processPendingFollowups() {
        log.info("========================================");
        log.info("[PendingFollowupScheduler] Bat dau kiem tra pending notifications...");
        log.info("========================================");

        List<PendingFollowupNotification> pendingList =
                pendingRepo.findPendingDue(LocalDateTime.now());

        if (pendingList.isEmpty()) {
            log.info("[PendingFollowupScheduler] Khong co pending notification nao can xu ly");
            return;
        }

        log.info("[PendingFollowupScheduler] Tim thay {} pending notification(s)", pendingList.size());

        int success = 0, failed = 0, skipped = 0;

        for (PendingFollowupNotification pending : pendingList) {
            try {
                boolean done = processOnePending(pending);
                if (done) {
                    success++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("[PendingFollowupScheduler] Loi khi xu ly pending id={}: {}",
                        pending.getId(), e.getMessage());
            }

            // Delay nho giua cac item de tranh rate limit
            try { Thread.sleep(1_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }

        log.info("========================================");
        log.info("[PendingFollowupScheduler] Hoan thanh: success={}, skipped={}, failed={}",
                success, skipped, failed);
        log.info("========================================");
    }

    /**
     * Xu ly mot pending notification.
     * @return true = da xu ly xong (da gui tin hoac skip), false = can retry sau
     */
    private boolean processOnePending(PendingFollowupNotification pending) {
        log.info("[PendingFollowupScheduler] Dang xu ly pending id={}, phone={}, baseId={}, tableId={}",
                pending.getId(), pending.getPhoneNumber(), pending.getBaseId(), pending.getTableId());

        String token = getUserAccessToken();
        if (token == null || token.isBlank()) {
            log.error("[PendingFollowupScheduler] Khong co user access token, skip pending id={}", pending.getId());
            return true; // mark as processed de khong bi lap mai
        }

        // --- Search trong Bang Khach Hang theo SDT ---
        List<String> linkRecordIds = searchLinkRecordIds(
                pending.getBaseId(),
                pending.getTableId(),
                pending.getViewId(),
                pending.getPhoneNumber(),
                token
        );

        // Neu van chua co link → gui tin nhan CSKH
        if (linkRecordIds == null || linkRecordIds.isEmpty()) {
            log.info("[PendingFollowupScheduler] Record {} van chua co link sang Bang Khach Hang. Tim CSKH de gui tin nhan...",
                    pending.getCreatedRecordId());

            String openId = extractCskhOpenId(
                    pending.getBaseId(),
                    pending.getTableId(),
                    pending.getPhoneNumber(),
                    token
            );

            if (openId != null && !openId.isBlank()) {
                String customerName = pending.getCustomerName() != null ? pending.getCustomerName() : "Khach hang";
                sendLarkMessage(openId, pending.getPhoneNumber(), customerName, token);
                markProcessed(pending, true, "Da gui tin nhan den CSKH open_id=" + openId);
            } else {
                // Thu lai sau
                log.warn("[PendingFollowupScheduler] Khong tim thay CSKH open_id cho phone={}, scheduling retry",
                        pending.getPhoneNumber());
                scheduleRetry(pending);
                return false;
            }
        } else {
            // Da co link → khong can gui tin nhan
            markProcessed(pending, true, "Record da co link_record_ids=" + linkRecordIds + ", bo qua");
            log.info("[PendingFollowupScheduler] Record {} da co link ({}). Bo qua gui tin nhan.",
                    pending.getCreatedRecordId(), linkRecordIds.size());
        }

        return true;
    }

    // ========== SEARCH: lay link_record_ids ==========

    /**
     * Search trong Bang Khach Hang theo so dien thoai.
     * Tra ve danh sach link_record_ids, hoac null/empty neu khong tim thay.
     */
    private List<String> searchLinkRecordIds(
            String baseId,
            String tableId,
            String viewId,
            String phoneNumber,
            String token
    ) {
        String view = viewId != null ? viewId : "vew5Ou4Kee";

        BitableSearchRequest request = BitableSearchRequest.builder()
                .automaticFields(false)
                .fieldNames(List.of(PHONE_FIELD, CSKH_FIELD))
                .viewId(view)
                .pageSize(1)
                .filter(BitableSearchRequest.Filter.builder()
                        .conjunction("and")
                        .conditions(List.of(
                                BitableSearchRequest.Condition.builder()
                                        .fieldName(PHONE_FIELD)
                                        .operator("is")
                                        .value(List.of(phoneNumber))
                                        .build()
                        ))
                        .build())
                .build();

        try {
            BitableSearchResponse resp = bitableService.searchRecords(baseId, tableId, token, request);
            if (resp == null || !resp.isSuccess() || resp.getData() == null
                    || resp.getData().getItems() == null || resp.getData().getItems().isEmpty()) {
                log.debug("[PendingFollowupScheduler] Search: khong tim thay record voi phone={}", phoneNumber);
                return Collections.emptyList();
            }

            BitableSearchResponse.SearchItem item = resp.getData().getItems().get(0);
            Map<String, Object> fields = item.getFields();

            // Lay link_record_ids tu fields (Lark tra ve khi co lien ket)
            Object linkIds = fields != null ? fields.get("link_record_ids") : null;
            if (linkIds instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) linkIds;
                log.info("[PendingFollowupScheduler] Tim thay link_record_ids: {}", ids);
                return ids;
            }

            log.debug("[PendingFollowupScheduler] Record ton tai nhung khong co link_record_ids cho phone={}", phoneNumber);
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("[PendingFollowupScheduler] Search loi cho phone={}: {}", phoneNumber, e.getMessage());
            return null; // null = loi, can retry
        }
    }

    // ========== LAY OPEN_ID TU NGƯỜI CSKH ==========

    /**
     * Search record theo SDT, lay open_id tu field "Người CSKH".
     */
    private String extractCskhOpenId(
            String baseId,
            String tableId,
            String phoneNumber,
            String token
    ) {
        String view = "vew5Ou4Kee";

        BitableSearchRequest request = BitableSearchRequest.builder()
                .automaticFields(false)
                .fieldNames(List.of(PHONE_FIELD, CSKH_FIELD))
                .viewId(view)
                .pageSize(1)
                .filter(BitableSearchRequest.Filter.builder()
                        .conjunction("and")
                        .conditions(List.of(
                                BitableSearchRequest.Condition.builder()
                                        .fieldName(PHONE_FIELD)
                                        .operator("is")
                                        .value(List.of(phoneNumber))
                                        .build()
                        ))
                        .build())
                .build();

        try {
            BitableSearchResponse resp = bitableService.searchRecords(baseId, tableId, token, request);
            if (resp == null || !resp.isSuccess() || resp.getData() == null
                    || resp.getData().getItems() == null || resp.getData().getItems().isEmpty()) {
                log.warn("[PendingFollowupScheduler] Khong tim thay record nao de lay CSKH open_id, phone={}", phoneNumber);
                return null;
            }

            Map<String, Object> fields = resp.getData().getItems().get(0).getFields();
            if (fields == null) {
                return null;
            }

            Object cskhValue = fields.get(CSKH_FIELD);
            if (cskhValue == null) {
                log.warn("[PendingFollowupScheduler] Field '{}' null cho phone={}", CSKH_FIELD, phoneNumber);
                return null;
            }

            // Lark tra ve field CSKH nhu the nao? Thu parse theo Map (open_id)
            if (cskhValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cskhMap = (Map<String, Object>) cskhValue;
                Object openId = cskhMap.get("open_id");
                if (openId != null) {
                    String openIdStr = openId.toString();
                    log.info("[PendingFollowupScheduler] Lay duoc CSKH open_id={} cho phone={}", openIdStr, phoneNumber);
                    return openIdStr;
                }
                // Thu lay id
                Object id = cskhMap.get("id");
                if (id != null) {
                    return id.toString();
                }
            }

            // Fallback: co the la string (ten CSKH)
            if (cskhValue instanceof String) {
                log.info("[PendingFollowupScheduler] CSKH field la string='{}' (co the can map ten→open_id)", cskhValue);
                return cskhValue.toString();
            }

            log.warn("[PendingFollowupScheduler] CSKH field kha nang dang={} cho phone={}",
                    cskhValue.getClass().getSimpleName(), phoneNumber);
            return cskhValue.toString();

        } catch (Exception e) {
            log.error("[PendingFollowupScheduler] Loi khi lay CSKH open_id cho phone={}: {}", phoneNumber, e.getMessage());
            return null;
        }
    }

    // ========== GUI TIN NHAN LARK IM ==========

    /**
     * Gui tin nhan Lark IM den open_id.
     */
    private void sendLarkMessage(String openId, String phoneNumber, String customerName, String token) {
        if (openId == null || openId.isBlank()) {
            log.warn("[PendingFollowupScheduler] openId null, khong gui duoc tin nhan");
            return;
        }

        String text = String.format("Khách hàng: %s (%s) chưa có trao đổi sau 30 phút kể từ khi được ghi nhận. Vui lòng kiểm tra và liên hệ.",
                customerName, phoneNumber);

        String contentJson = "{\"text\":\"" + escapeForJson(text) + "\"}";

        Map<String, Object> body = new HashMap<>();
        body.put("receive_id", openId);
        body.put("msg_type", "text");
        body.put("content", contentJson);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            if (restTemplate == null) {
                log.error("[PendingFollowupScheduler] RestTemplate chua duoc khoi tao");
                return;
            }
            ResponseEntity<String> resp = restTemplate.exchange(
                    LARK_IM_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            log.info("[PendingFollowupScheduler] Gui tin nhan thanh cong den open_id={}: {}", openId, resp.getBody());
        } catch (Exception e) {
            log.error("[PendingFollowupScheduler] Loi khi gui tin nhan den open_id={}: {}", openId, e.getMessage());
        }
    }

    private String escapeForJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ========== TRANG THAI ==========

    private void markProcessed(PendingFollowupNotification pending, boolean success, String note) {
        pending.setProcessed(true);
        pending.setProcessedAt(LocalDateTime.now());
        pending.setNote(note);
        pendingRepo.save(pending);
    }

    private void scheduleRetry(PendingFollowupNotification pending) {
        int retries = pending.getRetryCount() != null ? pending.getRetryCount() : 0;
        if (retries >= MAX_RETRIES) {
            log.warn("[PendingFollowupScheduler] Da vuot so lan retry cho pending id={}, danh dau processed",
                    pending.getId());
            markProcessed(pending, false, "Qua so lan retry toi da (" + MAX_RETRIES + ")");
            return;
        }
        pending.setRetryCount(retries + 1);
        pending.setScheduledAt(LocalDateTime.now().plusMinutes(15)); // retry sau 15 phut
        pending.setNote("Retry " + (retries + 1) + "/" + MAX_RETRIES);
        pendingRepo.save(pending);
        log.info("[PendingFollowupScheduler] Da schedule retry {} cho pending id={} luc {}",
                (retries + 1), pending.getId(), pending.getScheduledAt());
    }

    // ========== TOKEN ==========

    private String getUserAccessToken() {
        if (tokenStorageService != null) {
            String t = tokenStorageService.getUserAccessToken();
            if (t != null && !t.isBlank()) return t;
        }
        log.warn("[PendingFollowupScheduler] Khong lay duoc user access token");
        return null;
    }
}
