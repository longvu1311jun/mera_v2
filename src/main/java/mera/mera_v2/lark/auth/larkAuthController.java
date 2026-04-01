package mera.mera_v2.lark.auth;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/lark")
@RequiredArgsConstructor
public class larkAuthController {
  @Value("${lark.app-id}")
  private String appId;

  @Value("${lark.redirect-uri}")
  private String redirectUri;

  @Value("${lark.wiki-token}")
  private String wikiToken;
  
  @Value("${lark.bitable.app-token:}")
  private String defaultAppToken;
  
  @Value("${lark.bitable.table-id:}")
  private String defaultTableId;

  private final larkAuthService authService;
  private final mera.mera_v2.lark.token.TokenStorageService tokenStorageService;

  @Autowired(required = false)
  private mera.mera_v2.lark.webhook.config.LarkBaseProperties reportProps;

  @Autowired(required = false)
  private mera.mera_v2.lark.webhook.service.ReportService reportService;
  
  @Autowired(required = false)
  private mera.mera_v2.lark.webhook.getTableID getTableIDService;

  @Autowired(required = false)
  private mera.mera_v2.lark.webhook.service.BaseTableMappingService baseTableMappingService;

  @Autowired(required = false)
  private mera.mera_v2.lark.webhook.service.TenantTokenService tenantTokenService;

  // Trang /lark: show nút Login Lark
  @GetMapping("")
  public String index(Model model) {
    // build URL login Lark
    String baseUrl = "https://open.larksuite.com/open-apis/authen/v1/index";

    String authUrl = baseUrl
        + "?app_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&state=" + URLEncoder.encode("xyz", StandardCharsets.UTF_8);

    model.addAttribute("authUrl", authUrl);
    return "lark/login";
  }

