package mera.mera_v2.pos.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.sync.client.CustomerApiClient;
import mera.mera_v2.pos.sync.dto.CustomerApiDto;
import mera.mera_v2.pos.sync.dto.CustomerListResponseDto;
import mera.mera_v2.pos.sync.dto.NoteApiDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đồng bộ khách hàng + ghi chú + SĐT từ POS API vào pos_db.
 *
 * <p><b>Cách ghi (2 pha, xem {@link #persistPage}):</b></p>
 * <ol>
 *   <li><b>ĐỌC</b> trạng thái hiện có bằng SELECT thường (autocommit, non-locking) — chỉ lấy id/khoá,
 *       không nạp entity.</li>
 *   <li><b>GHI</b> trong một transaction ngắn bằng batch {@code INSERT ... ON DUPLICATE KEY UPDATE}
 *       qua JdbcTemplate (giống OrderSyncService).</li>
 * </ol>
 *
 * <p><b>Vì sao không dùng {@code repository.saveAll}:</b> Customer/CustomerNote/CustomerNoteEditHistory
 * có id tự gán (id từ POS) nên {@code save()} chạy theo kiểu merge — Hibernate SELECT từng entity
 * trước khi INSERT/UPDATE. Một trang 50 khách kèm vài trăm ghi chú và lịch sử sửa = vài trăm
 * round-trip qua Tailscale, trang mất hơn 60 giây (Hikari leak-detection kêu), và suốt thời gian đó
 * transaction giữ X-lock trên các dòng {@code customers} → webhook cập nhật cùng khách bị chờ khoá.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerSyncService {
  private static final Logger log = LoggerFactory.getLogger(CustomerSyncService.class);

    /** Số dòng mỗi lô batchUpdate / mỗi IN-list. */
    private static final int CHUNK = 200;

    /**
     * Chỉ ghi các cột mà Customer API thực sự cung cấp. Không đụng lt_count (job LT tính),
     * order_count/purchased_amount... (đồng bộ đơn hàng ghi), inserted_at chỉ set khi còn NULL.
     */
    private static final String UPSERT_CUSTOMER_SQL = """
        INSERT INTO customers (id, shop_id, name, gender, fb_id, referral_code, inserted_at, updated_at)
        VALUES (:id, :shop_id, :name, :gender, :fb_id, :referral_code, :inserted_at, :updated_at)
        ON DUPLICATE KEY UPDATE
            shop_id = VALUES(shop_id),
            name = VALUES(name),
            gender = VALUES(gender),
            fb_id = VALUES(fb_id),
            referral_code = VALUES(referral_code),
            inserted_at = IFNULL(inserted_at, VALUES(inserted_at)),
            updated_at = VALUES(updated_at)
        """;

    private static final String UPSERT_NOTE_SQL = """
        INSERT INTO customer_notes (id, customer_id, shop_id, order_id, message, created_by_id, created_by_name,
            created_by_pancake_id, created_by_token, images, links, removed_at, created_at, updated_at)
        VALUES (:id, :customer_id, :shop_id, :order_id, :message, :created_by_id, :created_by_name,
            :created_by_pancake_id, :created_by_token, :images, :links, :removed_at, :created_at, :updated_at)
        ON DUPLICATE KEY UPDATE
            customer_id = VALUES(customer_id),
            shop_id = VALUES(shop_id),
            order_id = VALUES(order_id),
            message = VALUES(message),
            created_by_id = VALUES(created_by_id),
            created_by_name = VALUES(created_by_name),
            created_by_pancake_id = VALUES(created_by_pancake_id),
            created_by_token = VALUES(created_by_token),
            images = VALUES(images),
            links = VALUES(links),
            removed_at = VALUES(removed_at),
            updated_at = VALUES(updated_at)
        """;

    private static final String UPSERT_EDIT_HISTORY_SQL = """
        INSERT INTO customer_note_edit_history (id, note_id, created_at, message, images,
            created_by_id, created_by_name, created_by_pancake_id, created_by_token)
        VALUES (:id, :note_id, :created_at, :message, :images,
            :created_by_id, :created_by_name, :created_by_pancake_id, :created_by_token)
        ON DUPLICATE KEY UPDATE
            note_id = VALUES(note_id),
            created_at = VALUES(created_at),
            message = VALUES(message),
            images = VALUES(images),
            created_by_id = VALUES(created_by_id),
            created_by_name = VALUES(created_by_name),
            created_by_pancake_id = VALUES(created_by_pancake_id),
            created_by_token = VALUES(created_by_token)
        """;

    /** UNIQUE uq_cust_phone(customer_id, phone_number): IGNORE để không lỗi khi webhook vừa chèn cùng số. */
    private static final String INSERT_PHONE_SQL = """
        INSERT IGNORE INTO customer_phone_numbers (customer_id, phone_number, is_primary, created_at)
        VALUES (:customer_id, :phone_number, :is_primary, :created_at)
        """;

    private final CustomerApiClient customerApiClient;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** shop_id NOT NULL trong DB nhưng API có bản ghi thiếu shop_id → dùng shop cấu hình (luôn là 1546758). */
    @Value("${pos.api.shop-id}")
    private Long defaultShopId;

    public CustomerSyncResult syncCustomers(String startDate, String endDate, Integer pageSize) {
        int size = pageSize != null && pageSize > 0 ? pageSize : 50;
        log.info("Starting customer sync: startDate={}, endDate={}, pageSize={}", startDate, endDate, size);

        int totalFromApi = 0;
        int fetched = 0;
        int insertedCustomers = 0;
        int updatedCustomers = 0;
        int insertedNotes = 0;
        int updatedNotes = 0;
        int insertedEditHistory = 0;
        int skippedNotes = 0;
        int insertedPhones = 0;

        int totalPages = 1;
        int currentPage = 1;

        do {
            log.info(">>> Fetching customer page {}/{}", currentPage, totalPages);
            CustomerListResponseDto resp;
            try {
                resp = customerApiClient.fetchCustomersPage(startDate, endDate, currentPage, size);
            } catch (Exception e) {
                log.error("Failed to fetch customer page {}: {}", currentPage, e.getMessage());
                break;
            }

            List<CustomerApiDto> customers = resp.getData() != null ? resp.getData() : List.of();
            Integer totalEntries = resp.getTotalEntries() != null ? resp.getTotalEntries() : resp.getTotal();
            if (currentPage == 1) {
                totalFromApi = totalEntries != null ? totalEntries : customers.size();
            }
            if (resp.getTotalPages() != null) {
                totalPages = resp.getTotalPages();
            }
            fetched += customers.size();
            log.info("Customer page {}/{}: fetched {} (total_entries={}, total_pages={})",
                    currentPage, totalPages, customers.size(), totalEntries, resp.getTotalPages());

            if (customers.isEmpty()) {
                log.info("Empty data on page {}, stopping loop", currentPage);
                break;
            }

            long t0 = System.nanoTime();
            PageOutcome outcome = persistPage(customers);
            log.info("Customer page {} persisted in {} ms", currentPage, (System.nanoTime() - t0) / 1_000_000);
            insertedCustomers += outcome.insertedCustomers;
            updatedCustomers += outcome.updatedCustomers;
            insertedNotes += outcome.insertedNotes;
            updatedNotes += outcome.updatedNotes;
            insertedEditHistory += outcome.insertedEditHistory;
            skippedNotes += outcome.skippedNotes;
            insertedPhones += outcome.insertedPhones;

            // API trả total_pages/total_entries chuẩn — chỉ cần tăng page_number đến hết.
            currentPage++;
        } while (currentPage <= totalPages);

        log.info("Customer sync completed. fetched={}, insertedCustomers={}, updatedCustomers={}, insertedNotes={}, updatedNotes={}, editHistory={}, insertedPhones={}",
                fetched, insertedCustomers, updatedCustomers, insertedNotes, updatedNotes, insertedEditHistory, insertedPhones);

        return CustomerSyncResult.builder()
                .totalCustomersFromApi(totalFromApi)
                .fetchedCustomers(fetched)
                .insertedCustomers(insertedCustomers)
                .updatedCustomers(updatedCustomers)
                .insertedNotes(insertedNotes)
                .updatedNotes(updatedNotes)
                .insertedEditHistory(insertedEditHistory)
                .skippedNotes(skippedNotes)
                .insertedPhones(insertedPhones)
                .build();
    }

    /** Ghi một trang khách: pha ĐỌC ngoài transaction, pha GHI trong transaction ngắn. */
    public PageOutcome persistPage(List<CustomerApiDto> customers) {
        PageOutcome outcome = new PageOutcome();
        if (customers == null || customers.isEmpty()) {
            return outcome;
        }

        List<String> customerIds = new ArrayList<>();
        List<String> incomingNoteIdsAll = new ArrayList<>();
        for (CustomerApiDto c : customers) {
            String id = c.getId() != null ? c.getId() : c.getCustomerId();
            if (id != null && !id.isBlank()) customerIds.add(id);
            if (c.getNotes() != null) {
                for (NoteApiDto n : c.getNotes()) {
                    if (n.getId() != null && !n.getId().isBlank()) incomingNoteIdsAll.add(n.getId());
                }
            }
        }

        // ---------- Pha 1: ĐỌC (autocommit, không giữ khoá) ----------
        Set<String> existingCustomerIds = new HashSet<>();
        Set<String> existingNoteIds = new HashSet<>();
        Map<String, Set<String>> dbNoteIdsByCustomer = new HashMap<>();
        Map<String, Set<String>> existingPhonesByCustomer = new HashMap<>();
        Set<String> customersWithPrimaryPhone = new HashSet<>();

        for (List<String> ids : chunks(customerIds)) {
            MapSqlParameterSource p = new MapSqlParameterSource("ids", ids);
            existingCustomerIds.addAll(jdbc.queryForList(
                    "SELECT id FROM customers WHERE id IN (:ids)", p, String.class));
            jdbc.query("SELECT id, customer_id FROM customer_notes WHERE customer_id IN (:ids)", p, (RowCallbackHandler) rs ->
                    dbNoteIdsByCustomer.computeIfAbsent(rs.getString(2), k -> new HashSet<>()).add(rs.getString(1)));
            jdbc.query("SELECT customer_id, phone_number, is_primary FROM customer_phone_numbers WHERE customer_id IN (:ids)",
                    p, (RowCallbackHandler) rs -> {
                        String cid = rs.getString(1);
                        existingPhonesByCustomer.computeIfAbsent(cid, k -> new HashSet<>())
                                .add(normalizePhoneNumber(rs.getString(2)));
                        if (rs.getBoolean(3)) customersWithPrimaryPhone.add(cid);
                    });
        }
        for (List<String> ids : chunks(incomingNoteIdsAll)) {
            existingNoteIds.addAll(jdbc.queryForList(
                    "SELECT id FROM customer_notes WHERE id IN (:ids)",
                    new MapSqlParameterSource("ids", ids), String.class));
        }

        // ---------- Dựng tham số ghi (thuần bộ nhớ) ----------
        List<SqlParameterSource> customerRows = new ArrayList<>();
        List<SqlParameterSource> noteRows = new ArrayList<>();
        List<SqlParameterSource> historyRows = new ArrayList<>();
        List<SqlParameterSource> phoneRows = new ArrayList<>();
        Set<String> noteIdsToDelete = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (CustomerApiDto dto : customers) {
            String customerId = dto.getId() != null ? dto.getId() : dto.getCustomerId();
            if (customerId == null || customerId.isBlank()) {
                log.warn("Skipping customer without id (name={})", dto.getName());
                continue;
            }
            Long shopId = dto.getShopId() != null ? dto.getShopId() : defaultShopId;
            LocalDateTime insertedAt = parseDateTime(dto.getInsertedAt(), "customer.insertedAt");
            LocalDateTime updatedAt = parseDateTime(dto.getUpdatedAt(), "customer.updatedAt");

            customerRows.add(new MapSqlParameterSource()
                    .addValue("id", customerId)
                    .addValue("shop_id", shopId)
                    .addValue("name", dto.getName() != null ? dto.getName() : "Unknown")
                    .addValue("gender", dto.getGender())
                    .addValue("fb_id", dto.getFbId())
                    .addValue("referral_code", dto.getReferralCode())
                    .addValue("inserted_at", toTimestamp(insertedAt != null ? insertedAt : now))
                    .addValue("updated_at", toTimestamp(updatedAt != null ? updatedAt : now)));
            if (existingCustomerIds.contains(customerId)) outcome.updatedCustomers++;
            else outcome.insertedCustomers++;

            // SĐT từ API — số đầu tiên là primary nếu KH chưa có primary
            if (dto.getPhoneNumbers() != null && !dto.getPhoneNumbers().isEmpty()) {
                Set<String> knownPhones = existingPhonesByCustomer.computeIfAbsent(customerId, k -> new HashSet<>());
                boolean hasPrimary = customersWithPrimaryPhone.contains(customerId);
                for (String rawPhone : dto.getPhoneNumbers()) {
                    String normalized = normalizePhoneNumber(rawPhone);
                    if (normalized == null || normalized.isBlank() || knownPhones.contains(normalized)) {
                        continue;
                    }
                    phoneRows.add(new MapSqlParameterSource()
                            .addValue("customer_id", customerId)
                            .addValue("phone_number", normalized)
                            .addValue("is_primary", !hasPrimary)
                            .addValue("created_at", toTimestamp(now)));
                    knownPhones.add(normalized);
                    hasPrimary = true;
                    customersWithPrimaryPhone.add(customerId);
                    outcome.insertedPhones++;
                }
            }

            List<NoteApiDto> apiNotes = dto.getNotes() != null ? dto.getNotes() : List.of();
            Set<String> incomingNoteIds = new HashSet<>();

            for (NoteApiDto noteDto : apiNotes) {
                if (noteDto.getId() == null || noteDto.getId().isBlank()) {
                    outcome.skippedNotes++;
                    continue;
                }
                if (noteDto.getMessage() == null || noteDto.getMessage().isBlank()) {
                    log.warn("Skipping note {} for customer {} - message is empty/null", noteDto.getId(), customerId);
                    outcome.skippedNotes++;
                    continue;
                }
                incomingNoteIds.add(noteDto.getId());

                MapSqlParameterSource note = new MapSqlParameterSource()
                        .addValue("id", noteDto.getId())
                        .addValue("customer_id", customerId)
                        .addValue("shop_id", shopId)
                        .addValue("order_id", noteDto.getOrderId())
                        .addValue("message", noteDto.getMessage())
                        .addValue("images", toJson(noteDto.getImages()))
                        .addValue("links", toJson(noteDto.getLinks()))
                        // created_at chỉ dùng khi INSERT; ON DUPLICATE KEY không ghi đè (POS không đổi created_at)
                        .addValue("created_at", toTimestamp(noteDto.getCreatedAt() != null ? fromEpochMillis(noteDto.getCreatedAt()) : now))
                        .addValue("updated_at", toTimestamp(noteDto.getUpdatedAt() != null ? fromEpochMillis(noteDto.getUpdatedAt()) : now))
                        .addValue("removed_at", toTimestamp(noteDto.getRemovedAt() != null ? fromEpochMillis(noteDto.getRemovedAt()) : null));
                addCreatedBy(note, noteDto.getCreatedBy());
                noteRows.add(note);
                if (existingNoteIds.contains(noteDto.getId())) outcome.updatedNotes++;
                else outcome.insertedNotes++;

                List<NoteApiDto.NoteEditHistoryApiDto> edits = noteDto.getEditHistory();
                if (edits != null) {
                    for (NoteApiDto.NoteEditHistoryApiDto edit : edits) {
                        long createdAt = edit.getCreatedAt() != null ? edit.getCreatedAt() : 0L;
                        // Không có id từ POS → id xác định theo (note, thời điểm) để sync lại không nhân bản dòng
                        String histId = (edit.getId() != null && !edit.getId().isBlank())
                                ? edit.getId() : noteDto.getId() + "_" + createdAt;
                        MapSqlParameterSource hist = new MapSqlParameterSource()
                                .addValue("id", histId)
                                .addValue("note_id", noteDto.getId())
                                .addValue("created_at", createdAt)
                                .addValue("message", edit.getMessage() != null ? edit.getMessage() : "")
                                .addValue("images", toJson(edit.getImages()));
                        addCreatedBy(hist, edit.getCreatedBy());
                        historyRows.add(hist);
                        outcome.insertedEditHistory++;
                    }
                }
            }

            for (String dbNoteId : dbNoteIdsByCustomer.getOrDefault(customerId, Set.of())) {
                if (!incomingNoteIds.contains(dbNoteId)) {
                    noteIdsToDelete.add(dbNoteId);
                }
            }
        }

        // ---------- Pha 2: GHI (transaction ngắn, chỉ batch statement) ----------
        transactionTemplate.executeWithoutResult(status -> {
            batch(UPSERT_CUSTOMER_SQL, customerRows);
            for (List<String> ids : chunks(new ArrayList<>(noteIdsToDelete))) {
                MapSqlParameterSource p = new MapSqlParameterSource("ids", ids);
                jdbc.update("DELETE FROM customer_note_edit_history WHERE note_id IN (:ids)", p);
                jdbc.update("DELETE FROM customer_notes WHERE id IN (:ids)", p);
            }
            batch(UPSERT_NOTE_SQL, noteRows);
            batch(UPSERT_EDIT_HISTORY_SQL, historyRows);
            batch(INSERT_PHONE_SQL, phoneRows);
        });

        return outcome;
    }

    private void batch(String sql, List<SqlParameterSource> rows) {
        for (int i = 0; i < rows.size(); i += CHUNK) {
            List<SqlParameterSource> part = rows.subList(i, Math.min(i + CHUNK, rows.size()));
            jdbc.batchUpdate(sql, part.toArray(new SqlParameterSource[0]));
        }
    }

    private static <T> List<List<T>> chunks(List<T> list) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += CHUNK) {
            out.add(list.subList(i, Math.min(i + CHUNK, list.size())));
        }
        return out;
    }

    private void addCreatedBy(MapSqlParameterSource p, NoteApiDto.CreatedByApiDto by) {
        p.addValue("created_by_id", by != null ? by.getUid() : null);
        p.addValue("created_by_name", by != null ? by.getFbName() : null);
        p.addValue("created_by_pancake_id", by != null ? by.getPancakeId() : null);
        p.addValue("created_by_token", by != null ? by.getTokenForBusiness() : null);
    }

    private static Timestamp toTimestamp(LocalDateTime t) {
        return t != null ? Timestamp.valueOf(t) : null;
    }

    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("[^0-9]", "");
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (Exception ignored) {
        }
        log.warn("Cannot parse datetime '{}' for {}", value, fieldName);
        return null;
    }

    private LocalDateTime fromEpochMillis(Long millis) {
        if (millis == null) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize JSON field: {}", e.getMessage());
            return null;
        }
    }

    public static class PageOutcome {
        public int insertedCustomers;
        public int updatedCustomers;
        public int insertedNotes;
        public int updatedNotes;
        public int insertedEditHistory;
        public int skippedNotes;
        public int insertedPhones;
    }
}
