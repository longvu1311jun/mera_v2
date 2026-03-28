package mera.mera_v2.lark.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.annotation.PreDestroy;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.text.SimpleDateFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpSession;
import mera.mera_v2.model.BitableTable;
import mera.mera_v2.model.BitableRecord;
import mera.mera_v2.model.TokenInfo;
import mera.mera_v2.model.UserConfigDto;
import mera.mera_v2.model.EmployeeStatsDto;
import mera.mera_v2.model.PosUser;
import mera.mera_v2.customer.Service.BitableService;
import mera.mera_v2.lark.token.LarkTokenService;
import mera.mera_v2.lark.wiki.LarkWikiService;
import mera.mera_v2.customer.Service.PosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.HashMap;

@Controller
public class authenController {

  private static final Logger log = LoggerFactory.getLogger(authenController.class);

  @Value("${lark.app-id}")
  private String appId;

  @Value("${lark.redirect-uri}")
  private String redirectUri;
 
  @Value("${pos.base-url}")
  private String posBaseUrl;

  @Value("${pos.api-key}")
  private String posApiKey;

  private final LarkTokenService tokenService;
  private final PosService posService;
  private final LarkWikiService larkWikiService;
  private final BitableService bitableService;
  private final ExecutorService executorService;

  public authenController(LarkTokenService tokenService, PosService posService, LarkWikiService larkWikiService,
      BitableService bitableService) {
    this.tokenService = tokenService;
    this.posService = posService;
    this.larkWikiService = larkWikiService;
    this.bitableService = bitableService;
    // Táº¡o thread pool vá»›i 5 threads Ä‘á»ƒ xá»­ lÃ½ stats song song
    this.executorService = Executors.newFixedThreadPool(5);
  }

