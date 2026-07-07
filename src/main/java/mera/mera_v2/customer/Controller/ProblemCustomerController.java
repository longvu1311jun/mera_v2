package mera.mera_v2.customer.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import mera.mera_v2.customer.DTO.ProblemCustomerView;
import mera.mera_v2.repository.ProblemCustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Trang "Số thả nổi" — khách hàng cảnh báo:
 * CSKH chăm nhiều nhưng chưa chốt (nhóm A) hoặc để lâu chưa lên đơn (nhóm B).
 */
@Controller
public class ProblemCustomerController {

    private static final Logger log = LoggerFactory.getLogger(ProblemCustomerController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final int DEFAULT_MIN_NOTES = 5;
    private static final int DEFAULT_HOURS = 72;
    private static final int DEFAULT_MAX_DAYS = 30;
    private static final int DEFAULT_LIMIT = 1000;
    private static final int MAX_LIMIT = 5000;

    private final ProblemCustomerRepository repository;

    public ProblemCustomerController(ProblemCustomerRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/khach-hang-canh-bao")
    public String problemCustomers(
            @RequestParam(value = "minNotes", required = false, defaultValue = "" + DEFAULT_MIN_NOTES) int minNotes,
            @RequestParam(value = "hours", required = false, defaultValue = "" + DEFAULT_HOURS) int hours,
            @RequestParam(value = "maxDays", required = false, defaultValue = "" + DEFAULT_MAX_DAYS) int maxDays,
            @RequestParam(value = "limit", required = false, defaultValue = "" + DEFAULT_LIMIT) int limit,
            Model model
    ) {
        // Validate biên
        if (minNotes < 1) minNotes = 1;
        if (hours < 1) hours = 1;
        if (maxDays < 1) maxDays = 1;
        if (limit < 1) limit = 1;
        if (limit > MAX_LIMIT) limit = MAX_LIMIT;
        // Cửa sổ nhóm B phải rộng hơn ngưỡng giờ
        if ((long) maxDays * 24 <= hours) {
            maxDays = (hours / 24) + 1;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusHours(hours);
        LocalDateTime oldest = now.minusDays(maxDays);

        model.addAttribute("minNotes", minNotes);
        model.addAttribute("hours", hours);
        model.addAttribute("maxDays", maxDays);
        model.addAttribute("limit", limit);
        model.addAttribute("generatedAt", now.format(DATE_FMT));

        try {
            List<ProblemCustomerView> views = repository.findProblemCustomers(minNotes, threshold, oldest, limit);

            List<ProblemCustomerRow> rows = new ArrayList<>(views.size());
            int groupACount = 0;
            int groupBCount = 0;

            for (ProblemCustomerView v : views) {
                int noteCount = v.getNoteCount() != null ? v.getNoteCount() : 0;
                LocalDateTime insertedAt = v.getInsertedAt();

                // Query đã loại sẵn khách có đơn đang xử lý / thành công (status 11,1,8,9,2,3,16)
                boolean groupA = noteCount >= minNotes;
                boolean groupB = insertedAt != null
                        && insertedAt.isBefore(threshold)
                        && !insertedAt.isBefore(oldest)
                        && noteCount >= 1;

                if (groupA) groupACount++;
                if (groupB) groupBCount++;

                List<String> reasons = new ArrayList<>(2);
                if (groupA) reasons.add("Chăm " + noteCount + " ghi chú nhưng chưa có đơn xử lý");
                if (groupB) reasons.add("Quá " + hours + "h chưa có đơn xử lý");

                ProblemCustomerRow row = new ProblemCustomerRow();
                row.setCustomerId(v.getCustomerId());
                row.setName(v.getName());
                row.setPhone(v.getPhone());
                row.setNoteCount(noteCount);
                row.setOrderCount(v.getOrderCount() != null ? v.getOrderCount() : 0);
                row.setSucceedOrderCount(v.getSucceedOrderCount() != null ? v.getSucceedOrderCount() : 0);
                row.setInsertedAt(insertedAt != null ? insertedAt.format(DATE_FMT) : "—");
                row.setLastNoteAt(v.getLastNoteAt() != null ? v.getLastNoteAt().format(DATE_FMT) : "—");
                row.setGroupA(groupA);
                row.setGroupB(groupB);
                row.setReason(String.join(" · ", reasons));
                rows.add(row);
            }

            model.addAttribute("rows", rows);
            model.addAttribute("total", rows.size());
            model.addAttribute("groupACount", groupACount);
            model.addAttribute("groupBCount", groupBCount);
            model.addAttribute("capped", rows.size() >= limit);
        } catch (Exception e) {
            log.error("Lỗi truy vấn khách hàng cảnh báo", e);
            model.addAttribute("rows", List.of());
            model.addAttribute("total", 0);
            model.addAttribute("groupACount", 0);
            model.addAttribute("groupBCount", 0);
            model.addAttribute("capped", false);
            model.addAttribute("errorMessage", "Không truy vấn được dữ liệu: " + e.getMessage());
        }

        return "problemCustomers";
    }

    /** Dòng hiển thị trên bảng, đã format sẵn cho template. */
    public static class ProblemCustomerRow {
        private String customerId;
        private String name;
        private String phone;
        private int noteCount;
        private int orderCount;
        private int succeedOrderCount;
        private String insertedAt;
        private String lastNoteAt;
        private boolean groupA;
        private boolean groupB;
        private String reason;

        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public int getNoteCount() { return noteCount; }
        public void setNoteCount(int noteCount) { this.noteCount = noteCount; }
        public int getOrderCount() { return orderCount; }
        public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
        public int getSucceedOrderCount() { return succeedOrderCount; }
        public void setSucceedOrderCount(int succeedOrderCount) { this.succeedOrderCount = succeedOrderCount; }
        public String getInsertedAt() { return insertedAt; }
        public void setInsertedAt(String insertedAt) { this.insertedAt = insertedAt; }
        public String getLastNoteAt() { return lastNoteAt; }
        public void setLastNoteAt(String lastNoteAt) { this.lastNoteAt = lastNoteAt; }
        public boolean isGroupA() { return groupA; }
        public void setGroupA(boolean groupA) { this.groupA = groupA; }
        public boolean isGroupB() { return groupB; }
        public void setGroupB(boolean groupB) { this.groupB = groupB; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
