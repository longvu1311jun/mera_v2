package mera.mera_v2.customer.Controller;

import mera.mera_v2.customer.Service.ProblemCustomerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Trang "Số thả nổi" — khách hàng cảnh báo. Trang được render phía client: controller
 * chỉ trả khung + giá trị filter/phân trang ban đầu cho form; dữ liệu được nạp qua
 * {@code /api/khach-hang-canh-bao/data} (polling + phân trang).
 */
@Controller
public class ProblemCustomerController {

    @GetMapping("/khach-hang-canh-bao")
    public String problemCustomers(
            @RequestParam(value = "minNotes", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_MIN_NOTES) int minNotes,
            @RequestParam(value = "hours", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_HOURS) int hours,
            @RequestParam(value = "maxDays", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_MAX_DAYS) int maxDays,
            @RequestParam(value = "months", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_STALE_MONTHS) int months,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "" + ProblemCustomerService.DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(value = "fromDate", required = false, defaultValue = "") String fromDate,
            @RequestParam(value = "toDate", required = false, defaultValue = "") String toDate,
            Model model
    ) {
        if (minNotes < 1) minNotes = 1;
        if (hours < 1) hours = 1;
        if (maxDays < 1) maxDays = 1;
        if (months < 1) months = 1;
        if (page < 1) page = 1;

        model.addAttribute("minNotes", minNotes);
        model.addAttribute("hours", hours);
        model.addAttribute("maxDays", maxDays);
        model.addAttribute("months", months);
        model.addAttribute("page", page);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("pageSizeOptions", ProblemCustomerService.PAGE_SIZE_OPTIONS);
        return "problemCustomers";
    }

    /**
     * Bản chia sẻ cho team sale: không sidebar/menu, không form filter, có tooltip mô tả
     * nhóm cạnh tiêu đề. Dùng chung endpoint dữ liệu + JS với trang admin.
     */
    @GetMapping("/so-tha-noi")
    public String problemCustomersShare(
            @RequestParam(value = "fromDate", required = false, defaultValue = "") String fromDate,
            @RequestParam(value = "toDate", required = false, defaultValue = "") String toDate,
            Model model
    ) {
        model.addAttribute("pageSize", ProblemCustomerService.DEFAULT_PAGE_SIZE);
        model.addAttribute("pageSizeOptions", ProblemCustomerService.PAGE_SIZE_OPTIONS);
        model.addAttribute("maxDays", ProblemCustomerService.DEFAULT_MAX_DAYS);
        model.addAttribute("months", ProblemCustomerService.DEFAULT_STALE_MONTHS);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        return "problemCustomersShare";
    }
}
