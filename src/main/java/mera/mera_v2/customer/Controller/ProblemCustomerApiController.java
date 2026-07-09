package mera.mera_v2.customer.Controller;

import mera.mera_v2.customer.DTO.ProblemCustomerResult;
import mera.mera_v2.customer.Service.ProblemCustomerCache;
import mera.mera_v2.customer.Service.ProblemCustomerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API JSON cho trang "Số thả nổi": trả toàn bộ tập cảnh báo theo bộ lọc.
 * Dữ liệu lấy qua {@link ProblemCustomerCache} (cache TTL + stale-while-revalidate) để
 * nhiều user không cùng lúc chạy query nặng. Client tự phân trang / lọc nhóm / tìm kiếm.
 */
@RestController
public class ProblemCustomerApiController {

    private final ProblemCustomerCache cache;

    public ProblemCustomerApiController(ProblemCustomerCache cache) {
        this.cache = cache;
    }

    @GetMapping("/api/khach-hang-canh-bao/data")
    public ProblemCustomerResult data(
            @RequestParam(value = "minNotes", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_MIN_NOTES) int minNotes,
            @RequestParam(value = "hours", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_HOURS) int hours,
            @RequestParam(value = "maxDays", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_MAX_DAYS) int maxDays,
            @RequestParam(value = "months", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_STALE_MONTHS) int months,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate
    ) {
        return cache.get(minNotes, hours, maxDays, months, fromDate, toDate);
    }
}
