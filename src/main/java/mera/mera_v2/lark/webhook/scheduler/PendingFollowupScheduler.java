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
 * Luong 30 phut (Pending Followup Notification):
 *
 * Buoc 1: Them ban ghi vao Bang Lieu Trinh (Lark webhook -> bitableService.createRecord)
 * Buoc 2: Luu so dien thoai vao bang pending_followup_notifications (trong DB)
 *          -> Scheduler se tu dong chay sau 30 phut (moi 5 phut check 1 lan)
 *
 * Sau 30 phut, scheduler lam Buoc 3-5:
 * Buoc 3: Goi API search voi SDT da luu
 * Buoc 4: Kiem tra link_record_ids -> null thi lay id tu field "Nguoi CSKH"
 * Buoc 5: Gui tin nhan Lark IM bang API voi SDT va id tu Buoc 4
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingFollowupScheduler {

    private static final String LARK_IM_URL =
            "https://open.larksuite.com/open-apis/im/v1/messages?receive_id_type=open_id";
    private static final String PHONE_FIELD = "Điện thoại";
    private static final String CSKH_FIELD = "Người CSKH";
    private static final String TRAO_DOI_FIELD = "Trao đổi gần nhất"; // field kiem tra da co trao doi chua
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
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
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
     * Xu ly mot pending notification (Buoc 3–5 cua luong 30 phut).
     * Buoc 3:  Goi API search voi SDT da luu
     * Buoc 4:  Kiem tra link_record_ids -> null thi lay id tu field "Nguoi CSKH"
     * Buoc 5:  Gui tin nhan Lark IM
     * @return true = da xu ly xong (da gui tin hoac skip), false = can retry sau
     */
    private boolean processOnePending(PendingFollowupNotification pending) {
        log.info("[PendingFollowupScheduler] Dang xu ly pending id={}, phone={}, baseId={}, tableId={}",
                pending.getId(), pending.getPhoneNumber(), pending.getBaseId(), pending.getTableId());

        String tenantToken = getTenantAccessToken();
        if (tenantToken == null || tenantToken.isBlank()) {
            log.error("[PendingFollowupScheduler] Khong co tenant access token, skip pending id={}", pending.getId());
            return true;
        }

        // Bước 3 & 4: Bitable search cần user_access_token
        String userToken = getUserAccessToken();
        if (userToken == null || userToken.isBlank()) {
            log.error("[PendingFollowupScheduler] Khong co user access token, skip pending id={}", pending.getId());
            return true;
        }

    // ========== BƯỚC 3: GỌI API SEARCH VỚI SDT ==========
    // Gọi API search với SDT đã lưu ở Bước 2
    List<String> linkRecordIds = searchLinkRecordIds(
            pending.getBaseId(),
            pending.getTableId(),
            pending.getViewId(),
            pending.getPhoneNumber(),
            userToken
    );

    // searchLinkRecordIds tra ve:
    // - null     = loi can retry
    // - empty()  = chua co link, tiep tuc buoc 4
    // - [ids]    = da co link, skip
    if (linkRecordIds == null) {
        // Tra ve null = skip chu dong (da co trao doi hoac loi), khong retry
        markProcessed(pending, true, "KH da co trao doi, bo qua thong bao");
        log.info("[PendingFollowupScheduler] Da co trao doi hoac skip chu dong, danh dau processed, phone={}", pending.getPhoneNumber());
        return true;
    }

    // Da co trao doi → markProcessed + skip
    if (linkRecordIds.isEmpty()) {
        // Chuong trinh o day = searchLinkRecordIds da log "da co trao doi" roi
        // Tiep tuc buoc 4 de lay CSKH
        log.info("[PendingFollowupScheduler] Record chua co link. Bat dau Buoc 4, phone={}", pending.getPhoneNumber());
    } else {
        // Co link_record_ids → Lark da tu link → ket thuc
        markProcessed(pending, true, "Record da co link_record_ids, bo qua");
        log.info("[PendingFollowupScheduler] Record {} da co link ({}). Bo qua Buoc 4-5.",
                pending.getCreatedRecordId(), linkRecordIds.size());
        return true;
    }

    // ========== BƯỚC 4: LẤY ID TỪ "NGƯỜI CSKH" ==========
    String openId = extractCskhOpenId(
            pending.getBaseId(),
            pending.getTableId(),
            pending.getPhoneNumber(),
            userToken
    );

    // ========== BƯỚC 5: GỬI TIN NHẮN ==========
    if (openId != null && !openId.isBlank()) {
        String customerName = pending.getCustomerName() != null ? pending.getCustomerName() : "Khach hang";
        sendLarkMessage(openId, pending.getPhoneNumber(), customerName, tenantToken);
        markProcessed(pending, true, "Đã gửi tin nhắn đến CSKH open_id=" + openId);
        log.info("[PendingFollowupScheduler] Hoàn thành Bước 5: gửi tin đến {}", openId);
    } else {
        // Chưa lấy được id → retry sau 15 phút
        log.warn("[PendingFollowupScheduler] Không tìm thấy id từ 'Người CSKH' cho phone={}", pending.getPhoneNumber());
        scheduleRetry(pending);
        return false;
    }

    return true;
    }

    // ========== BƯỚC 3: GỌI API SEARCH VỚI SDT ==========

    /**
     * Bước 3: Gọi API search vào Bảng Liệu trình với số điện thoại đã lưu.
     * Trả về danh sách link_record_ids, hoặc null/empty nếu không tìm thấy.
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
                .fieldNames(List.of(PHONE_FIELD, CSKH_FIELD, TRAO_DOI_FIELD))
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

            // LOG CHI TIET: in tat ca fields tra ve de debug
            log.info("[PendingFollowupScheduler] === RAW FIELDS DEBUG phone={} ===", phoneNumber);
            if (fields != null) {
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    log.info("[DEBUG] Field '{}' = {} (type: {})",
                            entry.getKey(),
                            entry.getValue(),
                            entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null");
                }
            } else {
                log.warn("[DEBUG] fields == null");
            }
            log.info("[PendingFollowupScheduler] === END RAW FIELDS DEBUG phone={} ===", phoneNumber);

            // Lay link_record_ids tu fields (Lark tra ve khi co lien ket)
            Object linkIds = fields != null ? fields.get("link_record_ids") : null;

            // Kiem tra field "Trao doi gan nhat" — phai check link_record_ids ben trong object
            Object traoDoiValue = fields != null ? fields.get(TRAO_DOI_FIELD) : null;
            boolean daCoTraoDoi = false;
            if (traoDoiValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> traoDoiMap = (Map<String, Object>) traoDoiValue;
                Object linkRecordIds = traoDoiMap.get("link_record_ids");
                daCoTraoDoi = linkRecordIds != null && !linkRecordIds.toString().isBlank();
                log.info("[PendingFollowupScheduler] Trao doi check: traoDoiValue={}, linkRecordIds={}, daCoTraoDoi={}, phone={}",
                        traoDoiValue, linkRecordIds, daCoTraoDoi, phoneNumber);
            }
            if (daCoTraoDoi) {
                log.info("[PendingFollowupScheduler] Record da co trao doi (field '{}'), bo qua buoc 3-5, phone={}",
                        TRAO_DOI_FIELD, phoneNumber);
                return null; // null = skip chu dong, caller se markProcessed
            }

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

    // ========== BƯỚC 4: LẤY ID TỪ "NGƯỜI CSKH" ==========

    /**
     * Bước 4: Search record vừa tạo theo SDT, lấy id từ field "Người CSKH".
     * Trả về open_id của CSKH để gửi tin nhắn ở Bước 5.
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
                .fieldNames(List.of(PHONE_FIELD, CSKH_FIELD, TRAO_DOI_FIELD))
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
                log.warn("[PendingFollowupScheduler] fields == null, phone={}", phoneNumber);
                return null;
            }

            // Debug: in tat ca fields nhan duoc
            log.info("[PendingFollowupScheduler] === extractCskhOpenId RAW FIELDS phone={} ===", phoneNumber);
            for (Map.Entry<String, Object> e : fields.entrySet()) {
                log.info("[DEBUG B4] Field '{}' = {} (type: {})",
                        e.getKey(), e.getValue(),
                        e.getValue() != null ? e.getValue().getClass().getSimpleName() : "null");
            }
            log.info("[PendingFollowupScheduler] === END RAW FIELDS phone={} ===", phoneNumber);

            // Kiem tra field trao doi — phai check link_record_ids ben trong object
            Object traoDoiValue = fields.get(TRAO_DOI_FIELD);
            boolean daCoTraoDoi = false;
            if (traoDoiValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> traoDoiMap = (Map<String, Object>) traoDoiValue;
                Object linkRecordIds = traoDoiMap.get("link_record_ids");
                daCoTraoDoi = linkRecordIds != null && !linkRecordIds.toString().isBlank();
                log.info("[PendingFollowupScheduler] Trao doi check B4: traoDoiValue={}, linkRecordIds={}, daCoTraoDoi={}, phone={}",
                        traoDoiValue, linkRecordIds, daCoTraoDoi, phoneNumber);
            }
            if (daCoTraoDoi) {
                log.info("[PendingFollowupScheduler] Record da co trao doi (field '{}'), bo qua buoc lay CSKH, phone={}",
                        TRAO_DOI_FIELD, phoneNumber);
                return null;
            }

            // CSKH luon co gia tri khi chua co trao doi, lay open_id
            Object cskhValue = fields.get(CSKH_FIELD);
            log.info("[PendingFollowupScheduler] CSKH field value={} (type={}) cho phone={}",
                    cskhValue, cskhValue != null ? cskhValue.getClass().getSimpleName() : "null", phoneNumber);

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
                // Thu lay id ( Lark user field chuan tra ve truong "id")
                Object id = cskhMap.get("id");
                if (id != null) {
                    String idStr = id.toString();
                    log.info("[PendingFollowupScheduler] Lay duoc CSKH id={} cho phone={}", idStr, phoneNumber);
                    return idStr;
                }
            }

            // Lark user field co the tra ve List (danh sach nguoi duoc assign)
            if (cskhValue instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> cskhList = (List<Object>) cskhValue;
                if (!cskhList.isEmpty()) {
                    Object first = cskhList.get(0);
                    if (first instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> firstMap = (Map<String, Object>) first;
                        Object openId = firstMap.get("open_id");
                        if (openId != null) {
                            String openIdStr = openId.toString();
                            log.info("[PendingFollowupScheduler] Lay duoc CSKH open_id tu list={} cho phone={}", openIdStr, phoneNumber);
                            return openIdStr;
                        }
                        Object id = firstMap.get("id");
                        if (id != null) {
                            String idStr = id.toString();
                            log.info("[PendingFollowupScheduler] Lay duoc CSKH id tu list={} cho phone={}", idStr, phoneNumber);
                            return idStr;
                        }
                    }
                    // Neu khong parse duoc Map, lay toString() cua phan tu dau tien
                    String firstStr = first.toString();
                    log.info("[PendingFollowupScheduler] Lay phan tu dau tien tu list CSKH='{}' cho phone={}", firstStr, phoneNumber);
                    return firstStr;
                }
            }

            // Fallback: co the la string (ten CSKH)
            if (cskhValue instanceof String) {
                log.info("[PendingFollowupScheduler] CSKH field la string='{}' (co the can map ten->open_id)", cskhValue);
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

    // ========== BƯỚC 5: GỬI TIN NHẮN LARK IM ==========

    /**
     * Gui tin nhan Lark IM den open_id.
     */
    private void sendLarkMessage(String openId, String phoneNumber, String customerName, String token) {
        if (openId == null || openId.isBlank()) {
            log.warn("[PendingFollowupScheduler] openId null, khong gui duoc tin nhan");
            return;
        }

        // Dam bao openId chi la chuoi id thuan, khong phai object/map/list
        if (openId.startsWith("{") || openId.startsWith("[")) {
            log.error("[PendingFollowupScheduler] openId dang la object/map/list='{}', khong gui duoc. Can parse dung truong id.", openId);
            return;
        }

        log.info("[PendingFollowupScheduler] Gui tin nhan den openId='{}', phone={}", openId, phoneNumber);

        String text = String.format("Khách hàng: %s (%s) cha có trao đổi sau 30 phút kể từ khi được ghi nhận. Vui lòng kiểm tra và lin hệ.",
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

    private String getTenantAccessToken() {
        if (tokenStorageService != null) {
            String t = tokenStorageService.getTenantAccessToken();
            if (t != null && !t.isBlank()) return t;
        }
        log.warn("[PendingFollowupScheduler] Khong lay duoc tenant access token");
        return null;
    }

    private String getUserAccessToken() {
        if (tokenStorageService != null) {
            String t = tokenStorageService.getUserAccessToken();
            if (t != null && !t.isBlank()) return t;
        }
        log.warn("[PendingFollowupScheduler] Khong lay duoc user access token");
        return null;
    }
}
