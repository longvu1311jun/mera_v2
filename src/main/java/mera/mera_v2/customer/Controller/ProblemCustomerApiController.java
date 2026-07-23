package mera.mera_v2.customer.Controller;

import java.util.Map;

import mera.mera_v2.customer.DTO.ProblemCustomerResult;
import mera.mera_v2.customer.Service.ProblemCustomerService;
import mera.mera_v2.customer.Service.ProblemGroupRemovalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API JSON cho trang "Số thả nổi" — <b>phân trang server-side</b>: nhận bộ lọc + nhóm + tìm kiếm +
 * sắp xếp + trang, trả về 1 trang dữ liệu (đọc từ bảng precompute {@code problem_customer_facts}).
 * Không còn tải toàn bộ tập / cap 5000; client chỉ render trang server trả về.
 */
@RestController
public class ProblemCustomerApiController {

    private final ProblemCustomerService service;
    private final ProblemGroupRemovalService removalService;

    public ProblemCustomerApiController(ProblemCustomerService service,
                                        ProblemGroupRemovalService removalService) {
        this.service = service;
        this.removalService = removalService;
    }

    @GetMapping("/api/khach-hang-canh-bao/data")
    public ProblemCustomerResult data(
            @RequestParam(value = "minNotes", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_MIN_NOTES) int minNotes,
            @RequestParam(value = "hours", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_HOURS) int hours,
            @RequestParam(value = "maxDays", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_MAX_DAYS) int maxDays,
            @RequestParam(value = "months", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_STALE_MONTHS) int months,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @RequestParam(value = "group", required = false, defaultValue = "all") String group,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_PAGE_SIZE) int pageSize
    ) {
        return service.computePage(minNotes, hours, maxDays, months, fromDate, toDate,
                group, search, sort, dir, page, pageSize);
    }

    /**
     * Xóa 1 khách khỏi 1 nhóm cảnh báo (chỉ dùng ở trang admin {@code /khach-hang-canh-bao}).
     * A/B/C ghi bản loại trừ bền; D xóa khỏi danh sách Từ chối chăm.
     */
    @PostMapping("/api/khach-hang-canh-bao/remove-group")
    public Map<String, Object> removeGroup(@RequestParam("customerId") String customerId,
                                           @RequestParam("group") String group) {
        try {
            removalService.removeGroup(customerId, group);
            return Map.of("success", true);
        } catch (IllegalArgumentException e) {
            return Map.of("success", false, "errorMessage", e.getMessage());
        } catch (Exception e) {
            return Map.of("success", false, "errorMessage", "Lỗi xóa nhóm: " + e.getMessage());
        }
    }
}
