package mera.mera_v2.customer.Controller;

import java.nio.charset.StandardCharsets;

import mera.mera_v2.customer.DTO.RefusedCareUploadResult;
import mera.mera_v2.customer.Service.RefusedCareService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * Trang quản trị "Từ Chối Chăm" (Nhóm D): tải file Excel chứa SĐT để nạp vào danh sách.
 */
@Controller
public class RefusedCareController {

    private final RefusedCareService service;

    public RefusedCareController(RefusedCareService service) {
        this.service = service;
    }

    @GetMapping("/khach-hang-canh-bao/tu-choi-cham")
    public String page(Model model) {
        model.addAttribute("currentCount", service.count());
        return "refusedCareUpload";
    }

    @PostMapping("/api/khach-hang-canh-bao/tu-choi-cham/upload")
    @ResponseBody
    public RefusedCareUploadResult upload(@RequestParam("file") MultipartFile file) {
        return service.importFromExcel(file);
    }

    @PostMapping("/api/khach-hang-canh-bao/tu-choi-cham/clear")
    @ResponseBody
    public java.util.Map<String, Object> clear() {
        service.clearAll();
        return java.util.Map.of("success", true, "count", 0);
    }

    /** Tải file Excel mẫu (1 cột Số điện thoại). */
    @GetMapping("/api/khach-hang-canh-bao/tu-choi-cham/mau")
    public ResponseEntity<byte[]> sample() {
        return xlsxResponse(service.sampleWorkbook(), "mau-tu-choi-cham.xlsx");
    }

    /**
     * Tải file Excel danh sách SĐT không khớp kèm lý do. Client gửi lại danh sách nhận được
     * từ lần upload (stateless — không giữ report trong memory vì app chạy 2 instance).
     */
    @PostMapping("/api/khach-hang-canh-bao/tu-choi-cham/khong-khop")
    public ResponseEntity<byte[]> downloadUnmatched(
            @RequestBody java.util.List<RefusedCareUploadResult.UnmatchedDetail> details) {
        return xlsxResponse(service.unmatchedWorkbook(details), "sdt-khong-khop-tcc.xlsx");
    }

    private ResponseEntity<byte[]> xlsxResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''"
                                + new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1))
                .body(body);
    }
}
