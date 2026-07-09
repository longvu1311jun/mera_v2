package mera.mera_v2.customer.Controller;

import mera.mera_v2.customer.Service.ProblemCustomerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Trang "Khách đã tối ưu" — lưu trữ khách từng nằm trong Số thả nổi và đã lên đơn sau khi được
 * chăm lại. Render phía client: controller chỉ trả khung; dữ liệu nạp qua
 * {@code /api/khach-hang-da-toi-uu/data}.
 */
@Controller
public class OptimizedCustomerController {

    @GetMapping("/khach-hang-da-toi-uu")
    public String optimizedCustomers(Model model) {
        model.addAttribute("pageSize", ProblemCustomerService.DEFAULT_PAGE_SIZE);
        model.addAttribute("pageSizeOptions", ProblemCustomerService.PAGE_SIZE_OPTIONS);
        return "optimizedCustomers";
    }
}
