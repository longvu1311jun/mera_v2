package mera.mera_v2.customer.Controller;

import mera.mera_v2.customer.DTO.ProblemCustomerResult;
import mera.mera_v2.customer.Service.ProblemCustomerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API JSON cho trang "Số thả nổi": trả toàn bộ tập cảnh báo theo bộ lọc.
 * Client cache lại rồi tự phân trang / lọc nhóm / tìm kiếm; poll định kỳ để làm mới.
 */
@RestController
public class ProblemCustomerApiController {

    private final ProblemCustomerService service;

    public ProblemCustomerApiController(ProblemCustomerService service) {
        this.service = service;
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
        return service.compute(minNotes, hours, maxDays, months, fromDate, toDate);
    }
}