  // Callback sau khi user áº¥n Authorize
  @GetMapping("/oauth/callback")
  public String callback(@RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      HttpSession session,
      RedirectAttributes redirectAttributes) {

    // Náº¿u khÃ´ng cÃ³ code, cÃ³ nghÄ©a lÃ  lá»—i hoáº·c chÆ°a login
    if (code == null || code.isEmpty()) {
      redirectAttributes.addFlashAttribute("error", "KhÃ´ng nháº­n Ä‘Æ°á»£c authorization code. Vui lÃ²ng Ä‘Äƒng nháº­p láº¡i.");
      return "redirect:/token";
    }

    try {
      // BÆ°á»›c 1: Láº¥y user access token
      larkTokenResponse tokenResp = authService.exchangeCodeForUserToken(code);
      String userAccessToken = tokenResp.getData().getAccessToken();
      String refreshToken = tokenResp.getData().getRefreshToken();

      // LÆ°u token vÃ o session
      session.setAttribute("userAccessToken", userAccessToken);
      session.setAttribute("refreshToken", refreshToken);
      
      // LÆ°u token vÃ o TokenStorageService Ä‘á»ƒ scheduler cÃ³ thá»ƒ tá»± Ä‘á»™ng lÃ m má»›i
      tokenStorageService.saveTokens(
          userAccessToken,
          refreshToken,
          tokenResp.getData().getExpiresIn(),
          tokenResp.getData().getRefreshExpiresIn()
      );
      log.info("âœ… Token Ä‘Ã£ Ä‘Æ°á»£c lÆ°u vÃ o TokenStorageService - Scheduler sáº½ tá»± Ä‘á»™ng lÃ m má»›i má»—i 1 giá»");

      // Láº¥y tenant token tá»± Ä‘á»™ng
      if (tenantTokenService != null) {
        try {
          String tenantToken = tenantTokenService.getTenantAccessToken();
          tokenStorageService.saveTenantAccessToken(tenantToken, 7200); // Tenant token thÆ°á»ng cÃ³ hiá»‡u lá»±c 2 giá»
          session.setAttribute("tenantAccessToken", tenantToken);
          log.info("âœ… Tenant token Ä‘Ã£ Ä‘Æ°á»£c láº¥y vÃ  lÆ°u vÃ o TokenStorageService");
        } catch (Exception e) {
          log.warn("âš ï¸ KhÃ´ng thá»ƒ láº¥y tenant token: {}", e.getMessage());
          session.setAttribute("tenantTokenError", e.getMessage());
        }
      }

      // BÆ°á»›c 2: Tá»± Ä‘á»™ng láº¥y app token tá»« wiki
      try {
        String appToken = authService.getAppTokenFromWiki(userAccessToken, wikiToken);
        session.setAttribute("appToken", appToken);
        session.setAttribute("wikiToken", wikiToken);

        // BÆ°á»›c 2.5: Láº¥y danh sÃ¡ch Base IDs tá»« Wiki
        try {
          List<larkAuthService.BaseIdInfo> baseIds = authService.getAllBaseIds(userAccessToken, wikiToken);
          session.setAttribute("baseIds", baseIds);
          log.info("âœ… ÄÃ£ láº¥y Ä‘Æ°á»£c {} Base IDs tá»« Wiki", baseIds.size());
          // In ra console danh sÃ¡ch Base IDs
          log.info("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
          log.info("ðŸ“‹ DANH SÃCH BASE IDs:");
          log.info("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
          for (larkAuthService.BaseIdInfo baseId : baseIds) {
            log.info("â”‚ Base Name: {}", baseId.getBaseName());
            log.info("â”‚ Base ID:   {}", baseId.getBaseId());
            log.info("â”‚ Path:      {}", baseId.getPath());
            log.info("â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€");
          }
          log.info("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
        } catch (Exception e) {
          log.warn("âš ï¸ KhÃ´ng thá»ƒ láº¥y danh sÃ¡ch Base IDs: {}", e.getMessage());
          session.setAttribute("baseIdsError", e.getMessage());
        }

        // LÆ°u token vÃ  app token vÃ o properties Ä‘á»ƒ cÃ¡c service khÃ¡c sá»­ dá»¥ng (pháº£i set TRÆ¯á»šC khi gá»i getTableIDService)
        if (reportProps != null) {
          reportProps.setUserAccessToken(userAccessToken);
          reportProps.setAppToken(appToken);
        }
        
        // BÆ°á»›c 3: Láº¥y danh sÃ¡ch tables (filter tÃªn chá»©a "_")
        List<larkBitableTablesResponse.TableInfo> tables =
            authService.getTablesFilteredByName(userAccessToken, appToken);
        session.setAttribute("tables", tables);
        session.setAttribute("count", tables.size());
        
        // BÆ°á»›c 3.5: Láº¥y danh sÃ¡ch Base IDs vÃ  Table IDs tá»« getTableID service
        if (getTableIDService != null) {
          try {
            List<mera.mera_v2.lark.webhook.getTableID.BaseIdInfo> baseIdsFromService = 
                getTableIDService.getAllBaseIds();
            java.util.Map<String, List<mera.mera_v2.lark.webhook.getTableID.TableIdInfo>> baseTableMap = 
                getTableIDService.getAllBaseIdsAndTableIds();
            
            log.info("");
            log.info("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
            log.info("ðŸ“‹ DANH SÃCH BASE IDs VÃ€ TABLE IDs:");
            log.info("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
            
            for (mera.mera_v2.lark.webhook.getTableID.BaseIdInfo baseInfo : baseIdsFromService) {
              log.info("â”‚ Base Name: {}", baseInfo.getBaseName());
              log.info("â”‚ Base ID:   {}", baseInfo.getBaseId());
              log.info("â”‚ Path:      {}", baseInfo.getPath());
              
              List<mera.mera_v2.lark.webhook.getTableID.TableIdInfo> tableIds = 
                  baseTableMap.getOrDefault(baseInfo.getBaseId(), new java.util.ArrayList<>());
              log.info("â”‚ Tables ({})", tableIds.size());
              
              if (tableIds.isEmpty()) {
                log.info("â”‚   (khÃ´ng cÃ³ tables)");
              } else {
                for (mera.mera_v2.lark.webhook.getTableID.TableIdInfo tableInfo : tableIds) {
                  log.info("â”‚   - Table ID: {} | Name: {}", tableInfo.getTableId(), tableInfo.getTableName());
                }
              }
              log.info("â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€");
            }
            log.info("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
            log.info("");
            
            session.setAttribute("baseTableMap", baseTableMap);
            
            // LÆ°u vÃ o BaseTableMappingService Ä‘á»ƒ webhook cÃ³ thá»ƒ sá»­ dá»¥ng
            if (baseTableMappingService != null) {
              java.util.List<mera.mera_v2.lark.webhook.dto.BaseTableMapping> mappings = new java.util.ArrayList<>();
              for (mera.mera_v2.lark.webhook.getTableID.BaseIdInfo baseInfo : baseIdsFromService) {
                List<mera.mera_v2.lark.webhook.getTableID.TableIdInfo> tableIds = 
                        baseTableMap.getOrDefault(baseInfo.getBaseId(), new java.util.ArrayList<>());
                // Trim() Base Name Ä‘á»ƒ loáº¡i bá» khoáº£ng tráº¯ng thá»«a
                String baseName = baseInfo.getBaseName() != null ? baseInfo.getBaseName().trim() : null;
                
                if (!tableIds.isEmpty()) {
                  for (mera.mera_v2.lark.webhook.getTableID.TableIdInfo tableInfo : tableIds) {
                    mappings.add(new mera.mera_v2.lark.webhook.dto.BaseTableMapping(
                            baseName,
                            baseInfo.getBaseId(),
                            tableInfo.getTableId(),
                            tableInfo.getTableName() != null ? tableInfo.getTableName().trim() : null
                    ));
                  }
                } else {
                  // Náº¿u khÃ´ng cÃ³ table, váº«n thÃªm base vÃ o mapping
                  mappings.add(new mera.mera_v2.lark.webhook.dto.BaseTableMapping(
                          baseName,
                          baseInfo.getBaseId(),
                          null,
                          null
                  ));
                }
              }
              baseTableMappingService.setMappings(mappings);
              log.info("âœ… ÄÃ£ lÆ°u {} Base-Table mappings vÃ o service", mappings.size());
            }
          } catch (Exception e) {
            log.warn("âš ï¸ KhÃ´ng thá»ƒ láº¥y Base IDs vÃ  Table IDs tá»« getTableID service: {}", e.getMessage());
          }
        }
        
        // Trigger refresh report vá»›i token má»›i (sáº½ tá»± Ä‘á»™ng láº¥y table IDs)
        if (reportService != null) {
          reportService.refreshReport();
        }
      } catch (Exception e) {
        // LÆ°u lá»—i vÃ o session Ä‘á»ƒ hiá»ƒn thá»‹ á»Ÿ trang token
        session.setAttribute("tableError", e.getMessage());
      }
    } catch (Exception e) {
      // LÆ°u lá»—i vÃ o session
      session.setAttribute("error", e.getMessage());
    }

    // Redirect Ä‘áº¿n URL ngáº¯n gá»n
    return "redirect:/token";
  }

  // Endpoint Ä‘Æ¡n giáº£n Ä‘á»ƒ xem token (URL ngáº¯n gá»n)
  @GetMapping("/token")
  public String token(HttpSession session, Model model) {
    // Láº¥y token tá»« session
    String userAccessToken = (String) session.getAttribute("userAccessToken");
    String refreshToken = (String) session.getAttribute("refreshToken");
    String tenantAccessToken = (String) session.getAttribute("tenantAccessToken");
    String appToken = (String) session.getAttribute("appToken");
    String wikiToken = (String) session.getAttribute("wikiToken");
    @SuppressWarnings("unchecked")
    List<larkBitableTablesResponse.TableInfo> tables =
        (List<larkBitableTablesResponse.TableInfo>) session.getAttribute("tables");
    @SuppressWarnings("unchecked")
    List<larkAuthService.BaseIdInfo> baseIds =
        (List<larkAuthService.BaseIdInfo>) session.getAttribute("baseIds");
    Integer count = (Integer) session.getAttribute("count");
    String error = (String) session.getAttribute("error");
    String tableError = (String) session.getAttribute("tableError");
    String baseIdsError = (String) session.getAttribute("baseIdsError");
    String tenantTokenError = (String) session.getAttribute("tenantTokenError");

    // Build auth URL Ä‘á»ƒ hiá»ƒn thá»‹ nÃºt login (cáº§n khi cÃ³ lá»—i hoáº·c chÆ°a login)
    String baseUrl = "https://open.larksuite.com/open-apis/authen/v1/index";
    String authUrl = baseUrl
        + "?app_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&state=" + URLEncoder.encode("xyz", StandardCharsets.UTF_8);
    model.addAttribute("authUrl", authUrl);

    // Kiá»ƒm tra error tá»« session hoáº·c flash attribute (tá»« redirect)
    String errorFromModel = (String) model.asMap().get("error");
    if (errorFromModel != null) {
      error = errorFromModel;
    }
    if (error != null) {
      model.addAttribute("error", error);
      // XÃ³a khá»i session sau khi Ä‘Ã£ Ä‘á»c
      session.removeAttribute("error");
    }
    if (tableError != null) {
      model.addAttribute("tableError", tableError);
      // XÃ³a khá»i session sau khi Ä‘Ã£ Ä‘á»c
      session.removeAttribute("tableError");
    }
    if (baseIdsError != null) {
      model.addAttribute("baseIdsError", baseIdsError);
      session.removeAttribute("baseIdsError");
    }
    if (tenantTokenError != null) {
      model.addAttribute("tenantTokenError", tenantTokenError);
      session.removeAttribute("tenantTokenError");
    }

    // Náº¿u khÃ´ng cÃ³ token, cÃ³ nghÄ©a lÃ  chÆ°a login
    if (userAccessToken == null || userAccessToken.isEmpty()) {
      model.addAttribute("needsLogin", true);
      return "token";
    }

    // CÃ³ token, hiá»ƒn thá»‹ thÃ´ng tin
    model.addAttribute("userAccessToken", userAccessToken);
    model.addAttribute("refreshToken", refreshToken);
    if (tenantAccessToken != null && !tenantAccessToken.isEmpty()) {
      model.addAttribute("tenantAccessToken", tenantAccessToken);
    }
    if (appToken != null) {
      model.addAttribute("appToken", appToken);
      model.addAttribute("wikiToken", wikiToken);
    }
    if (tables != null) {
      model.addAttribute("tables", tables);
      model.addAttribute("count", count != null ? count : tables.size());
    }
    if (baseIds != null) {
      model.addAttribute("baseIds", baseIds);
      model.addAttribute("baseIdsCount", baseIds.size());
    }
    
    // ThÃªm thÃ´ng tin Base ID vÃ  Table ID máº·c Ä‘á»‹nh dÃ¹ng Ä‘á»ƒ táº¡o báº£n ghi
    if (defaultAppToken != null && !defaultAppToken.isEmpty()) {
      model.addAttribute("defaultAppToken", defaultAppToken);
    }
    if (defaultTableId != null && !defaultTableId.isEmpty()) {
      model.addAttribute("defaultTableId", defaultTableId);
    }

    return "token";
  }

  /**
   * Láº¥y app token tá»« wiki node API
   * GET /wiki/app-token?userToken=xxx&wikiToken=xxx
   */
  @GetMapping("/wiki/app-token")
  public String getAppTokenFromWiki(
      @RequestParam("userToken") String userAccessToken,
      @RequestParam("wikiToken") String wikiToken,
      Model model) {
    try {
      String appToken = authService.getAppTokenFromWiki(userAccessToken, wikiToken);
      model.addAttribute("appToken", appToken);
      model.addAttribute("wikiToken", wikiToken);
      model.addAttribute("userAccessToken", userAccessToken);
    } catch (Exception e) {
      model.addAttribute("error", e.getMessage());
    }
    return "token"; // cÃ³ thá»ƒ táº¡o view riÃªng náº¿u cáº§n
  }

  /**
   * Láº¥y danh sÃ¡ch tables tá»« bitable (filter tÃªn chá»©a "_")
   * GET /bitable/tables?userToken=xxx&appToken=xxx
   */
  @GetMapping("/bitable/tables")
  public String getTables(
      @RequestParam("userToken") String userAccessToken,
      @RequestParam("appToken") String appToken,
      Model model) {
    try {
      List<larkBitableTablesResponse.TableInfo> tables =
          authService.getTablesFilteredByName(userAccessToken, appToken);
      model.addAttribute("tables", tables);
      model.addAttribute("appToken", appToken);
      model.addAttribute("count", tables.size());
    } catch (Exception e) {
      model.addAttribute("error", e.getMessage());
    }
    return "token"; // cÃ³ thá»ƒ táº¡o view riÃªng náº¿u cáº§n
  }

  /**
   * Endpoint tÃ­ch há»£p: Láº¥y app token tá»« wiki rá»“i láº¥y danh sÃ¡ch tables
   * GET /bitable/get-tables?userToken=xxx&wikiToken=xxx
   */
  @GetMapping("/bitable/get-tables")
  public String getTablesFromWiki(
      @RequestParam("userToken") String userAccessToken,
      @RequestParam("wikiToken") String wikiToken,
      Model model) {
    try {
      // BÆ°á»›c 1: Láº¥y app token tá»« wiki
      String appToken = authService.getAppTokenFromWiki(userAccessToken, wikiToken);
      model.addAttribute("appToken", appToken);

      // BÆ°á»›c 2: Láº¥y danh sÃ¡ch tables (filter tÃªn chá»©a "_")
      List<larkBitableTablesResponse.TableInfo> tables =
          authService.getTablesFilteredByName(userAccessToken, appToken);
      model.addAttribute("tables", tables);
      model.addAttribute("count", tables.size());
      model.addAttribute("wikiToken", wikiToken);
    } catch (Exception e) {
      model.addAttribute("error", e.getMessage());
    }
    return "token"; // cÃ³ thá»ƒ táº¡o view riÃªng náº¿u cáº§n
  }

  @Autowired(required = false)
  private mera.mera_v2.lark.webhook.scheduler2.TokenRefreshScheduler tokenRefreshScheduler;

  /**
   * Endpoint Ä‘á»ƒ lÃ m má»›i token thá»§ cÃ´ng
   * GET /token/refresh
   */
  @GetMapping("/token/refresh")
  public String manualRefreshToken(HttpSession session, RedirectAttributes redirectAttributes) {
    log.info("Manual token refresh requested");
    
    if (tokenRefreshScheduler != null) {
      boolean success = tokenRefreshScheduler.manualRefresh();
      if (success) {
        // Cáº­p nháº­t token má»›i vÃ o session
        String newToken = tokenStorageService.getUserAccessToken();
        String newRefreshToken = tokenStorageService.getRefreshToken();
        if (newToken != null) {
          session.setAttribute("userAccessToken", newToken);
          session.setAttribute("refreshToken", newRefreshToken);
          
          // Cáº­p nháº­t vÃ o reportProps náº¿u cÃ³
          if (reportProps != null) {
            reportProps.setUserAccessToken(newToken);
          }
        }
        redirectAttributes.addFlashAttribute("success", "Token Ä‘Ã£ Ä‘Æ°á»£c lÃ m má»›i thÃ nh cÃ´ng!");
      } else {
        redirectAttributes.addFlashAttribute("error", "KhÃ´ng thá»ƒ lÃ m má»›i token. Vui lÃ²ng Ä‘Äƒng nháº­p láº¡i.");
      }
    } else {
      redirectAttributes.addFlashAttribute("error", "TokenRefreshScheduler khÃ´ng kháº£ dá»¥ng");
    }
    
    return "redirect:/token";
  }
}