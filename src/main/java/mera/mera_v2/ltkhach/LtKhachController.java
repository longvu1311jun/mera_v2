package mera.mera_v2.ltkhach;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LtKhachController {

    /**
     * Trang chính của LT Khách - hiển thị dashboard
     */
    @GetMapping("/lt-khach")
    public String ltKhach() {
        return "lt-khach";
    }

    /**
     * Dashboard - KPI, biểu đồ, top nhân viên
     */
    @GetMapping("/lt-khach/dashboard")
    public String dashboard() {
        return "lt-khach-dashboard-v2";
    }

    /**
     * Report - Báo cáo hiệu suất nhân viên
     */
    @GetMapping("/lt-khach/report")
    public String report() {
        return "lt-khach-report-v2";
    }

    /**
     * Order Details - Chi tiết đơn hàng
     */
    @GetMapping("/lt-khach/order-details")
    public String orderDetails(
            @RequestParam(required = false) String creatorId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false, defaultValue = "true") Boolean zaloOnly,
            @RequestParam(required = false, defaultValue = "false") Boolean useInsertedAtForDataReceived) {
        return "lt-khach-order-details-v2";
    }

    /**
     * Combo Config - Cấu hình combo LT
     */
    @GetMapping("/lt-khach/combo")
    public String combo() {
        return "lt-khach-combo-v2";
    }

    /**
     * Product Substitution - Thuốc thay thế
     */
    @GetMapping("/lt-khach/substitution")
    public String substitution() {
        return "lt-khach-substitution-v2";
    }

}
