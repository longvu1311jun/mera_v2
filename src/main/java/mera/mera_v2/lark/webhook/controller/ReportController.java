package mera.mera_v2.lark.webhook.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.config.LarkBaseProperties;
import mera.mera_v2.lark.webhook.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ReportController {

  private final ReportService reportService;
  private final LarkBaseProperties larkProps;

  @GetMapping("/report")
  public String showReport(Model model) {
    try {
      log.info("Accessing /report endpoint");
      
      List<mera.mera_v2.lark.webhook.model.SalesSummary> report = reportService.getCachedReport();
//      log.info("Report size: {}", report != null ? report.size() : 0);
      
      // Đếm số tables có data (tổng mess > 0)
      long tablesWithData = 0;
      if (report != null) {
        tablesWithData = report.stream()
            .filter(s -> s != null && s.getTongMes() > 0)
            .count();
//        log.info("Tables with data: {}/{}", tablesWithData, report.size());
      }
      
      model.addAttribute("report", report);
      model.addAttribute("totalTables", report != null ? report.size() : 0);
      model.addAttribute("tablesWithData", tablesWithData);
      
      // Kiểm tra xem đã có token chưa (từ OAuth callback)
      boolean hasToken = false;
      try {
        hasToken = larkProps.getUserAccessToken() != null && 
                  !larkProps.getUserAccessToken().isBlank() &&
                  larkProps.getAppToken() != null &&
                  !larkProps.getAppToken().isBlank();
//        log.info("Has token: {}, userToken: {}, appToken: {}",
//            hasToken,
//            larkProps.getUserAccessToken() != null ? "set" : "null",
//            larkProps.getAppToken() != null ? "set" : "null");
      } catch (Exception e) {
        log.warn("Error checking token: {}", e.getMessage());
      }
      
      model.addAttribute("hasToken", hasToken);
      
      if (!hasToken) {
        model.addAttribute("message", 
            "Vui lòng đăng nhập tại trang chủ để tự động lấy token và báo cáo.");
      } else if (report == null || report.isEmpty()) {
        model.addAttribute("message", 
            "Chưa có dữ liệu. Hệ thống đang tự động tải dữ liệu từ Lark Base. Vui lòng thử lại sau vài giây.");
        // Trigger refresh nếu có token nhưng chưa có data
        try {
          reportService.refreshReport();
        } catch (Exception e) {
          log.error("Error triggering refresh: {}", e.getMessage());
        }
      }
      
//      log.info("Returning report template, hasToken: {}, reportSize: {}",
//          hasToken, report != null ? report.size() : 0);
      return "report";
      
    } catch (Exception e) {
      log.error("Error in showReport", e);
      model.addAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
      return "report";
    }
  }
}