  @PreDestroy
  public void destroy() {
    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  @GetMapping("/")
  public String home(Model model, HttpSession session) {
    String baseUrl = "https://open.larksuite.com/open-apis/authen/v1/index";

    String authUrl = baseUrl
        + "?app_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&state=" + URLEncoder.encode("xyz", StandardCharsets.UTF_8);
    model.addAttribute("authUrl", authUrl);

    if (tokenService.hasToken(session)) {
      TokenInfo token = tokenService.getCurrentToken(session);
      model.addAttribute("isAuthenticated", true);
      model.addAttribute("tokenExpiresAt", token.getExpiresAt());
    } else {
      model.addAttribute("isAuthenticated", false);
    }

    return "index";
  }

  @GetMapping("/admin")
  public String index(Model model, HttpSession session) {
    String baseUrl = "https://open.larksuite.com/open-apis/authen/v1/index";

    String authUrl = baseUrl
        + "?app_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&state=" + URLEncoder.encode("xyz", StandardCharsets.UTF_8);
    model.addAttribute("authUrl", authUrl);

    if (tokenService.hasToken(session)) {
      TokenInfo token = tokenService.getCurrentToken(session);
      model.addAttribute("isAuthenticated", true);
      model.addAttribute("tokenExpiresAt", token.getExpiresAt());
    } else {
      model.addAttribute("isAuthenticated", false);
    }

    return "index";
  }

  @GetMapping("/oauth/callback")
  public String oauthCallback(
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(value = "error", required = false) String error,
      HttpSession session,
      RedirectAttributes redirectAttributes) {

    if (error != null) {
      redirectAttributes.addFlashAttribute("error", "Authentication failed: " + error);
      return "redirect:/";
    }

    if (code != null) {
      try {
        TokenInfo tokenInfo = tokenService.exchangeCodeForToken(code, session);
        redirectAttributes.addFlashAttribute("success",
            "Authentication successful! Token expires at: " + tokenInfo.getExpiresAt());
      } catch (Exception e) {
        redirectAttributes.addFlashAttribute("error",
            "Failed to get token: " + e.getMessage());
      }
    }

    return "redirect:/";
  }

  private static final String SESSION_ALL_BASES = "SESSION_ALL_BASES";
  private static final String SESSION_USER_CONFIGS = "SESSION_USER_CONFIGS";
  private static final String SESSION_SALE_TABLES = "SESSION_SALE_TABLES";
  private static final String SESSION_EMPLOYEE_STATS = "SESSION_EMPLOYEE_STATS";
  private static final String SESSION_EMPLOYEE_STATS_FETCHED_AT = "SESSION_EMPLOYEE_STATS_FETCHED_AT";
  // Cache riÃªng cho LastMonth Ä‘á»ƒ khi ngÆ°á»i dÃ¹ng chuyá»ƒn qua láº¡i khÃ´ng pháº£i load láº¡i
  private static final String SESSION_EMPLOYEE_STATS_LAST = "SESSION_EMPLOYEE_STATS_LAST";
  private static final String SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT = "SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT";

  @GetMapping("/config")
  public String config(Model model, HttpSession session) {
    if (tokenService.hasToken(session)) {
      log.info("ðŸ” Checking token status for /config endpoint");
      tokenService.autoRefreshTokenIfNeeded(session);

      TokenInfo token = tokenService.getCurrentToken(session);
      model.addAttribute("hasToken", true);
      model.addAttribute("accessToken", token.getAccessToken());
      model.addAttribute("refreshToken", token.getRefreshToken());
      model.addAttribute("tokenType", token.getTokenType());
      model.addAttribute("expiresIn", token.getExpiresIn());
      model.addAttribute("expiresAt", token.getExpiresAt());
      model.addAttribute("lastUpdated", token.getLastUpdated());
      model.addAttribute("isExpired", token.isExpired());

      @SuppressWarnings("unchecked")
      List<mera.mera_v2.model.LarkNode> cachedBases =
          (List<mera.mera_v2.model.LarkNode>) session.getAttribute(SESSION_ALL_BASES);

      @SuppressWarnings("unchecked")
      List<UserConfigDto> cachedUserConfigs =
          (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      @SuppressWarnings("unchecked")
      List<BitableTable> cachedSaleTables =
          (List<BitableTable>) session.getAttribute(SESSION_SALE_TABLES);

      if (cachedBases != null && cachedUserConfigs != null && cachedSaleTables != null) {
        log.info("Using cached data from session");
        model.addAttribute("allBases", cachedBases);
        model.addAttribute("userConfigs", cachedUserConfigs);
        model.addAttribute("saleTables", cachedSaleTables);
      } else {
        try {
          loadAndCacheData(session, model);
        } catch (Exception e) {
          log.error("Error loading config data: {}", e.getMessage(), e);
          model.addAttribute("allBases", new ArrayList<>());
          model.addAttribute("userConfigs", new ArrayList<>());
          model.addAttribute("saleTables", new ArrayList<>());
        }
      }
    } else {
      model.addAttribute("hasToken", false);
      model.addAttribute("allBases", new ArrayList<>());
      model.addAttribute("userConfigs", new ArrayList<>());
      model.addAttribute("saleTables", new ArrayList<>());
    }
    return "config";
  }

  @PostMapping("/config/refresh")
  public String refreshData(HttpSession session, RedirectAttributes redirectAttributes) {
    if (!tokenService.hasToken(session)) {
      redirectAttributes.addFlashAttribute("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return "redirect:/config";
    }

    try {
      TokenInfo token = tokenService.getCurrentToken(session);
      if (token != null && token.isExpired()) {
        try {
          tokenService.refreshToken(session);
          log.info("Token refreshed successfully before loading data");
        } catch (Exception tokenError) {
          log.error("Failed to refresh expired token: {}", tokenError.getMessage());
          redirectAttributes.addFlashAttribute("error",
              "Token Ä‘Ã£ háº¿t háº¡n vÃ  khÃ´ng thá»ƒ lÃ m má»›i. Vui lÃ²ng <a href='/'>Ä‘Äƒng nháº­p láº¡i</a>");
          return "redirect:/config";
        }
      }

      session.removeAttribute(SESSION_ALL_BASES);
      session.removeAttribute(SESSION_USER_CONFIGS);
      session.removeAttribute(SESSION_SALE_TABLES);

      Model model = new org.springframework.ui.ExtendedModelMap();
      loadAndCacheData(session, model);

      redirectAttributes.addFlashAttribute("success", "ÄÃ£ lÃ m má»›i dá»¯ liá»‡u thÃ nh cÃ´ng!");
    } catch (Exception e) {
      log.error("Error refreshing data: {}", e.getMessage(), e);
      String errorMsg = e.getMessage();
      if (errorMsg != null && errorMsg.contains("invalid tenant access token")) {
        redirectAttributes.addFlashAttribute("error",
            "Token khÃ´ng há»£p lá»‡. Vui lÃ²ng <a href='/'>Ä‘Äƒng nháº­p láº¡i</a>");
      } else {
        redirectAttributes.addFlashAttribute("error", "Lá»—i khi lÃ m má»›i dá»¯ liá»‡u: " + errorMsg);
      }
    }

    return "redirect:/config";
  }

  private void loadAndCacheData(HttpSession session, Model model) throws Exception {
    List<mera.mera_v2.model.LarkNode> allNodes = larkWikiService.getAllNodesWithChildren(session);

    List<PosUser> posUsers = posService.getUsers();
    Map<PosUser, mera.mera_v2.model.LarkNode> matchedMap =
        larkWikiService.matchUsersWithNodes(posUsers, session);

    List<UserConfigDto> userConfigs = new ArrayList<>();
    for (PosUser posUser : posUsers) {
      mera.mera_v2.model.LarkNode matchedNode = matchedMap.get(posUser);
      UserConfigDto userConfig = new UserConfigDto(posUser, matchedNode);
      
      // âœ… Láº¥y Table ID cho ba báº£ng: KhÃ¡ch HÃ ng, Lá»‹ch Háº¹n, Trao Äá»•i
      String baseId = userConfig.getBaseId();
      if (baseId != null && !baseId.isBlank()) {
        try {
          List<BitableTable> tables = bitableService.getTablesByBaseId(session, baseId);
          for (BitableTable table : tables) {
            String tableName = table.getName();
            String tableId = table.getTableId();
            
            if (tableName != null && tableId != null) {
              if (tableName.equals("KhÃ¡ch HÃ ng")) {
                userConfig.setKhachHangTableId(tableId);
              } else if (tableName.equals("Lá»‹ch Háº¹n")) {
                userConfig.setLichHenTableId(tableId);
              } else if (tableName.equals("Trao Äá»•i")) {
                userConfig.setTraoDoiTableId(tableId);
              }
            }
          }
        } catch (Exception e) {
          log.warn("Failed to get tables for baseId {}: {}", baseId, e.getMessage());
        }
      }
      
      userConfigs.add(userConfig);
    }

    // âœ… Láº¥y table sale tá»« Bitable API
    List<BitableTable> saleTables = bitableService.getSaleTables(session);

    session.setAttribute(SESSION_ALL_BASES, allNodes);
    session.setAttribute(SESSION_USER_CONFIGS, userConfigs);
    session.setAttribute(SESSION_SALE_TABLES, saleTables);

    model.addAttribute("allBases", allNodes);
    model.addAttribute("userConfigs", userConfigs);
    model.addAttribute("saleTables", saleTables);

    log.info("Data loaded and cached to session");
  }

  @GetMapping("/stats")
  public String stats(
      @RequestParam(value = "customerMonth", required = false, defaultValue = "CurrentMonth") String customerMonth,
      Model model,
      HttpSession session) {
    model.addAttribute("customerMonth", customerMonth);
    return "stats";
  }

  @GetMapping("/api/stats/data")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> getStatsData(
      @RequestParam(value = "customerMonth", required = false, defaultValue = "CurrentMonth") String customerMonth,
      HttpSession session) {
    Map<String, Object> response = new HashMap<>();
    
    if (!tokenService.hasToken(session)) {
      response.put("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return ResponseEntity.ok(response);
    }

    try {
      log.info("ðŸ” Loading stats data for customerMonth: {}", customerMonth);
      tokenService.autoRefreshTokenIfNeeded(session);

      // Validate customerMonth parameter
      if (!customerMonth.equals("CurrentMonth") && !customerMonth.equals("LastMonth")) {
        customerMonth = "CurrentMonth";
      }

      // 1) âœ… Kiá»ƒm tra cache trong session cho cáº£ CurrentMonth vÃ  LastMonth
      @SuppressWarnings("unchecked")
      List<EmployeeStatsDto> cachedStatsCurrent =
          (List<EmployeeStatsDto>) session.getAttribute(SESSION_EMPLOYEE_STATS);
      LocalDateTime fetchedAtCurrent =
          (LocalDateTime) session.getAttribute(SESSION_EMPLOYEE_STATS_FETCHED_AT);

      @SuppressWarnings("unchecked")
      List<EmployeeStatsDto> cachedStatsLast =
          (List<EmployeeStatsDto>) session.getAttribute(SESSION_EMPLOYEE_STATS_LAST);
      LocalDateTime fetchedAtLast =
          (LocalDateTime) session.getAttribute(SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT);

      List<EmployeeStatsDto> cachedStatsToUse = null;
      LocalDateTime fetchedAtToUse = null;
      if (customerMonth.equals("CurrentMonth")) {
        cachedStatsToUse = cachedStatsCurrent;
        fetchedAtToUse = fetchedAtCurrent;
      } else if (customerMonth.equals("LastMonth")) {
        cachedStatsToUse = cachedStatsLast;
        fetchedAtToUse = fetchedAtLast;
      }

      if (cachedStatsToUse != null && fetchedAtToUse != null) {
        log.info("Using cached employee stats from session for {}", customerMonth);
        response.put("statsList", cachedStatsToUse);
        response.put("fetchedAt", fetchedAtToUse.toString());
        response.put("fromCache", true);
        response.put("customerMonth", customerMonth);

        long totalKhach = cachedStatsToUse.stream().mapToLong(EmployeeStatsDto::getTongKhach).sum();
        long totalLich = cachedStatsToUse.stream().mapToLong(EmployeeStatsDto::getTongLich).sum();
        long totalHoanThanh = cachedStatsToUse.stream().mapToLong(EmployeeStatsDto::getHoanThanh).sum();
        response.put("totalKhach", totalKhach);
        response.put("totalLich", totalLich);
        response.put("totalHoanThanh", totalHoanThanh);
        return ResponseEntity.ok(response);
      }

      // 2) âŒ Cache miss -> Láº¥y dá»¯ liá»‡u má»›i
      @SuppressWarnings("unchecked")
      List<UserConfigDto> cachedUserConfigs =
          (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      if (cachedUserConfigs == null) {
        // Náº¿u chÆ°a cÃ³ config, load config trÆ°á»›c
        Model tempModel = new org.springframework.ui.ExtendedModelMap();
        loadAndCacheData(session, tempModel);
        cachedUserConfigs = (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);
      }

      // TÃ­nh toÃ¡n stats vá»›i customerMonth Ä‘Æ°á»£c chá»n
      List<EmployeeStatsDto> statsList = calculateEmployeeStats(session, cachedUserConfigs, customerMonth);
      
      // LÆ°u vÃ o cache theo tá»«ng thÃ¡ng
      long nowMs = Instant.now().toEpochMilli();
      LocalDateTime nowDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault());
      if (customerMonth.equals("CurrentMonth")) {
        session.setAttribute(SESSION_EMPLOYEE_STATS, statsList);
        session.setAttribute(SESSION_EMPLOYEE_STATS_FETCHED_AT, nowDt);
      } else {
        session.setAttribute(SESSION_EMPLOYEE_STATS_LAST, statsList);
        session.setAttribute(SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT, nowDt);
      }
      response.put("fetchedAt", nowDt.toString());
      response.put("fromCache", false);
      
      response.put("statsList", statsList);
      response.put("customerMonth", customerMonth);
      
      // TÃ­nh tá»•ng
      long totalKhach = statsList.stream().mapToLong(EmployeeStatsDto::getTongKhach).sum();
      long totalLich = statsList.stream().mapToLong(EmployeeStatsDto::getTongLich).sum();
      long totalHoanThanh = statsList.stream().mapToLong(EmployeeStatsDto::getHoanThanh).sum();
      response.put("totalKhach", totalKhach);
      response.put("totalLich", totalLich);
      response.put("totalHoanThanh", totalHoanThanh);

    } catch (Exception e) {
      log.error("Error loading stats: {}", e.getMessage(), e);
      response.put("statsList", new ArrayList<EmployeeStatsDto>());
      response.put("error", "Lá»—i khi táº£i thá»‘ng kÃª: " + e.getMessage());
      response.put("customerMonth", customerMonth);
    }

    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/exchanges")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> getExchanges(
      @RequestParam("phone") String phone,
      @RequestParam(value = "customerName", required = false) String customerName,
      @RequestParam(value = "debug", required = false) Boolean debug,
      HttpSession session) {
    Map<String, Object> resp = new HashMap<>();
    if (!tokenService.hasToken(session)) {
      resp.put("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return ResponseEntity.status(401).body(resp);
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);
      @SuppressWarnings("unchecked")
      List<UserConfigDto> userConfigs = (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);
      if (userConfigs == null || userConfigs.isEmpty()) {
        loadAndCacheData(session, new org.springframework.ui.ExtendedModelMap());
        userConfigs = (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);
      }

      // split into 4 parts (but use executorService with 5 threads)
      int total = userConfigs.size();
      int parts = Math.max(1, (total + 3) / 4);
      List<List<UserConfigDto>> chunks = new ArrayList<>();
      for (int i = 0; i < total; i += parts) {
        chunks.add(userConfigs.subList(i, Math.min(i + parts, total)));
      }
      while (chunks.size() < 4) chunks.add(Collections.emptyList());

      List<Map<String, Object>> allResults = Collections.synchronizedList(new ArrayList<>());
      List<Object> rawItemsCollector = Collections.synchronizedList(new ArrayList<>());

      List<CompletableFuture<Void>> futures = new ArrayList<>();
      ObjectMapper mapper = new ObjectMapper();

      for (List<UserConfigDto> chunk : chunks) {
        if (chunk.isEmpty()) continue;
        CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
          for (UserConfigDto uc : chunk) {
            try {
              String baseId = uc.getBaseId();
              String tableId = uc.getTraoDoiTableId();
              if (baseId == null || baseId.isBlank() || tableId == null || tableId.isBlank()) continue;

              String url = "https://open.larksuite.com/open-apis/bitable/v1/apps/" + baseId
                  + "/tables/" + tableId + "/records/search?page_size=500";

              Map<String, Object> body = new HashMap<>();
              body.put("automatic_fields", false);
              body.put("field_names", List.of("KhÃ¡ch HÃ ng", "Ná»™i dung", "PhoneNumber"));
              Map<String, Object> filter = new HashMap<>();
              Map<String, Object> cond = new HashMap<>();
              cond.put("field_name", "PhoneNumber");
              cond.put("operator", "is");
              cond.put("value", List.of(phone));
              filter.put("conditions", List.of(cond));
              filter.put("conjunction", "and");
              body.put("filter", filter);
              body.put("view_id", "vewNXdsB3K");

              HttpHeaders headers = new HttpHeaders();
              headers.setBearerAuth(tokenService.getAccessToken(session, false));
              headers.setContentType(MediaType.APPLICATION_JSON);
              HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);

              RestTemplate rt = new RestTemplate();
              ResponseEntity<String> r = rt.exchange(url, HttpMethod.POST, entity, String.class);
              if (Boolean.TRUE.equals(debug)) {
                try {
                  log.info("Lark records/search raw response for baseId={} tableId={} status={}\n{}", uc.getBaseId(), uc.getTraoDoiTableId(), r.getStatusCode().value(), r.getBody());
                } catch (Exception le) {
                  log.warn("Failed to log raw Lark response: {}", le.getMessage());
                }
              }
              if (r.getStatusCode().is2xxSuccessful() && r.getBody() != null) {
                Map<?, ?> json = mapper.readValue(r.getBody(), Map.class);
                Map<?, ?> data = (Map<?, ?>) json.get("data");
                if (data != null && data.get("items") instanceof List<?>) {
                  List<?> items = (List<?>) data.get("items");
                  for (Object it : items) {
                    if (!(it instanceof Map)) continue;
                    Map<?, ?> item = (Map<?, ?>) it;
                    // collect raw item for debug if needed
                    rawItemsCollector.add(item);
                    Map<?, ?> fields = (Map<?, ?>) item.get("fields");
                    if (fields == null) continue;
                    // extract content text
                    String content = "";
                    Object noidung = fields.get("Ná»™i dung");
                    if (noidung instanceof List<?> l && !l.isEmpty()) {
                      Object first = l.get(0);
                      if (first instanceof Map<?, ?> m && m.get("text") != null) content = String.valueOf(m.get("text"));
                      else content = String.valueOf(first);
                    }
                    // customer name: use provided customerName or empty
                    String custName = customerName != null ? customerName : "";
                    // createdAt
                    Object ngay = fields.get("NgÃ y");
                    Object createdAtValue = null;
                    if (ngay instanceof Number) {
                      createdAtValue = ((Number) ngay).longValue(); // keep timestamp (ms) as number
                    } else if (ngay != null) {
                      // keep original value (string) â€” frontend will attempt to parse/format
                      createdAtValue = String.valueOf(ngay);
                    }
                    // Preserve original "NgÃ y" field in response (do not lose key)
                    Object ngayRawForResponse = ngay;
                    // debug logging
                    try {
                      if (log.isDebugEnabled()) {
                        String fieldsJson = mapper.writeValueAsString(fields);
                        log.debug("Exchange raw fields: baseId={} tableId={} fields={}", uc.getBaseId(), uc.getTraoDoiTableId(), fieldsJson);
                        log.debug("Parsed exchange: content='{}' ngay_raw='{}' createdAtValue='{}'", content, ngay, createdAtValue);
                      }
                    } catch (Exception le) {
                      log.debug("Could not serialize exchange fields for debug: {}", le.getMessage());
                    }
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("content", content);
                    rec.put("customerName", custName);
                    rec.put("createdAt", createdAtValue);
                    // include original "NgÃ y" value under key "NgÃ y" so frontend can use it directly
                    rec.put("NgÃ y", ngayRawForResponse);
                    // include source table info so frontend can create back into the same table
                    rec.put("baseId", baseId);
                    rec.put("tableId", tableId);
                    // extract KhÃ¡ch HÃ ng link ids if present
                    List<String> linkIds = new ArrayList<>();
                    Object khField = fields.get("KhÃ¡ch HÃ ng");
                    if (khField instanceof Map<?, ?> khMap) {
                      Object lrr = khMap.get("link_record_ids");
                      if (lrr instanceof List<?>) {
                        for (Object o : (List<?>) lrr) linkIds.add(String.valueOf(o));
                      }
                    } else if (khField instanceof List<?>) {
                      for (Object o : (List<?>) khField) linkIds.add(String.valueOf(o));
                    } else if (khField != null) {
                      linkIds.add(String.valueOf(khField));
                    }
                    rec.put("linkRecordIds", linkIds);
                    allResults.add(rec);
                  }
                }
              }
            } catch (Exception ex) {
              log.warn("Error fetching exchanges for baseId/tableId {} / {} : {}", uc.getBaseId(), uc.getTraoDoiTableId(), ex.getMessage());
            }
          }
        }, executorService);
        futures.add(f);
      }

      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

      resp.put("code", 0);
      resp.put("data", allResults);
      // always include raw items for debugging purposes
      resp.put("rawItems", rawItemsCollector);
      resp.put("msg", "ok");
      return ResponseEntity.ok(resp);
    } catch (Exception e) {
      log.error("Error in /api/exchanges: {}", e.getMessage(), e);
      resp.put("error", e.getMessage());
      return ResponseEntity.status(500).body(resp);
    }
  }

  /**
   * Search records in a specific Bitable table by phone number.
   * Accepts baseId, tableId, phone (and optional viewId) as request parameters.
   * Returns normalized list of exchanges with fields: content, createdAt, phone, linkRecordIds, NgÃ y (raw).
   */
  @PostMapping("/api/lark/search-by-table")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> searchByTable(
      @RequestParam("baseId") String baseId,
      @RequestParam("tableId") String tableId,
      @RequestParam("phone") String phone,
      @RequestParam(value = "viewId", required = false) String viewId,
      HttpSession session) {
    Map<String, Object> resp = new HashMap<>();
    if (!tokenService.hasToken(session)) {
      resp.put("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return ResponseEntity.status(401).body(resp);
    }
    try {
      tokenService.autoRefreshTokenIfNeeded(session);
      ObjectMapper mapper = new ObjectMapper();
      RestTemplate rt = new RestTemplate();

      String url = "https://open.larksuite.com/open-apis/bitable/v1/apps/" + baseId
          + "/tables/" + tableId + "/records/search?page_size=500";

      Map<String, Object> body = new HashMap<>();
      body.put("automatic_fields", false);
      body.put("field_names", List.of("KhÃ¡ch HÃ ng", "Ná»™i dung", "PhoneNumber", "NgÃ y"));
      Map<String, Object> filter = new HashMap<>();
      Map<String, Object> cond = new HashMap<>();
      cond.put("field_name", "PhoneNumber");
      cond.put("operator", "is");
      cond.put("value", List.of(phone));
      filter.put("conditions", List.of(cond));
      filter.put("conjunction", "and");
      body.put("filter", filter);
      if (viewId != null && !viewId.isBlank()) body.put("view_id", viewId);

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(tokenService.getAccessToken(session, false));
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);

      ResponseEntity<String> r = rt.postForEntity(url, entity, String.class);
      if (!r.getStatusCode().is2xxSuccessful() || r.getBody() == null) {
        resp.put("error", "Lark search failed: " + r.getStatusCode().value());
        return ResponseEntity.status(500).body(resp);
      }

      Map<?, ?> json = mapper.readValue(r.getBody(), Map.class);
      Map<?, ?> data = (Map<?, ?>) json.get("data");
      List<Map<String, Object>> results = new ArrayList<>();
      List<Object> rawItemsCollector = new ArrayList<>();
      if (data != null && data.get("items") instanceof List<?>) {
        List<?> items = (List<?>) data.get("items");
        for (Object it : items) {
          if (!(it instanceof Map)) continue;
          Map<?, ?> item = (Map<?, ?>) it;
          rawItemsCollector.add(item);
          Map<?, ?> fields = (Map<?, ?>) item.get("fields");
          if (fields == null) continue;

          // extract content
          String content = "";
          Object noidung = fields.get("Ná»™i dung");
          if (noidung instanceof List<?> l && !l.isEmpty()) {
            Object first = l.get(0);
            if (first instanceof Map<?, ?> m && m.get("text") != null) content = String.valueOf(m.get("text"));
            else content = String.valueOf(first);
          } else if (noidung != null) {
            content = String.valueOf(noidung);
          }

          // createdAt / NgÃ y
          Object ngay = fields.get("NgÃ y");
          Object createdAtValue = null;
          if (ngay instanceof Number) {
            createdAtValue = ((Number) ngay).longValue();
          } else if (ngay != null) {
            createdAtValue = String.valueOf(ngay);
          }

          // phone
          String parsedPhone = "";
          Object phoneObj = fields.get("PhoneNumber");
          try {
            if (phoneObj instanceof Map<?, ?> p && p.get("value") instanceof List<?>) {
              List<?> vals = (List<?>) p.get("value");
              if (!vals.isEmpty()) parsedPhone = String.valueOf(vals.get(0));
            } else if (phoneObj instanceof List<?>) {
              List<?> vals = (List<?>) phoneObj;
              if (!vals.isEmpty()) parsedPhone = String.valueOf(vals.get(0));
            } else if (phoneObj != null) {
              parsedPhone = String.valueOf(phoneObj);
            }
          } catch (Exception ex) {
            // ignore parsing issues
          }

          // KhÃ¡ch HÃ ng -> link_record_ids
          List<String> linkIds = new ArrayList<>();
          Object kh = fields.get("KhÃ¡ch HÃ ng");
          if (kh instanceof Map<?, ?> khm) {
            Object lrr = khm.get("link_record_ids");
            if (lrr instanceof List<?>) {
              for (Object o : (List<?>) lrr) linkIds.add(String.valueOf(o));
            }
          } else if (kh instanceof List<?>) {
            for (Object o : (List<?>) kh) linkIds.add(String.valueOf(o));
          } else if (kh != null) {
            linkIds.add(String.valueOf(kh));
          }

          Map<String, Object> rec = new HashMap<>();
          rec.put("content", content);
          rec.put("createdAt", createdAtValue);
          rec.put("phone", parsedPhone);
          rec.put("baseId", baseId);
          rec.put("tableId", tableId);
          rec.put("linkRecordIds", linkIds);
          rec.put("NgÃ y", ngay);
          results.add(rec);
        }
      }

      resp.put("code", 0);
      resp.put("data", results);
      resp.put("rawItems", rawItemsCollector);
      resp.put("msg", "ok");
      return ResponseEntity.ok(resp);
    } catch (Exception e) {
      log.error("Error in /api/lark/search-by-table: {}", e.getMessage(), e);
      resp.put("error", e.getMessage());
      return ResponseEntity.status(500).body(resp);
    }
  }

  /**
   * Create a new record in a specified Bitable table.
   * Expects JSON body: { "content": "...", "ngay": 176..., "linkRecordIds": ["recv..."] }
   */
  @PostMapping("/api/lark/create-record")
  @ResponseBody
  @SuppressWarnings("unchecked")
  public ResponseEntity<Map<String, Object>> createLarkRecord(
      @RequestParam("baseId") String baseId,
      @RequestParam("tableId") String tableId,
      @RequestBody Map<String, Object> reqBody,
      HttpSession session) {
    Map<String, Object> resp = new HashMap<>();
    if (!tokenService.hasToken(session)) {
      resp.put("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return ResponseEntity.status(401).body(resp);
    }
    try {
      tokenService.autoRefreshTokenIfNeeded(session);
      ObjectMapper mapper = new ObjectMapper();
      RestTemplate rt = new RestTemplate();

      String url = "https://open.larksuite.com/open-apis/bitable/v1/apps/" + baseId
          + "/tables/" + tableId + "/records?user_id_type=union_id";

      String content = reqBody.get("content") == null ? "" : String.valueOf(reqBody.get("content"));
      Object ngay = reqBody.get("ngay");
      List<String> linkIds = new ArrayList<>();
      Object linksObj = reqBody.get("linkRecordIds");
      if (linksObj instanceof List<?>) {
        for (Object o : (List<?>) linksObj) linkIds.add(String.valueOf(o));
      } else if (linksObj != null) {
        linkIds.add(String.valueOf(linksObj));
      }

      Map<String, Object> body = new HashMap<>();
      Map<String, Object> fields = new HashMap<>();
      fields.put("Ná»™i dung", content);
      if (ngay != null) fields.put("NgÃ y", ngay);
      fields.put("KhÃ¡ch HÃ ng", linkIds);
      body.put("fields", fields);

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(tokenService.getAccessToken(session, false));
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);

      ResponseEntity<String> r = rt.postForEntity(url, entity, String.class);
      if (!r.getStatusCode().is2xxSuccessful() || r.getBody() == null) {
        resp.put("error", "Lark create failed: " + r.getStatusCode().value());
        return ResponseEntity.status(500).body(resp);
      }
      Map<?, ?> created = mapper.readValue(r.getBody(), Map.class);
      resp.put("code", 0);
      resp.put("data", created);
      resp.put("msg", "ok");
      return ResponseEntity.ok(resp);
    } catch (Exception e) {
      log.error("Error in /api/lark/create-record: {}", e.getMessage(), e);
      resp.put("error", e.getMessage());
      return ResponseEntity.status(500).body(resp);
    }
  }

  /**
   * Create a POS customer note via POS API.
   * Expects JSON body: { "customerId": "...", "message": "...", "orderId": "..." }
   */
  @PostMapping("/api/pos/create-note")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> createPosNote(@RequestBody Map<String, Object> req,
      HttpSession session) {
    Map<String, Object> resp = new HashMap<>();
    if (!tokenService.hasToken(session)) {
      resp.put("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return ResponseEntity.status(401).body(resp);
    }
    try {
      tokenService.autoRefreshTokenIfNeeded(session);
      ObjectMapper mapper = new ObjectMapper();
      log.info("Received /api/pos/create-note request body: {}", mapper.writeValueAsString(req));
      String customerId = req.get("customerId") == null ? "" : String.valueOf(req.get("customerId"));
      String message = req.get("message") == null ? "" : String.valueOf(req.get("message"));
      Object orderIdObj = req.get("orderId");
      String orderId = orderIdObj == null ? "" : String.valueOf(orderIdObj);

      if (customerId.isBlank()) {
        resp.put("error", "Missing customerId");
        return ResponseEntity.badRequest().body(resp);
      }

      String url = posBaseUrl;
      if (!url.endsWith("/")) url += "/";
      url += "customers/" + URLEncoder.encode(customerId, StandardCharsets.UTF_8) + "/create_note?api_key=" + URLEncoder.encode(posApiKey, StandardCharsets.UTF_8);

      Map<String, Object> body = new HashMap<>();
      body.put("message", message);
      if (orderId != null && !orderId.isBlank()) body.put("order_id", orderId);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);

      RestTemplate rt = new RestTemplate();
      log.info("Calling POS create_note URL={} body={}", url, mapper.writeValueAsString(body));
      ResponseEntity<String> r = rt.postForEntity(url, entity, String.class);
      log.info("POS create_note response status={} body={}", r.getStatusCode().value(), r.getBody());
      if (!r.getStatusCode().is2xxSuccessful() || r.getBody() == null) {
        resp.put("error", "POS create note failed: " + r.getStatusCode().value());
        return ResponseEntity.status(500).body(resp);
      }
      Map<?, ?> created = mapper.readValue(r.getBody(), Map.class);
      resp.put("code", 0);
      resp.put("data", created);
      resp.put("msg", "ok");
      return ResponseEntity.ok(resp);
    } catch (Exception e) {
      log.error("Error in /api/pos/create-note: {}", e.getMessage(), e);
      resp.put("error", e.getMessage());
      return ResponseEntity.status(500).body(resp);
    }
  }

  @PostMapping("/stats/refresh")
  public String refreshStats(
      @RequestParam(value = "customerMonth", required = false, defaultValue = "CurrentMonth") String customerMonth,
      HttpSession session, 
      RedirectAttributes redirectAttributes) {
    if (!tokenService.hasToken(session)) {
      redirectAttributes.addFlashAttribute("error", "Vui lÃ²ng Ä‘Äƒng nháº­p trÆ°á»›c");
      return "redirect:/stats";
    }

    try {
      // XÃ³a cache
      session.removeAttribute(SESSION_EMPLOYEE_STATS);
      session.removeAttribute(SESSION_EMPLOYEE_STATS_FETCHED_AT);
      session.removeAttribute(SESSION_EMPLOYEE_STATS_LAST);
      session.removeAttribute(SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT);
      
      redirectAttributes.addFlashAttribute("success", "ÄÃ£ lÃ m má»›i dá»¯ liá»‡u thá»‘ng kÃª thÃ nh cÃ´ng!");
    } catch (Exception e) {
      log.error("Error refreshing stats: {}", e.getMessage(), e);
      redirectAttributes.addFlashAttribute("error", "Lá»—i khi lÃ m má»›i dá»¯ liá»‡u: " + e.getMessage());
    }

    return "redirect:/stats?customerMonth=" + customerMonth;
  }

  @GetMapping("/stats/export")
  public ResponseEntity<byte[]> exportStatsToExcel(
      @RequestParam(value = "customerMonth", required = false, defaultValue = "CurrentMonth") String customerMonth,
      HttpSession session) throws IOException {

    if (!tokenService.hasToken(session)) {
      return ResponseEntity.badRequest().build();
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      // Validate customerMonth parameter
      if (!customerMonth.equals("CurrentMonth") && !customerMonth.equals("LastMonth")) {
        customerMonth = "CurrentMonth";
      }

      // Láº¥y stats tá»« cache náº¿u cÃ³ (cho cáº£ CurrentMonth vÃ  LastMonth)
      @SuppressWarnings("unchecked")
      List<EmployeeStatsDto> cachedStatsCurrent =
          (List<EmployeeStatsDto>) session.getAttribute(SESSION_EMPLOYEE_STATS);
      @SuppressWarnings("unchecked")
      List<EmployeeStatsDto> cachedStatsLast =
          (List<EmployeeStatsDto>) session.getAttribute(SESSION_EMPLOYEE_STATS_LAST);

      List<EmployeeStatsDto> statsList = null;
      if (customerMonth.equals("CurrentMonth") && cachedStatsCurrent != null) {
        statsList = cachedStatsCurrent;
      } else if (customerMonth.equals("LastMonth") && cachedStatsLast != null) {
        statsList = cachedStatsLast;
      } else {
        // Náº¿u khÃ´ng cÃ³ cache, tÃ­nh láº¡i vÃ  lÆ°u cache nhÆ° getStatsData
        @SuppressWarnings("unchecked")
        List<UserConfigDto> cachedUserConfigs =
            (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);
        if (cachedUserConfigs == null) {
          Model tempModel = new org.springframework.ui.ExtendedModelMap();
          loadAndCacheData(session, tempModel);
          cachedUserConfigs = (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);
        }
        statsList = calculateEmployeeStats(session, cachedUserConfigs, customerMonth);

        long nowMs = Instant.now().toEpochMilli();
        LocalDateTime nowDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault());
        if (customerMonth.equals("CurrentMonth")) {
          session.setAttribute(SESSION_EMPLOYEE_STATS, statsList);
          session.setAttribute(SESSION_EMPLOYEE_STATS_FETCHED_AT, nowDt);
        } else {
          session.setAttribute(SESSION_EMPLOYEE_STATS_LAST, statsList);
          session.setAttribute(SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT, nowDt);
        }
      }

      if (statsList == null || statsList.isEmpty()) {
        return ResponseEntity.badRequest().build();
      }

      // XÃ¡c Ä‘á»‹nh thÃ¡ng hiá»ƒn thá»‹
      LocalDateTime nowDtLabel = LocalDateTime.now(ZoneId.systemDefault());
      int currentMonthNum = nowDtLabel.getMonthValue();
      int targetMonthNum = "CurrentMonth".equals(customerMonth)
          ? currentMonthNum
          : (currentMonthNum == 1 ? 12 : currentMonthNum - 1);
      String monthLabel = "ThÃ¡ng " + targetMonthNum;

      // Táº¡o Excel
      Workbook workbook = new XSSFWorkbook();
      Sheet sheet = workbook.createSheet("Stats");

      // Style title
      CellStyle titleStyle = workbook.createCellStyle();
      Font titleFont = workbook.createFont();
      titleFont.setBold(true);
      titleFont.setFontHeightInPoints((short) 14);
      titleStyle.setFont(titleFont);
      titleStyle.setAlignment(HorizontalAlignment.CENTER);

      // Header style
      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerFont.setFontHeightInPoints((short) 12);
      headerStyle.setFont(headerFont);
      headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      headerStyle.setBorderBottom(BorderStyle.THIN);
      headerStyle.setBorderTop(BorderStyle.THIN);
      headerStyle.setBorderLeft(BorderStyle.THIN);
      headerStyle.setBorderRight(BorderStyle.THIN);
      headerStyle.setAlignment(HorizontalAlignment.CENTER);

      // Number style
      CellStyle numberStyle = workbook.createCellStyle();
      numberStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0"));

      // Title
      Row titleRow = sheet.createRow(0);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("Thá»‘ng kÃª lá»‹ch háº¹n CSKH " + monthLabel);
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 6));

