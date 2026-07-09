package mera.mera_v2.customer.Controller;

import mera.mera_v2.customer.DTO.OptimizedCustomerResult;
import mera.mera_v2.customer.Service.OptimizedCustomerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API JSON cho trang "Khách đã tối ưu": trả toàn bộ tập đã lưu trữ (client tự phân trang/tìm kiếm).
 */
@RestController
public class OptimizedCustomerApiController {

    private final OptimizedCustomerService service;

    public OptimizedCustomerApiController(OptimizedCustomerService service) {
        this.service = service;
    }

    @GetMapping("/api/khach-hang-da-toi-uu/data")
    public OptimizedCustomerResult data() {
        return service.listOptimized();
    }
}
