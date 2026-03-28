package mera.mera_v2.lark.webhook.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.dto.BaseTableMapping;
import mera.mera_v2.lark.webhook.dto.BitableBatchUpdateRequest;
import mera.mera_v2.lark.webhook.dto.BitableSearchRequest;
import mera.mera_v2.lark.webhook.dto.BitableSearchResponse;
import mera.mera_v2.lark.webhook.service.BaseTableMappingService;
import mera.mera_v2.lark.webhook.service.LarkBitableService;
import mera.mera_v2.lark.token.TokenStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduler tự động xóa các bản ghi cũ (tạo trước 2 tháng)
 * Chạy mỗi ngày vào lúc 2 giờ sáng
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OldRecordsCleanupScheduler {

    private final LarkBitableService bitableService;
    private final BaseTableMappingService baseTableMappingService;
    
    @Autowired(required = false)
    private TokenStorageService tokenStorageService;
    
    private static final String VIEW_ID = "vew5Ou4Kee";
    private static final String CREATED_DATE_FIELD = "Ngày tạo";
    private static final String CSKH_FIELD = "Người CSKH";
    private static final String TU_CHOI_CHAM_FIELD = "Từ chối chăm";
    
    /**
     * Cấu hình thời gian chạy scheduler
     * 
     * CÁC TÙY CHỌN CRON EXPRESSION:
     * 
     * 1. CHẠY THỬ NGAY HÔM NAY (để test):
     *    - Chạy sau 1 phút khi app khởi động: @Scheduled(fixedDelay = 60000, initialDelay = 60000)
     *    - Chạy vào giờ cụ thể hôm nay (ví dụ 15:30): @Scheduled(cron = "0 30 15 * * *")
     *    - Chạy mỗi 5 phút để test: @Scheduled(fixedRate = 300000, initialDelay = 60000)
     * 
     * 2. CHẠY HÀNG THÁNG (khi code ổn):
     *    - Ngày 1 mỗi tháng lúc 2 giờ sáng: @Scheduled(cron = "0 0 2 1 * *")
     *    - Ngày 1 mỗi tháng lúc 3 giờ sáng: @Scheduled(cron = "0 0 3 1 * *")
     * 
     * 3. CHẠY HÀNG NGÀY (hiện tại):
     *    - Mỗi ngày lúc 2 giờ sáng: @Scheduled(cron = "0 0 2 * * *")
     * 
     * CRON FORMAT: giây phút giờ ngày tháng thứ
     * Ví dụ: "0 0 2 1 * *" = giây=0, phút=0, giờ=2, ngày=1, tháng=*, thứ=*
     * 
     * ĐỂ THAY ĐỔI: Comment/uncomment dòng @Scheduled tương ứng bên dưới
     */
    
    // ========== CHẠY THỬ NGAY (TEST) - BỎ COMMENT DÒNG NÀY ĐỂ TEST ==========
//    @Scheduled(fixedDelay = 60000, initialDelay = 60000) // Chạy sau 1 phút khi app start
    // @Scheduled(cron = "0 30 15 * * *") // Chạy vào 15:30 hôm nay (thay đổi giờ/phút theo ý)
    
    // ========== CHẠY HÀNG THÁNG (PRODUCTION) - BỎ COMMENT DÒNG NÀY KHI CODE ỔN ==========
    // @Scheduled(cron = "0 0 2 1 * *") // Ngày 1 mỗi tháng lúc 2 giờ sáng
    
    // ========== CHẠY HÀNG NGÀY (HIỆN TẠI) ==========
    // @Scheduled(cron = "0 0 2 * * *") // Mỗi ngày lúc 2 giờ sáng (đã comment vì đang dùng fixedDelay để test)
    public void cleanupOldRecords() {
        log.info("========================================");
        log.info("🔄 STARTING OLD RECORDS UPDATE - {}", LocalDate.now());
        log.info("   Action: Set '{}' field to null for old records", CSKH_FIELD);
        log.info("   Filter: Records created before check date AND not '{}'", TU_CHOI_CHAM_FIELD);
        log.info("========================================");
        
        try {
            // Kiểm tra token
            String userAccessToken = getUserAccessToken();
            if (userAccessToken == null || userAccessToken.isBlank()) {
                log.error("❌ User access token is not available. Skipping update.");
                return;
            }
            
            // Tính ngày check (ngày 1 của 2 tháng trước)
            LocalDate checkDate = calculateCheckDate();
            long timestamp = convertToTimestamp(checkDate);
            
            log.info("📅 Check date: {} (timestamp: {})", checkDate, timestamp);
            
            // Lấy danh sách mappings
            List<BaseTableMapping> mappings = baseTableMappingService.getAllMappings();
            if (mappings == null || mappings.isEmpty()) {
                log.warn("⚠️ No mappings found. Loading mappings...");
                baseTableMappingService.loadMappings();
                mappings = baseTableMappingService.getAllMappings();
                if (mappings == null || mappings.isEmpty()) {
                    log.error("❌ Still no mappings found after reload. Skipping cleanup.");
                    return;
                }
            }
            
            log.info("📋 Found {} mappings to process", mappings.size());
            
            int totalDeleted = 0;
            int totalErrors = 0;
            List<String> allProcessedPhones = new ArrayList<>(); // Danh sách SDT đã xử lý
            
            // Xử lý từng mapping
            for (BaseTableMapping mapping : mappings) {
                if (mapping.getBaseId() == null || mapping.getBaseId().isBlank()) {
                    log.warn("⚠️ Skipping mapping with null/blank baseId: {}", mapping.getBaseName());
                    continue;
                }
                
                if (mapping.getTableId() == null || mapping.getTableId().isBlank()) {
                    log.warn("⚠️ Skipping mapping with null/blank tableId: {} (base: {})", 
                            mapping.getTableName(), mapping.getBaseName());
                    continue;
                }
                
                try {
                    List<String> processedPhones = updateOldRecordsForMapping(mapping, userAccessToken, timestamp);
                    totalDeleted += processedPhones.size();
                    allProcessedPhones.addAll(processedPhones);
                    log.info("✅ Processed mapping: {} -> {} records updated", 
                            mapping.getBaseName(), processedPhones.size());
                } catch (Exception e) {
                    totalErrors++;
                    log.error("❌ Error processing mapping {} (baseId: {}, tableId: {}): {}", 
                            mapping.getBaseName(), mapping.getBaseId(), mapping.getTableId(), 
                            e.getMessage(), e);
                }
                
                // Delay nhỏ giữa các mapping để tránh rate limiting
                try {
                    Thread.sleep(500); // 500ms delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("⚠️ Interrupted during delay");
                }
            }
            
            log.info("========================================");
            log.info("✅ UPDATE COMPLETED");
            log.info("   Total mappings processed: {}", mappings.size());
            log.info("   Total records updated: {}", totalDeleted);
            log.info("   Total errors: {}", totalErrors);
            log.info("   Total phone numbers processed: {}", allProcessedPhones.size());
            log.info("   Field updated: {} = null", CSKH_FIELD);
            log.info("");
            log.info("📞 DANH SÁCH SỐ ĐIỆN THOẠI ĐÃ XỬ LÝ:");
            if (allProcessedPhones.isEmpty()) {
                log.info("   (Không có số điện thoại nào được xử lý)");
            } else {
                for (int i = 0; i < allProcessedPhones.size(); i++) {
                    log.info("   {}. {}", i + 1, allProcessedPhones.get(i));
                }
            }
            log.info("========================================");
            
        } catch (Exception e) {
            log.error("❌ Fatal error during cleanup: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Tính ngày check: ngày 1 của 2 tháng trước
     * Ví dụ: hôm nay là 25/12/2025 -> ngày check là 1/10/2025
     */
    private LocalDate calculateCheckDate() {
        LocalDate now = LocalDate.now();
        // Trừ 2 tháng và set về ngày 1
        LocalDate checkDate = now.minusMonths(2).withDayOfMonth(1);
        return checkDate;
    }
    
    /**
     * Convert LocalDate sang timestamp (milliseconds)
     * Timestamp sẽ là 00:00:00 của ngày đó theo timezone UTC
     */
    private long convertToTimestamp(LocalDate date) {
        ZonedDateTime zonedDateTime = date.atStartOfDay(ZoneId.of("UTC"));
        return zonedDateTime.toInstant().toEpochMilli();
    }
    
    /**
     * Update các bản ghi cũ cho một mapping cụ thể: set "Người CSKH" = null
     * @return Danh sách số điện thoại đã update
     */
    private List<String> updateOldRecordsForMapping(
            BaseTableMapping mapping,
            String userAccessToken,
            long timestamp
    ) throws Exception {
        String baseId = mapping.getBaseId();
        String tableId = mapping.getTableId();
        
        log.info("🔍 Searching old records in: {} (baseId: {}, tableId: {})", 
                mapping.getBaseName(), baseId, tableId);
        
        List<String> processedPhones = new ArrayList<>();
        String pageToken = null;
        
        do {
            // Tạo search request với 2 điều kiện:
            // 1. Ngày tạo < timestamp (trước ngày 1/10)
            // 2. Chưa có "Từ chối chăm" (isNot "Từ chối chăm")
            BitableSearchRequest.Condition dateCondition = BitableSearchRequest.Condition.builder()
                    .fieldName(CREATED_DATE_FIELD)
                    .operator("isLess")
                    .value(List.of("ExactDate", String.valueOf(timestamp)))
                    .build();
            
            BitableSearchRequest.Condition statusCondition = BitableSearchRequest.Condition.builder()
                    .fieldName(TU_CHOI_CHAM_FIELD)
                    .operator("isNot")
                    .value(List.of("Từ chối chăm"))
                    .build();
            
            BitableSearchRequest.Filter filter = BitableSearchRequest.Filter.builder()
                    .conjunction("and")
                    .conditions(List.of(dateCondition, statusCondition))
                    .build();
            
            BitableSearchRequest searchRequest = BitableSearchRequest.builder()
                    .automaticFields(false)
                    .fieldNames(List.of("Tên Liệu Trình", "Điện thoại", TU_CHOI_CHAM_FIELD))
                    .viewId(VIEW_ID)
                    .pageSize(500)
                    .pageToken(pageToken)
                    .filter(filter)
                    .build();
            
            // Search records
            BitableSearchResponse response = bitableService.searchRecords(
                    baseId, tableId, userAccessToken, searchRequest);
            
            if (response == null || response.getData() == null 
                    || response.getData().getItems() == null 
                    || response.getData().getItems().isEmpty()) {
                log.debug("   No more records found");
                break;
            }
            
            List<BitableSearchResponse.SearchItem> items = response.getData().getItems();
            log.info("   Found {} records in this page", items.size());
            
            // Chuẩn bị danh sách records để batch update
            List<BitableBatchUpdateRequest.UpdateRecord> updateRecords = new ArrayList<>();
            List<String> phoneNumbersInBatch = new ArrayList<>();
            
            // Thu thập records để batch update
            for (BitableSearchResponse.SearchItem item : items) {
                String recordId = item.getRecordId();
                if (recordId == null || recordId.isBlank()) {
                    continue;
                }
                
                // Lấy số điện thoại từ fields
                String phoneNumber = extractPhoneNumber(item.getFields());
                
                // Tạo update record với field "Người CSKH" = null
                Map<String, Object> fields = new HashMap<>();
                fields.put(CSKH_FIELD, null);
                
                BitableBatchUpdateRequest.UpdateRecord updateRecord = 
                        BitableBatchUpdateRequest.UpdateRecord.builder()
                        .recordId(recordId)
                        .fields(fields)
                        .build();
                
                updateRecords.add(updateRecord);
                phoneNumbersInBatch.add(phoneNumber != null ? phoneNumber : "(Không có SDT)");
            }
            
            // Batch update nếu có records
            if (!updateRecords.isEmpty()) {
                try {
                    BitableBatchUpdateRequest batchUpdateRequest = 
                            BitableBatchUpdateRequest.builder()
                            .records(updateRecords)
                            .build();
                    
                    bitableService.batchUpdateRecords(baseId, tableId, userAccessToken, batchUpdateRequest);
                    
                    // Log từng record đã update
                    for (int i = 0; i < updateRecords.size(); i++) {
                        String recordId = updateRecords.get(i).getRecordId();
                        String phoneNumber = phoneNumbersInBatch.get(i);
                        processedPhones.add(phoneNumber);
                        log.info("   ✅ Updated record: {} | SDT: {} | Set {} = null", 
                                recordId, phoneNumber, CSKH_FIELD);
                    }
                    
                    // Delay nhỏ giữa các batch để tránh rate limiting
                    Thread.sleep(500); // 500ms delay
                } catch (Exception e) {
                    log.error("   ❌ Failed to batch update {} records: {}", 
                            updateRecords.size(), e.getMessage());
                    // Tiếp tục với page tiếp theo dù có lỗi
                }
            }
            
            // Kiểm tra có page tiếp theo không
            boolean hasMore = Boolean.TRUE.equals(response.getData().getHasMore());
            pageToken = hasMore ? response.getData().getPageToken() : null;
            
        } while (pageToken != null && !pageToken.isBlank());
        
        log.info("   ✅ Updated {} old records from {} (set {} = null)", 
                processedPhones.size(), mapping.getBaseName(), CSKH_FIELD);
        return processedPhones;
    }
    
    /**
     * Lấy số điện thoại từ fields của record
     */
    private String extractPhoneNumber(java.util.Map<String, Object> fields) {
        if (fields == null) {
            return null;
        }
        
        // Thử các tên field khác nhau
        Object phoneField = fields.get("Điện thoại");
        if (phoneField == null) {
            phoneField = fields.get("Điện Thoại");
        }
        if (phoneField == null) {
            phoneField = fields.get("Số điện thoại");
        }
        
        if (phoneField != null) {
            return phoneField.toString();
        }
        
        return null;
    }
    
    /**
     * Lấy user access token
     */
    private String getUserAccessToken() {
        if (tokenStorageService != null) {
            String token = tokenStorageService.getUserAccessToken();
            if (token != null && !token.isBlank()) {
                return token;
            }
        }
        
        log.warn("⚠️ User access token not found in TokenStorageService");
        return null;
    }
}