      // Header
      Row headerRow = sheet.createRow(2);
      String[] headers = {
          "STT", "TÃªn NhÃ¢n ViÃªn", "Tá»•ng KhÃ¡ch", "Tá»•ng Lá»‹ch", "HoÃ n ThÃ nh Muá»™n", "HoÃ n ThÃ nh", "QuÃ¡ Háº¡n"
      };
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data
      int rowNum = 3;
      int stt = 1;
      for (EmployeeStatsDto stat : statsList) {
        Row dataRow = sheet.createRow(rowNum++);
        int col = 0;
        dataRow.createCell(col++).setCellValue(stt++);
        dataRow.createCell(col++).setCellValue(stat.getEmployeeName() != null ? stat.getEmployeeName() : "");

        Cell tongKhachCell = dataRow.createCell(col++);
        tongKhachCell.setCellValue(stat.getTongKhach());
        tongKhachCell.setCellStyle(numberStyle);

        Cell tongLichCell = dataRow.createCell(col++);
        tongLichCell.setCellValue(stat.getTongLich());
        tongLichCell.setCellStyle(numberStyle);

        Cell hoanThanhMuonCell = dataRow.createCell(col++);
        hoanThanhMuonCell.setCellValue(stat.getHoanThanhMuon());
        hoanThanhMuonCell.setCellStyle(numberStyle);

        Cell hoanThanhCell = dataRow.createCell(col++);
        hoanThanhCell.setCellValue(stat.getHoanThanh());
        hoanThanhCell.setCellStyle(numberStyle);

        Cell quaHanCell = dataRow.createCell(col++);
        quaHanCell.setCellValue(stat.getQuaHan());
        quaHanCell.setCellStyle(numberStyle);
      }

