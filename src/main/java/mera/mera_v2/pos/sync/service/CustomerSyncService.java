package mera.mera_v2.pos.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.Customer;
import mera.mera_v2.entity.CustomerNote;
import mera.mera_v2.entity.CustomerNoteEditHistory;
import mera.mera_v2.pos.sync.client.CustomerApiClient;
import mera.mera_v2.pos.sync.dto.CustomerApiDto;
import mera.mera_v2.pos.sync.dto.CustomerListResponseDto;
import mera.mera_v2.pos.sync.dto.NoteApiDto;
import mera.mera_v2.repository.CustomerNoteEditHistoryRepository;
import mera.mera_v2.repository.CustomerNoteRepository;
import mera.mera_v2.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerSyncService {

    private final CustomerApiClient customerApiClient;
    private final CustomerRepository customerRepository;
    private final CustomerNoteRepository customerNoteRepository;
    private final CustomerNoteEditHistoryRepository editHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            PageOutcome outcome = persistPage(customers);
            insertedCustomers += outcome.insertedCustomers;
            updatedCustomers += outcome.updatedCustomers;
            insertedNotes += outcome.insertedNotes;
            updatedNotes += outcome.updatedNotes;
            insertedEditHistory += outcome.insertedEditHistory;
            skippedNotes += outcome.skippedNotes;

            currentPage++;
            // Defensive: nếu API trả total_pages quá nhỏ nhưng page này đầy → vẫn thử page kế tiếp.
            if (currentPage > totalPages && customers.size() >= size) {
                log.warn("API reported total_pages={} but a full page was returned, checking next page anyway", totalPages);
                totalPages = currentPage;
            }
            // Hard stop: tránh loop vô tận nếu POS API luôn trả đầy page nhưng page_number tăng vẫn không hết.
            if (currentPage > 1000) {
                log.warn("Reached safety limit (1000 pages), stopping to avoid infinite loop");
                break;
            }
        } while (currentPage <= totalPages);

        log.info("Customer sync completed. fetched={}, insertedCustomers={}, updatedCustomers={}, insertedNotes={}, updatedNotes={}, editHistory={}",
                fetched, insertedCustomers, updatedCustomers, insertedNotes, updatedNotes, insertedEditHistory);

        return CustomerSyncResult.builder()
                .totalCustomersFromApi(totalFromApi)
                .fetchedCustomers(fetched)
                .insertedCustomers(insertedCustomers)
                .updatedCustomers(updatedCustomers)
                .insertedNotes(insertedNotes)
                .updatedNotes(updatedNotes)
                .insertedEditHistory(insertedEditHistory)
                .skippedNotes(skippedNotes)
                .build();
    }

    @Transactional
    public PageOutcome persistPage(List<CustomerApiDto> customers) {
        PageOutcome outcome = new PageOutcome();
        if (customers == null || customers.isEmpty()) {
            return outcome;
        }

        List<String> customerIds = new ArrayList<>();
        for (CustomerApiDto c : customers) {
            String id = c.getId() != null ? c.getId() : c.getCustomerId();
            if (id != null && !id.isBlank()) customerIds.add(id);
        }

        Map<String, Customer> existingCustomers = new HashMap<>();
        customerRepository.findAllById(customerIds).forEach(c -> existingCustomers.put(c.getId(), c));

        Map<String, CustomerNote> existingNotesById = new HashMap<>();
        customerNoteRepository.findAllById(
                customers.stream()
                        .flatMap(c -> c.getNotes() == null ? java.util.stream.Stream.empty()
                                : c.getNotes().stream())
                        .map(NoteApiDto::getId)
                        .filter(id -> id != null && !id.isBlank())
                        .toList()
        ).forEach(n -> existingNotesById.put(n.getId(), n));

        Map<String, List<CustomerNote>> notesByCustomer = new HashMap<>();
        if (!customerIds.isEmpty()) {
            for (CustomerNote n : customerNoteRepository.findByCustomerIdIn(customerIds)) {
                notesByCustomer.computeIfAbsent(n.getCustomerId(), k -> new ArrayList<>()).add(n);
            }
        }

        List<Customer> customersToSave = new ArrayList<>();
        List<CustomerNote> notesToSave = new ArrayList<>();
        List<CustomerNoteEditHistory> historyToSave = new ArrayList<>();
        Set<String> noteIdsToDelete = new HashSet<>();

        for (CustomerApiDto dto : customers) {
            String customerId = dto.getId() != null ? dto.getId() : dto.getCustomerId();
            if (customerId == null || customerId.isBlank()) {
                log.warn("Skipping customer without id (name={})", dto.getName());
                continue;
            }

            Customer existing = existingCustomers.get(customerId);
            boolean isNewCustomer = (existing == null);
            Customer customer = existing != null ? existing : new Customer();
            customer.setId(customerId);
            if (dto.getShopId() != null) customer.setShopId(dto.getShopId());
            customer.setName(dto.getName() != null ? dto.getName() : "Unknown");
            customer.setGender(dto.getGender());
            customer.setFbId(dto.getFbId());
            customer.setReferralCode(dto.getReferralCode());

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime insertedAt = parseDateTime(dto.getInsertedAt(), "customer.insertedAt");
            LocalDateTime updatedAt = parseDateTime(dto.getUpdatedAt(), "customer.updatedAt");
            customer.setInsertedAt(insertedAt != null ? insertedAt : (existing != null ? existing.getInsertedAt() : now));
            customer.setUpdatedAt(updatedAt != null ? updatedAt : now);

            customersToSave.add(customer);
            if (isNewCustomer) outcome.insertedCustomers++;
            else outcome.updatedCustomers++;

            List<NoteApiDto> apiNotes = dto.getNotes() != null ? dto.getNotes() : List.of();
            List<CustomerNote> currentDbNotes = notesByCustomer.getOrDefault(customerId, List.of());
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

                CustomerNote existingNote = existingNotesById.get(noteDto.getId());
                boolean isNewNote = (existingNote == null);
                CustomerNote note = existingNote != null ? existingNote : new CustomerNote();
                note.setId(noteDto.getId());
                note.setCustomerId(customerId);
                note.setShopId(dto.getShopId() != null ? dto.getShopId() : 0L);
                note.setOrderId(noteDto.getOrderId());
                if (noteDto.getMessage() != null && !noteDto.getMessage().isBlank()) {
                    note.setMessage(noteDto.getMessage());
                }
                if (note.getMessage() == null && existingNote == null) {
                    log.warn("Note {} has no message and is new - skipping insert", noteDto.getId());
                    outcome.skippedNotes++;
                    continue;
                }

                if (noteDto.getCreatedBy() != null) {
                    note.setCreatedById(noteDto.getCreatedBy().getUid());
                    note.setCreatedByName(noteDto.getCreatedBy().getFbName());
                    note.setCreatedByPancakeId(noteDto.getCreatedBy().getPancakeId());
                    note.setCreatedByToken(noteDto.getCreatedBy().getTokenForBusiness());
                }

                note.setImages(toJson(noteDto.getImages()));
                note.setLinks(toJson(noteDto.getLinks()));

                if (noteDto.getCreatedAt() != null) {
                    note.setCreatedAt(fromEpochMillis(noteDto.getCreatedAt()));
                } else {
                    note.setCreatedAt(existingNote != null ? existingNote.getCreatedAt() : now);
                }
                note.setUpdatedAt(noteDto.getUpdatedAt() != null ? fromEpochMillis(noteDto.getUpdatedAt()) : now);
                note.setRemovedAt(noteDto.getRemovedAt() != null ? fromEpochMillis(noteDto.getRemovedAt()) : null);

                notesToSave.add(note);
                if (isNewNote) outcome.insertedNotes++;
                else outcome.updatedNotes++;

                List<NoteApiDto.NoteEditHistoryApiDto> edits = noteDto.getEditHistory();
                if (edits != null) {
                    for (NoteApiDto.NoteEditHistoryApiDto edit : edits) {
                        CustomerNoteEditHistory hist = new CustomerNoteEditHistory();
                        hist.setId(edit.getId() != null ? edit.getId() : UUID.randomUUID().toString());
                        hist.setNoteId(note.getId());
                        hist.setCreatedAt(edit.getCreatedAt() != null ? edit.getCreatedAt() : 0L);
                        hist.setMessage(edit.getMessage() != null ? edit.getMessage() : "");
                        hist.setImages(toJson(edit.getImages()));
                        if (edit.getCreatedBy() != null) {
                            hist.setCreatedById(edit.getCreatedBy().getUid());
                            hist.setCreatedByName(edit.getCreatedBy().getFbName());
                            hist.setCreatedByPancakeId(edit.getCreatedBy().getPancakeId());
                            hist.setCreatedByToken(edit.getCreatedBy().getTokenForBusiness());
                        }
                        historyToSave.add(hist);
                        outcome.insertedEditHistory++;
                    }
                }
            }

            for (CustomerNote dbNote : currentDbNotes) {
                if (!incomingNoteIds.contains(dbNote.getId())) {
                    noteIdsToDelete.add(dbNote.getId());
                }
            }
        }

        if (!customersToSave.isEmpty()) {
            customerRepository.saveAll(customersToSave);
        }
        if (!noteIdsToDelete.isEmpty()) {
            editHistoryRepository.deleteByNoteIdIn(noteIdsToDelete);
            customerNoteRepository.deleteAllById(noteIdsToDelete);
        }
        if (!notesToSave.isEmpty()) {
            customerNoteRepository.saveAll(notesToSave);
        }
        if (!historyToSave.isEmpty()) {
            editHistoryRepository.saveAll(historyToSave);
        }

        return outcome;
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
    }
}