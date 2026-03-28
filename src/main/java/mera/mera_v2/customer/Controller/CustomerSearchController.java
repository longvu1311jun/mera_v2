package mera.mera_v2.customer.Controller;

import jakarta.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;
import jakarta.annotation.PreDestroy;
import mera.mera_v2.model.BitableRecord;
import mera.mera_v2.model.UserConfigDto;
import mera.mera_v2.service.BitableService;
import mera.mera_v2.service.LarkTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class CustomerSearchController {

  private static final Logger log = LoggerFactory.getLogger(CustomerSearchController.class);
  private static final String SESSION_USER_CONFIGS = "SESSION_USER_CONFIGS";
  
  // View IDs
  private static final String KHACH_HANG_VIEW_ID = "vew5Ou4Kee";
  private static final String TRAO_DOI_VIEW_ID = "vewNXdsB3K";
  private static final String LICH_HEN_VIEW_ID = "vewRa6d1vZ";

  private final LarkTokenService tokenService;
  private final BitableService bitableService;
  private final ExecutorService executorService;

  public CustomerSearchController(LarkTokenService tokenService, BitableService bitableService) {
    this.tokenService = tokenService;
    this.bitableService = bitableService;
    // Táº¡o thread pool vá»›i 5 threads Ä‘á»ƒ xá»­ lÃ½ cÃ¡c API calls song song
    this.executorService = Executors.newFixedThreadPool(5);
  }

  @GetMapping("/tra_cuu_khach_hang")
  public String searchCustomerPage(Model model, HttpSession session) {
    return "searchCustomer";
  }

  @GetMapping("/api/tra_cuu_khach_hang")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> searchCustomerApi(
      @RequestParam("phoneNumber") String phoneNumber,
      HttpSession session) {
    
    Map<String, Object> response = new HashMap<>();
    
    // Ghi láº¡i thá»i gian báº¯t Ä‘áº§u tra cá»©u
    long startTime = System.currentTimeMillis();
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    String searchTime = dateFormat.format(new Date(startTime));
    
    if (!tokenService.hasToken(session)) {
      response.put("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return ResponseEntity.ok(response);
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      // Láº¥y danh sÃ¡ch user configs tá»« session
      @SuppressWarnings("unchecked")
      List<UserConfigDto> userConfigs = (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      if (userConfigs == null || userConfigs.isEmpty()) {
        response.put("error", "ChÆ°a cÃ³ dá»¯ liá»‡u cáº¥u hÃ¬nh. Vui lÃ²ng vÃ o trang /config Ä‘á»ƒ load dá»¯ liá»‡u trÆ°á»›c.");
        return ResponseEntity.ok(response);
      }

      // âœ… Chia danh sÃ¡ch userConfigs thÃ nh 5 pháº§n Ä‘á»ƒ xá»­ lÃ½ song song
      int totalConfigs = userConfigs.size();
      int chunkSize = Math.max(1, (totalConfigs + 4) / 5); // Chia thÃ nh 5 pháº§n, lÃ m trÃ²n lÃªn
      
      List<List<UserConfigDto>> chunks = new ArrayList<>();
      for (int i = 0; i < totalConfigs; i += chunkSize) {
        int end = Math.min(i + chunkSize, totalConfigs);
        chunks.add(userConfigs.subList(i, end));
      }
      
      // Äáº£m báº£o cÃ³ Ä‘Ãºng 5 chunks (náº¿u Ã­t hÆ¡n thÃ¬ thÃªm empty lists)
      while (chunks.size() < 5) {
        chunks.add(Collections.emptyList());
      }
      
      // AtomicReference Ä‘á»ƒ lÆ°u káº¿t quáº£ tÃ¬m tháº¥y (thread-safe)
      AtomicReference<BitableRecord> foundCustomerRef = new AtomicReference<>();
      AtomicReference<String> foundBaseIdRef = new AtomicReference<>();
      AtomicReference<String> foundLichHenTableIdRef = new AtomicReference<>();
      AtomicReference<String> foundTraoDoiTableIdRef = new AtomicReference<>();
      AtomicReference<String> foundCskhNameRef = new AtomicReference<>();

      // Táº¡o cÃ¡c CompletableFuture Ä‘á»ƒ cháº¡y song song
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      
      for (List<UserConfigDto> chunk : chunks) {
        if (chunk.isEmpty()) continue;
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
          // Náº¿u Ä‘Ã£ tÃ¬m tháº¥y á»Ÿ thread khÃ¡c, dá»«ng láº¡i
          if (foundCustomerRef.get() != null) {
//            System.out.println(foundCustomerRef.toString());
            return;
          }
          
          for (UserConfigDto userConfig : chunk) {
            // Náº¿u Ä‘Ã£ tÃ¬m tháº¥y á»Ÿ thread khÃ¡c, dá»«ng láº¡i
            if (foundCustomerRef.get() != null) {
              break;
            }
            
            String baseId = userConfig.getBaseId();
            String khachHangTableId = userConfig.getKhachHangTableId();

            if (baseId == null || baseId.isBlank() || khachHangTableId == null || khachHangTableId.isBlank()) {
              continue;
            }

            try {
              List<BitableRecord> customers = bitableService.searchCustomerByPhone(
                  session, baseId, khachHangTableId, phoneNumber, KHACH_HANG_VIEW_ID);

              if (customers != null && !customers.isEmpty()) {
                // Chá»‰ set náº¿u chÆ°a cÃ³ thread nÃ o set (atomic check-and-set)
                if (foundCustomerRef.compareAndSet(null, customers.get(0))) {
                  foundBaseIdRef.set(baseId);
                  foundLichHenTableIdRef.set(userConfig.getLichHenTableId());
                  foundTraoDoiTableIdRef.set(userConfig.getTraoDoiTableId());
                  // Láº¥y tÃªn CSKH tá»« mapping
                  String cskhName = userConfig.getPosName();
                  if (cskhName == null || cskhName.isBlank()) {
                    cskhName = userConfig.getLarkName();
                  }
                  foundCskhNameRef.set(cskhName);
//                  log.info("âœ… Found customer in baseId: {}, tableId: {}, CSKH: {}", baseId, khachHangTableId, cskhName);
                  break; // TÃ¬m tháº¥y rá»“i, dá»«ng thread nÃ y
                }
              }
            } catch (Exception e) {
              log.warn("Error searching customer in baseId {}: {}", baseId, e.getMessage());
            }
          }
        }, executorService);
        
        futures.add(future);
      }
      
      // Chá» táº¥t cáº£ cÃ¡c thread hoÃ n thÃ nh hoáº·c khi tÃ¬m tháº¥y khÃ¡ch hÃ ng
      try {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        log.warn("Timeout waiting for customer search threads");
      } catch (Exception e) {
        log.error("Error waiting for customer search threads: {}", e.getMessage(), e);
      }
      
      // Láº¥y káº¿t quáº£
      BitableRecord foundCustomer = foundCustomerRef.get();
      String foundBaseId = foundBaseIdRef.get();
      String foundLichHenTableId = foundLichHenTableIdRef.get();
      String foundTraoDoiTableId = foundTraoDoiTableIdRef.get();
      String foundCskhName = foundCskhNameRef.get();

      if (foundCustomer == null) {
        response.put("error", "KhÃ´ng tÃ¬m tháº¥y khÃ¡ch hÃ ng vá»›i sá»‘ Ä‘iá»‡n thoáº¡i: " + phoneNumber);
        return ResponseEntity.ok(response);
      }

      // Láº¥y thÃ´ng tin khÃ¡ch hÃ ng vÃ  format cÃ¡c field phá»©c táº¡p
      Map<String, Object> customerFields = foundCustomer.getFields();
      String customerRecordId = foundCustomer.getRecordId();
      
      formatCustomerFields(customerFields);

      // âœ… Táº¡o cÃ¡c biáº¿n final Ä‘á»ƒ dÃ¹ng trong lambda
      final String finalBaseId = foundBaseId;
      final String finalTraoDoiTableId = foundTraoDoiTableId;
      final String finalLichHenTableId = foundLichHenTableId;
      final String finalCustomerRecordId = customerRecordId;
      final String customerMaKH = (String) customerFields.get("MÃ£ KH");
      final HttpSession finalSession = session;

      // âœ… DÃ¹ng Ä‘a tiáº¿n trÃ¬nh Ä‘á»ƒ cháº¡y song song 2 API: trao Ä‘á»•i vÃ  lá»‹ch háº¹n
      CompletableFuture<List<Map<String, Object>>> traoDoiFuture = CompletableFuture.supplyAsync(() -> {
        List<Map<String, Object>> traoDoiList = new ArrayList<>();
        if (finalTraoDoiTableId != null && !finalTraoDoiTableId.isBlank()) {
          try {
            List<BitableRecord> records = bitableService.searchRecordsByCustomerId(
                finalSession, finalBaseId, finalTraoDoiTableId, finalCustomerRecordId,
                List.of("KhÃ¡ch HÃ ng", "Ná»™i dung", "NgÃ y"), TRAO_DOI_VIEW_ID);
            for (BitableRecord r : records) {
              Map<String, Object> item = new HashMap<>();
              item.put("customerId", customerMaKH != null ? customerMaKH : finalCustomerRecordId);
              item.put("date", formatDate(extractFieldValue(r.getFields(), "NgÃ y")));
              item.put("content", extractFieldValue(r.getFields(), "Ná»™i dung"));
              traoDoiList.add(item);
            }
//            log.info("âœ… Loaded {} trao doi records", traoDoiList.size());
          } catch (Exception e) {
            log.warn("Error loading trao doi: {}", e.getMessage());
          }
        }
        return traoDoiList;
      }, executorService);

      CompletableFuture<List<Map<String, Object>>> lichHenFuture = CompletableFuture.supplyAsync(() -> {
        List<Map<String, Object>> lichHenList = new ArrayList<>();
        if (finalLichHenTableId != null && !finalLichHenTableId.isBlank()) {
          try {
            List<BitableRecord> records = bitableService.searchRecordsByCustomerId(
                finalSession, finalBaseId, finalLichHenTableId, finalCustomerRecordId,
                List.of("KhÃ¡ch HÃ ng", "CÃ´ng Viá»‡c", "NgÃ y Báº¯t Äáº§u", "Tráº¡ng ThÃ¡i", "NgÃ y Káº¿t ThÃºc"), LICH_HEN_VIEW_ID);
            for (BitableRecord r : records) {
              Map<String, Object> item = new HashMap<>();
              item.put("customerId", customerMaKH != null ? customerMaKH : finalCustomerRecordId);
              item.put("task", extractFieldValue(r.getFields(), "CÃ´ng Viá»‡c"));
              item.put("status", extractStatusText(r.getFields(), "Tráº¡ng ThÃ¡i"));
              item.put("start", formatDate(extractFieldValue(r.getFields(), "NgÃ y Báº¯t Äáº§u")));
              item.put("end", formatDate(extractFieldValue(r.getFields(), "NgÃ y Káº¿t ThÃºc")));
              lichHenList.add(item);
            }
//            log.info("âœ… Loaded {} lich hen records", lichHenList.size());
          } catch (Exception e) {
            log.warn("Error loading lich hen: {}", e.getMessage());
          }
        }
        return lichHenList;
      }, executorService);

      // Chá» cáº£ 2 API hoÃ n thÃ nh (tá»‘i Ä‘a 30 giÃ¢y)
      try {
        CompletableFuture.allOf(traoDoiFuture, lichHenFuture).get(30, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        log.error("Timeout waiting for API calls: {}", e.getMessage());
        response.put("error", "Timeout khi táº£i dá»¯ liá»‡u. Vui lÃ²ng thá»­ láº¡i.");
        return ResponseEntity.ok(response);
      } catch (Exception e) {
        log.error("Error waiting for API calls: {}", e.getMessage(), e);
        response.put("error", "Lá»—i khi táº£i dá»¯ liá»‡u: " + e.getMessage());
        return ResponseEntity.ok(response);
      }
      
      List<Map<String, Object>> traoDoiList = traoDoiFuture.get();
      List<Map<String, Object>> lichHenList = lichHenFuture.get();

      // Build response
      List<Map<String, Object>> customersList = new ArrayList<>();
      Map<String, Object> customerData = new HashMap<>();
      customerData.put("id", customerFields.get("MÃ£ KH"));
      customerData.put("name", customerFields.get("TÃªn khÃ¡ch hÃ ng"));
      customerData.put("phone", customerFields.get("Äiá»‡n thoáº¡i"));
      customerData.put("address", customerFields.get("Äá»‹a chá»‰"));
      customerData.put("note", customerFields.get("TÃªn Liá»‡u TrÃ¬nh"));
      customerData.put("benhNen", customerFields.get("Bá»‡nh ná»n"));
      customerData.put("cskh", foundCskhName != null ? foundCskhName : "-");
      customersList.add(customerData);

      response.put("customers", customersList);
      response.put("notes", traoDoiList);
      response.put("appointments", lichHenList);

      // TÃ­nh thá»i gian pháº£n há»“i
      long endTime = System.currentTimeMillis();
      long responseTime = endTime - startTime;
      response.put("searchTime", searchTime);
      response.put("responseTime", responseTime);
      response.put("responseTimeFormatted", String.format("%.2f giÃ¢y", responseTime / 1000.0));

    } catch (Exception e) {
      log.error("Error searching customer: {}", e.getMessage(), e);
      response.put("error", "Lá»—i khi tÃ¬m kiáº¿m khÃ¡ch hÃ ng: " + e.getMessage());
      
      // Váº«n thÃªm thá»i gian tra cá»©u ngay cáº£ khi cÃ³ lá»—i
      long endTime = System.currentTimeMillis();
      long responseTime = endTime - startTime;
      response.put("searchTime", searchTime);
      response.put("responseTime", responseTime);
      response.put("responseTimeFormatted", String.format("%.2f giÃ¢y", responseTime / 1000.0));
    }

    return ResponseEntity.ok(response);
  }

  private String extractFieldValue(Map<String, Object> fields, String fieldName) {
    if (fields == null) return "";
    Object value = fields.get(fieldName);
    if (value == null) return "";
    if (value instanceof String) return (String) value;
    if (value instanceof Number) return String.valueOf(value);
    if (value instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) value;
      if (list.isEmpty()) return "";
      Object first = list.get(0);
      if (first instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) first;
        Object text = map.get("text");
        return text != null ? text.toString() : first.toString();
      }
      return first.toString();
    }
    return value.toString();
  }

  /**
   * Extract text tá»« tráº¡ng thÃ¡i object (cÃ³ thá»ƒ lÃ  Map vá»›i structure phá»©c táº¡p)
   */
  private String extractStatusText(Map<String, Object> fields, String fieldName) {
    if (fields == null) return "";
    Object value = fields.get(fieldName);
    if (value == null) return "";
    
    // Náº¿u lÃ  Map (object structure)
    if (value instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) value;
      
      // TÃ¬m trong value array
      Object valueObj = map.get("value");
      if (valueObj instanceof List) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) valueObj;
        if (!list.isEmpty()) {
          Object first = list.get(0);
          if (first instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstMap = (Map<String, Object>) first;
            Object text = firstMap.get("text");
            if (text != null) return text.toString();
          }
        }
      }
      
      // Fallback: tÃ¬m text trá»±c tiáº¿p
      Object text = map.get("text");
      if (text != null) return text.toString();
    }
    
    // Náº¿u lÃ  List
    if (value instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) value;
      if (!list.isEmpty()) {
        Object first = list.get(0);
        if (first instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> map = (Map<String, Object>) first;
          Object text = map.get("text");
          if (text != null) return text.toString();
        }
        return first.toString();
      }
    }
    
    return value.toString();
  }

  /**
   * Format ngÃ y tá»« timestamp (milliseconds) hoáº·c string sang dd/MM/yyyy
   */
  private String formatDate(String dateStr) {
    if (dateStr == null || dateStr.isBlank() || "-".equals(dateStr)) {
      return "-";
    }
    
    try {
      // Thá»­ parse nhÆ° timestamp (milliseconds)
      long timestamp = Long.parseLong(dateStr);
      Date date = new Date(timestamp);
      SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
      return sdf.format(date);
    } catch (NumberFormatException e) {
      // Náº¿u khÃ´ng pháº£i sá»‘, thá»­ parse nhÆ° date string
      try {
        // Thá»­ cÃ¡c format phá»• biáº¿n
        SimpleDateFormat[] formats = {
          new SimpleDateFormat("yyyy-MM-dd"),
          new SimpleDateFormat("yyyy/MM/dd"),
          new SimpleDateFormat("dd/MM/yyyy"),
          new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
          new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        };
        
        for (SimpleDateFormat format : formats) {
          try {
            Date date = format.parse(dateStr);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy");
            return outputFormat.format(date);
          } catch (Exception ignored) {
            // Thá»­ format tiáº¿p theo
          }
        }
      } catch (Exception ignored) {
        // Náº¿u khÃ´ng parse Ä‘Æ°á»£c, tráº£ vá» nguyÃªn báº£n
      }
      
      // Náº¿u khÃ´ng parse Ä‘Æ°á»£c, tráº£ vá» nguyÃªn báº£n
      return dateStr;
    }
  }

  /**
   * Format cÃ¡c field phá»©c táº¡p (list, object) thÃ nh string Ä‘á»ƒ hiá»ƒn thá»‹
   */
  private void formatCustomerFields(Map<String, Object> fields) {
    if (fields == null) return;

    // Format "TÃªn khÃ¡ch hÃ ng" - cÃ³ thá»ƒ lÃ  list of objects
    Object tenKhachHang = fields.get("TÃªn khÃ¡ch hÃ ng");
    if (tenKhachHang instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) tenKhachHang;
      StringBuilder sb = new StringBuilder();
      for (Object item : list) {
        if (item instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> map = (Map<String, Object>) item;
          Object text = map.get("text");
          if (text != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(text);
          }
        } else if (item != null) {
          if (sb.length() > 0) sb.append(", ");
          sb.append(item);
        }
      }
      fields.put("TÃªn khÃ¡ch hÃ ng", sb.length() > 0 ? sb.toString() : "-");
    }

    // Format "Äá»‹a chá»‰" - cÃ³ thá»ƒ lÃ  list of objects
    Object diaChi = fields.get("Äá»‹a chá»‰");
    if (diaChi instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) diaChi;
      StringBuilder sb = new StringBuilder();
      for (Object item : list) {
        if (item instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> map = (Map<String, Object>) item;
          Object text = map.get("text");
          if (text != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(text);
          }
        } else if (item != null) {
          if (sb.length() > 0) sb.append(", ");
          sb.append(item);
        }
      }
      fields.put("Äá»‹a chá»‰", sb.length() > 0 ? sb.toString() : "-");
    }

    // Format "TÃªn Liá»‡u TrÃ¬nh" - cÃ³ thá»ƒ lÃ  list
    Object tenLieuTrinh = fields.get("TÃªn Liá»‡u TrÃ¬nh");
    if (tenLieuTrinh instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) tenLieuTrinh;
      fields.put("TÃªn Liá»‡u TrÃ¬nh", String.join(", ", 
          list.stream().map(Object::toString).toArray(String[]::new)));
    }

    // Format "Bá»‡nh ná»n" - cÃ³ thá»ƒ lÃ  list
    Object benhNen = fields.get("Bá»‡nh ná»n");
    if (benhNen instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) benhNen;
      fields.put("Bá»‡nh ná»n", String.join(", ", 
          list.stream().map(Object::toString).toArray(String[]::new)));
    }
  }

  /**
   * Chuáº©n hÃ³a field text: náº¿u lÃ  list/map cÃ³ {text, type} thÃ¬ láº¥y ra chuá»—i text,
   * trÃ¡nh gá»­i structure phá»©c táº¡p sang báº£ng Ä‘Ã­ch.
   */
  private String extractPlainText(Object value) {
    if (value == null) return null;

    if (value instanceof String s) {
      return s;
    }

    if (value instanceof List<?> list) {
      StringBuilder sb = new StringBuilder();
      for (Object item : list) {
        String part = extractPlainText(item);
        if (part != null && !part.isBlank()) {
          if (!sb.isEmpty()) sb.append(", ");
          sb.append(part);
        }
      }
      return sb.toString();
    }

    if (value instanceof Map<?, ?> map) {
      Object text = map.get("text");
      if (text != null) return text.toString();
      Object name = map.get("name");
      if (name != null) return name.toString();
    }

    return value.toString();
  }

  /**
   * Chuáº©n hÃ³a field Link: bá» key "type", chá»‰ giá»¯ "link" vÃ  "text".
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> normalizeLinkField(Object linkValue) {
    if (linkValue == null) {
      return null;
    }

    if (linkValue instanceof Map<?, ?> rawMap) {
      Map<String, Object> map = new HashMap<>();
      Object link = rawMap.get("link");
      Object text = rawMap.get("text");
      if (link != null) map.put("link", link.toString());
      if (text != null) map.put("text", text.toString());
      return map;
    }

    // Náº¿u chá»‰ lÃ  string thÃ¬ dÃ¹ng cho cáº£ link vÃ  text
    String s = linkValue.toString();
    Map<String, Object> map = new HashMap<>();
    map.put("link", s);
    map.put("text", s);
    return map;
  }

  // ================== Äá»“ng bá»™ "Tá»« chá»‘i chÄƒm" ==================

  /**
   * API ná»™i bá»™: quÃ©t táº¥t cáº£ CSKH, tÃ¬m khÃ¡ch hÃ ng cÃ³ "TÃªn Liá»‡u TrÃ¬nh" chá»©a "Tá»« chá»‘i chÄƒm"
   * vÃ  táº¡o báº£n ghi tÆ°Æ¡ng á»©ng trong báº£ng "Tá»« chá»‘i chÄƒm" Ä‘Ã­ch.
   *
   * - DÃ¹ng: gá»i tá»« browser hoáº·c tool: /api/sync_tu_choi_cham
   * - KhÃ´ng cÃ³ UI riÃªng, chá»‰ tráº£ JSON.
   */
  @GetMapping("/api/sync_tu_choi_cham")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> syncTuChoiCham(HttpSession session) {
    Map<String, Object> result = new HashMap<>();

    if (!tokenService.hasToken(session)) {
      result.put("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return ResponseEntity.ok(result);
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      @SuppressWarnings("unchecked")
      List<UserConfigDto> userConfigs =
          (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      if (userConfigs == null || userConfigs.isEmpty()) {
        result.put("error",
            "ChÆ°a cÃ³ dá»¯ liá»‡u cáº¥u hÃ¬nh. Vui lÃ²ng vÃ o trang /config Ä‘á»ƒ load dá»¯ liá»‡u trÆ°á»›c.");
        return ResponseEntity.ok(result);
      }

      int totalBases = 0;
      int totalFound = 0;
      int totalInserted = 0;
      int totalFailed = 0;

      List<String> insertedPhones = new ArrayList<>();

      for (UserConfigDto userConfig : userConfigs) {
        String baseId = userConfig.getBaseId();
        String khachHangTableId = userConfig.getKhachHangTableId();

        if (baseId == null || baseId.isBlank() || khachHangTableId == null
            || khachHangTableId.isBlank()) {
          continue;
        }

        totalBases++;
        List<BitableRecord> records = bitableService.searchRejectedCareCustomers(
            session, baseId, khachHangTableId, KHACH_HANG_VIEW_ID);

        if (records == null || records.isEmpty()) {
          continue;
        }

        totalFound += records.size();

        for (BitableRecord r : records) {
          Map<String, Object> srcFields = r.getFields();
          if (srcFields == null) continue;

          // Láº¥y sá»‘ Ä‘iá»‡n thoáº¡i Ä‘á»ƒ check trÃ¹ng á»Ÿ báº£ng "Tá»« chá»‘i chÄƒm"
          Object rawPhone = srcFields.get("Äiá»‡n thoáº¡i");
          String phoneStr = (rawPhone != null) ? rawPhone.toString().trim() : "";
          if (!phoneStr.isEmpty()) {
            log.info("Check 'Tá»« chá»‘i chÄƒm' phone={} baseId={} tableId={}", phoneStr, baseId, khachHangTableId);
            boolean exists = bitableService.existsRejectedCareByPhone(session, phoneStr);
            if (exists) {
              continue;
            }
          }

          Map<String, Object> destFields = new HashMap<>();
          destFields.put("MÃ£ KH", srcFields.get("MÃ£ KH"));
          // Chuáº©n hÃ³a cÃ¡c field text: bá» wrapper {text,type}, chá»‰ láº¥y string
          destFields.put("TÃªn khÃ¡ch hÃ ng", extractPlainText(srcFields.get("TÃªn khÃ¡ch hÃ ng")));
          destFields.put("Äá»‹a chá»‰", extractPlainText(srcFields.get("Äá»‹a chá»‰")));
          destFields.put("Tá»‰nh/ThÃ nh phá»‘", srcFields.get("Tá»‰nh/ThÃ nh phá»‘"));
          destFields.put("Äiá»‡n thoáº¡i", srcFields.get("Äiá»‡n thoáº¡i"));
          destFields.put("TÃªn Liá»‡u TrÃ¬nh", srcFields.get("TÃªn Liá»‡u TrÃ¬nh"));
          // Chuáº©n hÃ³a Link: bá» field type, giá»¯ link + text
          destFields.put("Link", normalizeLinkField(srcFields.get("Link")));
          destFields.put("Tuá»•i", srcFields.get("Tuá»•i"));
          destFields.put("Bá»‡nh ná»n", srcFields.get("Bá»‡nh ná»n"));

          Object ngayTao = srcFields.get("NgÃ y táº¡o");
          if (ngayTao == null) {
            ngayTao = System.currentTimeMillis();
          }
          destFields.put("NgÃ y táº¡o", ngayTao);

          // Æ¯u tiÃªn giá»¯ nguyÃªn field "NgÆ°á»i CSKH" tá»« báº£n ghi gá»‘c (chá»©a id dáº¡ng ou_...)
          Object nguoiCskhField = srcFields.get("NgÆ°á»i CSKH");
          if (nguoiCskhField != null) {
            destFields.put("NgÆ°á»i CSKH", nguoiCskhField);
          }

          // Táº¡o báº£n ghi má»›i trong báº£ng "Tá»« chá»‘i chÄƒm"
          try {
            bitableService.createRejectedCareRecord(session, destFields);
            totalInserted++;
            insertedPhones.add(phoneStr);
            log.info("Inserted 'Tá»« chá»‘i chÄƒm' phone={}", phoneStr);
          } catch (Exception ex) {
            totalFailed++;
            log.error("âŒ Failed to insert 'Tá»« chá»‘i chÄƒm' record for phone {}: {}", phoneStr, ex.getMessage());
          }
        }
      }

      result.put("message", "ÄÃ£ Ä‘á»“ng bá»™ xong 'Tá»« chá»‘i chÄƒm'");
      result.put("totalBases", totalBases);
      result.put("totalFound", totalFound);
      result.put("totalInserted", totalInserted);
      result.put("totalFailed", totalFailed);
      result.put("phones", insertedPhones);

    } catch (Exception e) {
      log.error("Error when syncing 'Tá»« chá»‘i chÄƒm': {}", e.getMessage(), e);
      result.put("error", "Lá»—i khi Ä‘á»“ng bá»™ 'Tá»« chá»‘i chÄƒm': " + e.getMessage());
    }

    return ResponseEntity.ok(result);
  }

  /**
   * Shortcut Ä‘á»ƒ cháº¡y sync "Tá»« chá»‘i chÄƒm" trá»±c tiáº¿p trÃªn browser:
   * truy cáº­p /updateTTC sáº½ gá»i láº¡i logic /api/sync_tu_choi_cham vÃ  tráº£ vá» JSON.
   */
  @GetMapping("/updateTTC")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> updateTuChoiCham(HttpSession session) {
    return syncTuChoiCham(session);
  }

  /**
   * API ná»™i bá»™: Ä‘á»“ng bá»™ khÃ¡ch hÃ ng cÃ³ "TÃªn Liá»‡u TrÃ¬nh" chá»©a "Äang chÄƒm"
   * sang báº£ng Ä‘Ã­ch tÆ°Æ¡ng á»©ng.
   *
   * Shortcut: /updateDangCham
   */
  @GetMapping("/api/sync_dang_cham")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> syncDangCham(HttpSession session) {
    Map<String, Object> result = new HashMap<>();

    if (!tokenService.hasToken(session)) {
      result.put("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return ResponseEntity.ok(result);
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      @SuppressWarnings("unchecked")
      List<UserConfigDto> userConfigs =
          (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      if (userConfigs == null || userConfigs.isEmpty()) {
        result.put("error",
            "ChÆ°a cÃ³ dá»¯ liá»‡u cáº¥u hÃ¬nh. Vui lÃ²ng vÃ o trang /config Ä‘á»ƒ load dá»¯ liá»‡u trÆ°á»›c.");
        return ResponseEntity.ok(result);
      }

      int totalBases = 0;
      int totalFound = 0;
      int totalInserted = 0;
      int totalFailed = 0;

      List<String> insertedPhones = new ArrayList<>();

      for (UserConfigDto userConfig : userConfigs) {
        String baseId = userConfig.getBaseId();
        String khachHangTableId = userConfig.getKhachHangTableId();

        if (baseId == null || baseId.isBlank() || khachHangTableId == null
            || khachHangTableId.isBlank()) {
          continue;
        }

        totalBases++;
        List<BitableRecord> records = bitableService.searchDangChamCustomers(
            session, baseId, khachHangTableId, KHACH_HANG_VIEW_ID);

        if (records == null || records.isEmpty()) {
          continue;
        }

        totalFound += records.size();

        for (BitableRecord r : records) {
          Map<String, Object> srcFields = r.getFields();
          if (srcFields == null) continue;

          Object rawPhone = srcFields.get("Äiá»‡n thoáº¡i");
          String phoneStr = (rawPhone != null) ? rawPhone.toString().trim() : "";
          if (!phoneStr.isEmpty()) {
            log.info("Check 'Äang chÄƒm' phone={} baseId={} tableId={}", phoneStr, baseId, khachHangTableId);
            boolean exists = bitableService.existsDangChamByPhone(session, phoneStr);
            if (exists) {
              continue;
            }
          }

          Map<String, Object> destFields = new HashMap<>();
          destFields.put("MÃ£ KH", srcFields.get("MÃ£ KH"));
          destFields.put("TÃªn khÃ¡ch hÃ ng", extractPlainText(srcFields.get("TÃªn khÃ¡ch hÃ ng")));
          destFields.put("Äá»‹a chá»‰", extractPlainText(srcFields.get("Äá»‹a chá»‰")));
          destFields.put("Tá»‰nh/ThÃ nh phá»‘", srcFields.get("Tá»‰nh/ThÃ nh phá»‘"));
          destFields.put("Äiá»‡n thoáº¡i", srcFields.get("Äiá»‡n thoáº¡i"));
          destFields.put("TÃªn Liá»‡u TrÃ¬nh", srcFields.get("TÃªn Liá»‡u TrÃ¬nh"));
          destFields.put("Link", normalizeLinkField(srcFields.get("Link")));
          destFields.put("Tuá»•i", srcFields.get("Tuá»•i"));
          destFields.put("Bá»‡nh ná»n", srcFields.get("Bá»‡nh ná»n"));

          Object ngayTao = srcFields.get("NgÃ y táº¡o");
          if (ngayTao == null) {
            ngayTao = System.currentTimeMillis();
          }
          destFields.put("NgÃ y táº¡o", ngayTao);

          Object nguoiCskhField = srcFields.get("NgÆ°á»i CSKH");
          if (nguoiCskhField != null) {
            destFields.put("NgÆ°á»i CSKH", nguoiCskhField);
          }

          try {
            bitableService.createDangChamRecord(session, destFields);
            totalInserted++;
            insertedPhones.add(phoneStr);
            log.info("Inserted 'Äang chÄƒm' phone={}", phoneStr);
          } catch (Exception ex) {
            totalFailed++;
            log.error("âŒ Failed to insert 'Äang chÄƒm' record for phone {}: {}", phoneStr, ex.getMessage());
          }
        }
      }

      result.put("message", "ÄÃ£ Ä‘á»“ng bá»™ xong 'Äang chÄƒm'");
      result.put("totalBases", totalBases);
      result.put("totalFound", totalFound);
      result.put("totalInserted", totalInserted);
      result.put("totalFailed", totalFailed);
      result.put("phones", insertedPhones);

    } catch (Exception e) {
      log.error("Error when syncing 'Äang chÄƒm': {}", e.getMessage(), e);
      result.put("error", "Lá»—i khi Ä‘á»“ng bá»™ 'Äang chÄƒm': " + e.getMessage());
    }

    return ResponseEntity.ok(result);
  }

  @GetMapping("/updateDangCham")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> updateDangCham(HttpSession session) {
    return syncDangCham(session);
  }

  @PreDestroy
  public void cleanup() {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
      log.info("âœ… ExecutorService shutdown completed");
    }
  }
}