      // Auto-size columns
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
        sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 800);
      }

      // Filename
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      String fileName = "report_CSKH_" + monthLabel.replace(" ", "_") + "_" + dateFormat.format(new java.util.Date()) + ".xlsx";

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      workbook.close();

      HttpHeaders responseHeaders = new HttpHeaders();
      responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
      responseHeaders.setContentDispositionFormData("attachment", fileName);

      return ResponseEntity.ok()
          .headers(responseHeaders)
          .body(outputStream.toByteArray());

    } catch (Exception e) {
      log.error("Error exporting stats Excel: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError().build();
    }
  }

  private List<EmployeeStatsDto> calculateEmployeeStats(HttpSession session,
      List<UserConfigDto> userConfigs, String customerMonthRange) throws Exception {
    List<EmployeeStatsDto> statsList = Collections.synchronizedList(new ArrayList<>());

    // customerMonthRange: "CurrentMonth" hoáº·c "LastMonth" - dÃ¹ng cho cáº£ API láº¥y khÃ¡ch hÃ ng vÃ  lá»‹ch háº¹n
    // Cáº£ hai API Ä‘á»u filter theo cÃ¹ng thÃ¡ng Ä‘á»ƒ Ä‘áº£m báº£o tÃ­nh nháº¥t quÃ¡n
    String khachHangTimeRange = customerMonthRange != null ? customerMonthRange : "CurrentMonth";
    String lichHenTimeRange = customerMonthRange != null ? customerMonthRange : "CurrentMonth";

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
    
    // Táº¡o cÃ¡c CompletableFuture Ä‘á»ƒ cháº¡y song song
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    
    for (List<UserConfigDto> chunk : chunks) {
      if (chunk.isEmpty()) continue;
      
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        for (UserConfigDto userConfig : chunk) {
      String employeeName = userConfig.getPosName();
      String baseId = userConfig.getBaseId();
      String khachHangTableId = userConfig.getKhachHangTableId();
      String lichHenTableId = userConfig.getLichHenTableId();
      String larkName = userConfig.getLarkName();

      // Láº¥y thÃªm thÃ´ng tin chi tiáº¿t tá»« POS user
      String posPhone = "";
      if (userConfig.getPosUser() != null && userConfig.getPosUser().getUser() != null) {
        posPhone = userConfig.getPosUser().getUser().getPhoneNumber() != null
            ? userConfig.getPosUser().getUser().getPhoneNumber() : "";
      }

      log.info("[STATS] NhÃ¢n viÃªn: '{}' | SÄT: {} | Lark node: '{}' | baseId: '{}' | khTableId: '{}' | lhTableId: '{}'",
          employeeName, posPhone, larkName, baseId, khachHangTableId, lichHenTableId);

      // Bá» qua nhÃ¢n viÃªn khÃ´ng cÃ³ base id - khÃ´ng hiá»ƒn thá»‹ trong báº£ng thá»‘ng kÃª
      if (baseId == null || baseId.isBlank()) {
        log.info("[STATS]   â†’ Bá» qua: baseId rá»—ng");
        continue;
      }

      // Bá» qua nhÃ¢n viÃªn khÃ´ng cÃ³ Ä‘á»§ table id
      if (khachHangTableId == null || khachHangTableId.isBlank()
          || lichHenTableId == null || lichHenTableId.isBlank()) {
        log.info("[STATS]   â†’ Bá» qua: thiáº¿u tableId (kh={}, lh={})",
            khachHangTableId, lichHenTableId);
        continue;
      }

      EmployeeStatsDto stats = new EmployeeStatsDto(employeeName);
      
      // Kiá»ƒm tra xem cÃ³ pháº£i nhÃ¢n viÃªn Ä‘áº·c biá»‡t khÃ´ng
      boolean isSpecialEmployee = employeeName != null 
          && (employeeName.contains("Nguyá»…n Thá»‹ Lan Anh") && employeeName.contains("0333058439"));

      try {
        // 1. èŽ·å–å®¢æˆ·åˆ—è¡¨ï¼ˆRecord ID ä¼šè‡ªåŠ¨è¿”å›žï¼‰
        // Sá»­ dá»¥ng view_id: vew5Ou4Kee cho báº£ng KhÃ¡ch HÃ ng
        // Sá»­ dá»¥ng customerMonthRange (CurrentMonth hoáº·c LastMonth) cho API láº¥y khÃ¡ch hÃ ng
        List<String> fieldNamesKhachHang = List.of("NgÃ y táº¡o", "Äiá»‡n thoáº¡i");
        String khachHangViewId = "vew5Ou4Kee";
        List<BitableRecord> khachHangRecords = bitableService.searchRecords(session, baseId,
            khachHangTableId, fieldNamesKhachHang, khachHangViewId, khachHangTimeRange);

        log.info("[STATS]   â†’ KhÃ¡ch HÃ ng: fetched {} records", khachHangRecords.size());

        // æå–å®¢æˆ· Record ID é›†åˆ
        java.util.Set<String> khachHangRecordIds = new java.util.HashSet<>();
        for (BitableRecord record : khachHangRecords) {
          if (record.getRecordId() != null && !record.getRecordId().isBlank()) {
            khachHangRecordIds.add(record.getRecordId());
          }
        }
        stats.setTongKhach(khachHangRecordIds.size());

        // 2. èŽ·å–é¢„çº¦åˆ—è¡¨ï¼ˆåŒ…å« KhÃ¡ch HÃ ng çš„ link_record_ids å’Œ Tráº¡ng ThÃ¡iï¼‰
        List<String> fieldNamesLichHen = List.of("NgÃ y táº¡o", "KhÃ¡ch HÃ ng", "Tráº¡ng ThÃ¡i");
        String lichHenViewId = isSpecialEmployee ? "vewENGQUc0" : "vewRa6d1vZ";
        if (isSpecialEmployee) {
          log.info("[STATS]   Using special view_id vewENGQUc0 for employee: {}", employeeName);
        }
        log.debug("Fetching lich hen records with timeRange: {} for employee: {}", lichHenTimeRange, employeeName);
        List<BitableRecord> lichHenRecords = bitableService.searchRecords(session, baseId,
            lichHenTableId, fieldNamesLichHen, lichHenViewId, lichHenTimeRange);

        log.info("[STATS]   â†’ Lá»‹ch Háº¹n: fetched {} records", lichHenRecords.size());

        long tongLich = 0;
        long hoanThanhMuon = 0;
        long hoanThanh = 0;
        long quaHan = 0;

        for (BitableRecord record : lichHenRecords) {
          Map<String, Object> fields = record.getFields();
          if (fields == null) continue;

          // èŽ·å– KhÃ¡ch HÃ ng çš„ link_record_ids
          Object khachHangField = fields.get("KhÃ¡ch HÃ ng");
          if (khachHangField == null) continue;

          java.util.List<String> linkRecordIds = extractLinkRecordIds(khachHangField);

          // æ£€æŸ¥æ˜¯å¦æœ‰ä»»ä½• link_record_id åœ¨å®¢æˆ·åˆ—è¡¨ä¸­
          boolean hasMatchingCustomer = false;
          for (String linkRecordId : linkRecordIds) {
            if (khachHangRecordIds.contains(linkRecordId)) {
              hasMatchingCustomer = true;
              break;
            }
          }

          if (hasMatchingCustomer) {
            tongLich++;

            // èŽ·å– Tráº¡ng ThÃ¡i
            Object trangThaiField = fields.get("Tráº¡ng ThÃ¡i");
            String trangThai = extractText(trangThaiField).toLowerCase();

            if (trangThai.contains("hoÃ n thÃ nh muá»™n") || trangThai.contains("hoÃ n thÃ nh trá»…")) {
              hoanThanhMuon++;
            } else if (trangThai.contains("hoÃ n thÃ nh")) {
              hoanThanh++;
            } else if (trangThai.contains("quÃ¡ háº¡n") || trangThai.contains("quÃ¡ háº¡n")) {
              quaHan++;
            }
          }
        }

          stats.setTongLich(tongLich);
          stats.setHoanThanhMuon(hoanThanhMuon);
          stats.setHoanThanh(hoanThanh);
          stats.setQuaHan(quaHan);

          if (khachHangRecordIds.size() == 0 && tongLich == 0) {
            log.info("[STATS]   â†’ Káº¾T QUáº¢ Báº°NG 0: {} khÃ¡ch, {} lá»‹ch", khachHangRecordIds.size(), tongLich);
          }

        } catch (Exception e) {
          log.warn("Failed to calculate stats for employee {}: {}", employeeName, e.getMessage());
        }

        statsList.add(stats);
        }
      }, executorService);
      
      futures.add(future);
    }
    
    // Chá» táº¥t cáº£ cÃ¡c thread hoÃ n thÃ nh
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(120, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      log.warn("Timeout waiting for stats calculation threads");
    } catch (Exception e) {
      log.error("Error waiting for stats calculation threads: {}", e.getMessage(), e);
    }

    return statsList;
  }

  private java.util.List<String> extractLinkRecordIds(Object khachHangField) {
    java.util.List<String> result = new java.util.ArrayList<>();

    if (khachHangField instanceof Map<?, ?> map) {
      Object linkRecordIds = map.get("link_record_ids");
      if (linkRecordIds instanceof java.util.List<?> list) {
        for (Object item : list) {
          if (item instanceof String str) {
            result.add(str);
          }
        }
      }
    }

    return result;
  }

  private String extractText(Object v) {
    if (v == null) return "";
    if (v instanceof String s) return s;
    if (v instanceof Number n) return String.valueOf(n);

    if (v instanceof Map<?, ?> map) {
      Object name = map.get("name");
      if (name != null) return String.valueOf(name);
      Object text = map.get("text");
      if (text != null) return String.valueOf(text);
      Object value = map.get("value");
      if (value != null) return String.valueOf(value);
    }

    if (v instanceof java.util.List<?> list) {
      StringBuilder sb = new StringBuilder();
      for (Object it : list) {
        String part = extractText(it);
        if (!part.isBlank()) {
          if (!sb.isEmpty()) sb.append(", ");
          sb.append(part);
        }
      }
      return sb.toString();
    }

    return String.valueOf(v);
  }
}
