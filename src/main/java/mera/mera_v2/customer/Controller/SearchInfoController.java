package mera.mera_v2.customer.Controller;

import lombok.RequiredArgsConstructor;
import mera.mera_v2.customer.Service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for POS-based customer and order lookup.
 * This is used by searchInfo.html and demo.html via /api/search-info.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchInfoController {

  private static final Logger log = LoggerFactory.getLogger(SearchInfoController.class);
  private final SearchService searchService;

  /**
   * Search for customer and order info by phone number.
   * Expects phone parameter: /api/search-info?phone=0984859009
   */
  @GetMapping("/search-info")
  public ResponseEntity<?> searchInfo(@RequestParam("phone") String phone, jakarta.servlet.http.HttpSession session) {
    try {
      log.info("🔍 Searching 360 info for phone: {}", phone);
      Map<String, Object> result = searchService.searchCustomer360(phone, session);
      
      if (result == null || result.get("customer") == null) {
        return ResponseEntity.status(HttpStatus.OK)
            .body(Map.of("message", "Không tìm thấy thông tin khách hàng"));
      }
      
      return ResponseEntity.ok(result);
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    } catch (Exception ex) {
      log.error("❌ Error in SearchInfo", ex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Lỗi hệ thống: " + ex.getMessage()));
    }
  }
}
