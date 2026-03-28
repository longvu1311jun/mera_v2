package mera.mera_v2.lark.auth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class larkAuthService {
  @Value("${lark.app-id}")
  private String appId;

  @Value("${lark.app-secret}")
  private String appSecret;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RestTemplate restTemplate;

  /**
   * Láº¥y app_access_token tá»« Lark
   */
  public String getAppAccessToken() throws Exception {
    String url = "https://open.larksuite.com/open-apis/auth/v3/app_access_token/internal/";

    Map<String, String> body = Map.of(
        "app_id", appId,
        "app_secret", appSecret
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

    ResponseEntity<String> resp;
    try {
      resp = restTemplate.postForEntity(url, entity, String.class);
    } catch (RestClientException e) {
      log.error("Error calling getAppAccessToken API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to get app access token: " + e.getMessage(), e);
    }

    if (!resp.getStatusCode().is2xxSuccessful()) {
      throw new RuntimeException("getAppAccessToken HTTP error: " + resp.getStatusCode());
    }

    JsonNode json = objectMapper.readTree(resp.getBody());
    int code = json.path("code").asInt();
    if (code != 0) {
      throw new RuntimeException("Lark error getAppAccessToken: " + resp.getBody());
    }

    JsonNode tokenNode = json.get("app_access_token");
    if (tokenNode == null || tokenNode.isNull()) {
      throw new RuntimeException("app_access_token not found in response");
    }
    // Sá»­ dá»¥ng ObjectMapper Ä‘á»ƒ convert JsonNode thÃ nh String, trÃ¡nh deprecated asText()
    return objectMapper.convertValue(tokenNode, String.class);
  }

  /**
   * Äá»•i authorization code â†’ user_access_token + refresh_token
   */
  public larkTokenResponse exchangeCodeForUserToken(String code) throws Exception {
    String appAccessToken = getAppAccessToken();

    String url = "https://open.larksuite.com/open-apis/authen/v1/access_token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(appAccessToken);  // Authorization: Bearer <app_access_token>

    Map<String, String> body = Map.of(
        "grant_type", "authorization_code",
        "code", code
    );

    HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

    ResponseEntity<larkTokenResponse> resp;
    try {
      resp = restTemplate.postForEntity(url, entity, larkTokenResponse.class);
    } catch (RestClientException e) {
      log.error("Error calling exchangeCodeForUserToken API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to exchange code for user token: " + e.getMessage(), e);
    }

    larkTokenResponse result = resp.getBody();
    if (result == null) {
      throw new RuntimeException("Empty response from Lark");
    }
    if (result.getCode() != 0) {
      throw new RuntimeException("Lark error: " + result.getCode() + " - " + result.getMsg());
    }

    return result;
  }

  /**
   * LÃ m má»›i user_access_token báº±ng refresh_token
   * @param refreshToken Refresh token hiá»‡n táº¡i
   * @return Response chá»©a token má»›i
   */
  public larkTokenResponse refreshUserAccessToken(String refreshToken) throws Exception {
    String appAccessToken = getAppAccessToken();

    String url = "https://open.larksuite.com/open-apis/authen/v1/refresh_access_token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(appAccessToken);

    Map<String, String> body = Map.of(
        "grant_type", "refresh_token",
        "refresh_token", refreshToken
    );

    HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

    ResponseEntity<larkTokenResponse> resp;
    try {
      log.info("Calling refresh_access_token API...");
      resp = restTemplate.postForEntity(url, entity, larkTokenResponse.class);
    } catch (RestClientException e) {
      log.error("Error calling refreshUserAccessToken API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to refresh user token: " + e.getMessage(), e);
    }

    larkTokenResponse result = resp.getBody();
    if (result == null) {
      throw new RuntimeException("Empty response from Lark refresh token API");
    }
    if (result.getCode() != 0) {
      throw new RuntimeException("Lark refresh token error: " + result.getCode() + " - " + result.getMsg());
    }

    log.info("Successfully refreshed user access token");
    return result;
  }

  /**
   * Láº¥y app token (obj_token) tá»« wiki node API
   * @param userAccessToken User access token Ä‘á»ƒ authenticate
   * @param token Wiki token parameter tá»« URL
   * @return obj_token (app token)
   */
  public String getAppTokenFromWiki(String userAccessToken, String token) throws Exception {
    String url = String.format(
        "https://open.larksuite.com/open-apis/wiki/v2/spaces/get_node?obj_type=wiki&token=%s",
        java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8));

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(userAccessToken);  // Authorization: Bearer <user_access_token>

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    // Láº¥y response dáº¡ng String trÆ°á»›c Ä‘á»ƒ log vÃ  debug
    ResponseEntity<String> stringResp;
    try {
      stringResp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    } catch (RestClientException e) {
      log.error("Error calling getAppTokenFromWiki API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to get app token from wiki: " + e.getMessage(), e);
    }
    
    String responseBody = stringResp.getBody();
//    log.info("Wiki API Response: {}", responseBody);
    
    if (responseBody == null || responseBody.isEmpty()) {
      throw new RuntimeException("Empty response from Lark wiki API");
    }

    // Parse JSON Ä‘á»ƒ kiá»ƒm tra cáº¥u trÃºc
    JsonNode json = objectMapper.readTree(responseBody);
    int code = json.path("code").asInt();
    if (code != 0) {
      JsonNode msgNode = json.path("msg");
      String msg = msgNode != null && !msgNode.isNull() 
          ? objectMapper.convertValue(msgNode, String.class) 
          : "Unknown error";
      throw new RuntimeException("Lark wiki error: " + code + " - " + msg);
    }

    // TÃ¬m obj_token á»Ÿ nhiá»u vá»‹ trÃ­ cÃ³ thá»ƒ
    String objToken = null;
    
    // Thá»­ láº¥y tá»« data.node.obj_token (cáº¥u trÃºc thá»±c táº¿ cá»§a API)
    JsonNode dataNode = json.get("data");
    if (dataNode != null && !dataNode.isNull()) {
      JsonNode nodeData = dataNode.get("node");
      if (nodeData != null && !nodeData.isNull()) {
        JsonNode objTokenNode = nodeData.get("obj_token");
        if (objTokenNode != null && !objTokenNode.isNull()) {
          objToken = objectMapper.convertValue(objTokenNode, String.class);
        }
      }
    }
    
    // Náº¿u khÃ´ng cÃ³, thá»­ láº¥y tá»« data.obj_token (fallback)
    if (objToken == null || objToken.isEmpty()) {
      if (dataNode != null && !dataNode.isNull()) {
        JsonNode objTokenNode = dataNode.get("obj_token");
        if (objTokenNode != null && !objTokenNode.isNull()) {
          objToken = objectMapper.convertValue(objTokenNode, String.class);
        }
      }
    }
    
    // Náº¿u váº«n khÃ´ng cÃ³, thá»­ láº¥y trá»±c tiáº¿p tá»« root
    if (objToken == null || objToken.isEmpty()) {
      JsonNode objTokenNode = json.get("obj_token");
      if (objTokenNode != null && !objTokenNode.isNull()) {
        objToken = objectMapper.convertValue(objTokenNode, String.class);
      }
    }

    if (objToken == null || objToken.isEmpty()) {
      log.error("Cannot find obj_token in response. Full response: {}", responseBody);
      throw new RuntimeException("obj_token not found in wiki response. Response: " + responseBody);
    }

    log.info("Successfully extracted obj_token: {}", objToken);
    return objToken;
  }

  /**
   * DTO Ä‘á»ƒ lÆ°u thÃ´ng tin Base ID
   */
  public static class BaseIdInfo {
    private String baseId;
    private String baseName;
    private String path;

    public BaseIdInfo(String baseId, String baseName, String path) {
      this.baseId = baseId;
      this.baseName = baseName;
      this.path = path;
    }

    public String getBaseId() { return baseId; }
    public String getBaseName() { return baseName; }
    public String getPath() { return path; }
  }

  /**
   * Láº¥y danh sÃ¡ch Base ID tá»« Wiki Space
   * @param userAccessToken User access token Ä‘á»ƒ authenticate
   * @param wikiToken Wiki token Ä‘á»ƒ láº¥y space_id
   * @return Danh sÃ¡ch Base ID info
   */
  public List<BaseIdInfo> getAllBaseIds(String userAccessToken, String wikiToken) throws Exception {
    // BÆ°á»›c 1: Láº¥y space_id tá»« wiki node
    String spaceId = getSpaceIdFromWikiToken(userAccessToken, wikiToken);
    if (spaceId == null || spaceId.isEmpty()) {
      log.warn("Cannot get space_id from wiki token, returning empty list");
      return new ArrayList<>();
    }

    // BÆ°á»›c 2: Crawl táº¥t cáº£ nodes trong space vÃ  filter cÃ¡c bitable nodes
    List<BaseIdInfo> baseIds = new ArrayList<>();
    crawlNodesRecursive(userAccessToken, spaceId, null, "", baseIds);

    log.info("Found {} Base IDs in space", baseIds.size());
    return baseIds;
  }

  /**
   * Láº¥y space_id tá»« wiki token
   */
  private String getSpaceIdFromWikiToken(String userAccessToken, String token) throws Exception {
    String url = String.format(
        "https://open.larksuite.com/open-apis/wiki/v2/spaces/get_node?obj_type=wiki&token=%s",
        java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8));

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(userAccessToken);

    HttpEntity<Void> entity = new HttpEntity<>(headers);
    ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

    if (resp.getBody() == null || resp.getBody().isEmpty()) {
      return null;
    }

    JsonNode json = objectMapper.readTree(resp.getBody());
    if (json.path("code").asInt() != 0) {
      return null;
    }

    JsonNode dataNode = json.get("data");
    if (dataNode != null && !dataNode.isNull()) {
      JsonNode nodeData = dataNode.get("node");
      if (nodeData != null && !nodeData.isNull()) {
        JsonNode spaceIdNode = nodeData.get("space_id");
        if (spaceIdNode != null && !spaceIdNode.isNull()) {
          return objectMapper.convertValue(spaceIdNode, String.class);
        }
      }
    }

    return null;
  }

  /**
   * Äá»‡ quy crawl nodes trong space
   */
  private void crawlNodesRecursive(String userAccessToken, String spaceId, 
      String parentNodeToken, String parentPath, List<BaseIdInfo> baseIds) throws Exception {
    String url = String.format(
        "https://open.larksuite.com/open-apis/wiki/v2/spaces/%s/nodes?page_size=50",
        spaceId);
    
    if (parentNodeToken != null && !parentNodeToken.isEmpty()) {
      url += "&parent_node_token=" + java.net.URLEncoder.encode(parentNodeToken, java.nio.charset.StandardCharsets.UTF_8);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(userAccessToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    String pageToken = null;
    do {
      String currentUrl = url;
      if (pageToken != null && !pageToken.isEmpty()) {
        currentUrl += "&page_token=" + java.net.URLEncoder.encode(pageToken, java.nio.charset.StandardCharsets.UTF_8);
      }

      ResponseEntity<String> resp = restTemplate.exchange(currentUrl, HttpMethod.GET, entity, String.class);
      if (resp.getBody() == null || resp.getBody().isEmpty()) {
        break;
      }

      JsonNode json = objectMapper.readTree(resp.getBody());
      if (json.path("code").asInt() != 0) {
        break;
      }

      JsonNode dataNode = json.get("data");
      if (dataNode == null || dataNode.isNull()) {
        break;
      }

      JsonNode itemsNode = dataNode.get("items");
      if (itemsNode != null && itemsNode.isArray()) {
        for (JsonNode item : itemsNode) {
          JsonNode objTypeNode = item.path("obj_type");
          String objType = objTypeNode != null && !objTypeNode.isNull() 
              ? objectMapper.convertValue(objTypeNode, String.class) : "";
          JsonNode objTokenNode = item.path("obj_token");
          String objToken = objTokenNode != null && !objTokenNode.isNull() 
              ? objectMapper.convertValue(objTokenNode, String.class) : "";
          JsonNode titleNode = item.path("title");
          String title = titleNode != null && !titleNode.isNull() 
              ? objectMapper.convertValue(titleNode, String.class) : "";
          JsonNode nodeTokenNode = item.path("node_token");
          String nodeToken = nodeTokenNode != null && !nodeTokenNode.isNull() 
              ? objectMapper.convertValue(nodeTokenNode, String.class) : "";
          boolean hasChild = item.path("has_child").asBoolean(false);

          String currentPath = (parentPath == null || parentPath.isEmpty()) 
              ? title 
              : parentPath + "/" + title;

          // Náº¿u lÃ  bitable, thÃªm vÃ o danh sÃ¡ch
          if ("bitable".equalsIgnoreCase(objType) && objToken != null && !objToken.isEmpty()) {
            baseIds.add(new BaseIdInfo(objToken, title, currentPath));
            log.debug("Found Base ID: {} - {} ({})", objToken, title, currentPath);
          }

          // Äá»‡ quy náº¿u cÃ³ con
          if (hasChild && nodeToken != null && !nodeToken.isEmpty()) {
            crawlNodesRecursive(userAccessToken, spaceId, nodeToken, currentPath, baseIds);
          }
        }
      }

      JsonNode hasMoreNode = dataNode.path("has_more");
      boolean hasMore = hasMoreNode != null && !hasMoreNode.isNull() && hasMoreNode.asBoolean(false);
      if (!hasMore) {
        break;
      }

      JsonNode pageTokenNode = dataNode.path("page_token");
      pageToken = pageTokenNode != null && !pageTokenNode.isNull() 
          ? objectMapper.convertValue(pageTokenNode, String.class) : "";
      if (pageToken == null || pageToken.isEmpty()) {
        break;
      }
    } while (true);
  }

  /**
   * Láº¥y danh sÃ¡ch tables tá»« bitable API, chá»‰ láº¥y cÃ¡c table cÃ³ tÃªn chá»©a "_"
   * Xá»­ lÃ½ pagination Ä‘á»ƒ láº¥y táº¥t cáº£ tables náº¿u cÃ³ nhiá»u hÆ¡n 50
   * @param userAccessToken User access token Ä‘á»ƒ authenticate
   * @param appToken App token (obj_token) tá»« wiki
   * @return Danh sÃ¡ch table info cÃ³ tÃªn chá»©a "_"
   */
  public List<larkBitableTablesResponse.TableInfo> getTablesFilteredByName(
      String userAccessToken, String appToken) throws Exception {
    String baseUrl = "https://open.larksuite.com/open-apis/bitable/v1/apps/"
        + appToken + "/tables?page_size=50";

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(userAccessToken);  // Authorization: Bearer <user_access_token>

    // Danh sÃ¡ch Ä‘á»ƒ gá»™p táº¥t cáº£ tables tá»« cÃ¡c page
    List<larkBitableTablesResponse.TableInfo> allTables = new ArrayList<>();
    
    String pageToken = null;
    int pageNumber = 1;
    
    do {
      // XÃ¢y dá»±ng URL vá»›i page_token náº¿u cÃ³
      String url = baseUrl;
      if (pageToken != null && !pageToken.isEmpty()) {
        try {
          url += "&page_token=" + java.net.URLEncoder.encode(pageToken, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
          log.error("Error encoding page_token: {}", e.getMessage());
          break;
        }
      }

      HttpEntity<Void> entity = new HttpEntity<>(headers);

      ResponseEntity<larkBitableTablesResponse> resp;
      try {
        log.info("Fetching tables page {} from bitable API, appToken: {}...", 
            pageNumber,
            appToken != null && appToken.length() > 10 ? appToken.substring(0, 10) + "..." : appToken);
        resp = restTemplate.exchange(url, HttpMethod.GET, entity, larkBitableTablesResponse.class);
        log.info("Successfully received response from bitable API (page {})", pageNumber);
      } catch (RestClientException e) {
        log.error("Error calling getTablesFilteredByName API. URL: {}, Error: {}", url, e.getMessage(), e);
        throw new RuntimeException("Failed to get tables from bitable: " + e.getMessage(), e);
      }

      larkBitableTablesResponse result = resp.getBody();
      if (result == null) {
        throw new RuntimeException("Empty response from Lark bitable API");
      }
      if (result.getCode() != 0) {
        throw new RuntimeException("Lark bitable error: " + result.getCode() + " - " + result.getMsg());
      }
      if (result.getData() == null || result.getData().getItems() == null) {
        log.info("KhÃ´ng cÃ³ tables nÃ o trong response (page {})", pageNumber);
        break;
      }

      // ThÃªm tables tá»« page nÃ y vÃ o danh sÃ¡ch tá»•ng
      List<larkBitableTablesResponse.TableInfo> pageTables = result.getData().getItems();
      allTables.addAll(pageTables);
      log.info("Page {}: Láº¥y Ä‘Æ°á»£c {} tables (tá»•ng cá»™ng: {})", pageNumber, pageTables.size(), allTables.size());

      // Kiá»ƒm tra xem cÃ²n page tiáº¿p theo khÃ´ng
      boolean hasMore = result.getData().isHasMore();
      if (hasMore) {
        pageToken = result.getData().getPageToken();
        if (pageToken == null || pageToken.isEmpty()) {
          log.warn("has_more = true nhÆ°ng khÃ´ng cÃ³ page_token, dá»«ng pagination");
          break;
        }
        pageNumber++;
        // ThÃªm delay nhá» giá»¯a cÃ¡c request Ä‘á»ƒ trÃ¡nh rate limiting
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Thread interrupted during delay");
          break;
        }
      } else {
        pageToken = null; // KhÃ´ng cÃ²n page nÃ o ná»¯a
      }
      
    } while (pageToken != null && !pageToken.isEmpty());

    log.info("Tá»•ng sá»‘ tables láº¥y Ä‘Æ°á»£c tá»« táº¥t cáº£ cÃ¡c page: {}", allTables.size());

    // Filter chá»‰ láº¥y cÃ¡c table cÃ³ tÃªn chá»©a "_"
    List<larkBitableTablesResponse.TableInfo> filteredTables = allTables.stream()
        .filter(table -> table.getName() != null && table.getName().contains("_"))
        .collect(Collectors.toList());

    log.info("Sau khi filter (tÃªn chá»©a '_'): {} tables", filteredTables.size());

    return filteredTables;
  }
}