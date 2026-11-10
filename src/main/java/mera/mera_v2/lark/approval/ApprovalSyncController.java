package mera.mera_v2.lark.approval;

import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/lark/sync/approvals")
@RequiredArgsConstructor
@Slf4j
public class ApprovalSyncController {
    private final ApprovalSyncService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Job job = new Job(false, 0, List.of());

    public record Job(boolean running, int total, List<ApprovalSyncService.Result> results) {}

    @GetMapping("/templates")
    public List<ApprovalSyncService.Template> templates() {
        return service.templates();
    }

    private static final Pattern APPROVAL_CODE = Pattern.compile(
            "(?i)^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$");

    @PostMapping("/templates")
    public void addTemplate(@RequestParam String approvalCode) {
        String code = approvalCode.strip();
        if (!APPROVAL_CODE.matcher(code).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid approval code");
        }
        try {
            service.addTemplate(code.toUpperCase(Locale.ROOT));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot read Lark approval definition");
        }
    }

    @GetMapping
    public Job status() {
        return job;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public synchronized Job start(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long templateId) {
        if (startDate.isAfter(endDate) || startDate.isBefore(endDate.minusDays(30))
                || endDate.isAfter(LocalDate.now(ApprovalSyncService.LOCAL_ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid approval date range");
        }
        if (job.running()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval sync already running");
        }
        if (templateId == null) {
            service.seedTemplates();
        }
        var templates = service.templates().stream()
                .filter(template -> templateId == null || templateId == template.id()).toList();
        if (templates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No approval templates configured");
        }
        Job started = new Job(true, templates.size(), List.of());
        job = started;
        worker.submit(() -> {
            var results = new ArrayList<ApprovalSyncService.Result>();
            try {
                for (var template : templates) {
                    if (Thread.currentThread().isInterrupted()) break;
                    try {
                        results.add(service.sync(template, startDate, endDate));
                    } catch (RuntimeException ex) {
                        log.warn("Approval sync failed for template {} ({})", template.id(),
                                ex.getClass().getSimpleName());
                        results.add(new ApprovalSyncService.Result(template.name(), 0, 0, 0, "FAILED"));
                    }
                    job = new Job(true, templates.size(), List.copyOf(results));
                }
            } finally {
                job = new Job(false, templates.size(), List.copyOf(results));
            }
        });
        return started;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
