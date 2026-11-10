package mera.mera_v2.lark.webhook;

import mera.mera_v2.lark.webhook.service.WebhookCreationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class WebhookCreationLogController {
    private final WebhookCreationLogService logs;

    @GetMapping("/webhook-lark-logs")
    public String page(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "") String order,
                       @RequestParam(defaultValue = "") String status, Model model) {
        if (to == null) to = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (from == null) from = to.minusDays(6);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("order", order);
        model.addAttribute("status", status);
        model.addAttribute("entries", List.of());
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30) {
            model.addAttribute("error", "Chọn khoảng ngày hợp lệ, tối đa 31 ngày.");
        } else {
            try { model.addAttribute("entries", logs.read(from, to, order, status)); }
            catch (IOException e) { model.addAttribute("error", "Không đọc được file log. Kiểm tra quyền thư mục logs/lark-webhook trên máy chủ."); }
        }
        return "webhook-lark-logs";
    }
}
