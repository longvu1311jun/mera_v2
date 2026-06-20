package mera.mera_v2.lark.wiki;

import mera.mera_v2.lark.token.LarkTokenService;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpSession;
import mera.mera_v2.model.LarkNode;
import mera.mera_v2.model.PosUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LarkWikiService {
  
  private static final Logger log = LoggerFactory.getLogger(LarkWikiService.class);
  private static final String LARK_SPACE_ID = "7553087350184673311";
  
  private final RestTemplate restTemplate;
  private final LarkTokenService tokenService;

  // Rate-limit protection: cache for body content (session-scoped conceptually,
  // but keyed by nodeToken so it survives across sessions within the same JVM process)
  private final java.util.Map<String, String> bodyCache = new java.util.concurrent.ConcurrentHashMap<>();
  // Track last API call time to enforce minimum delay between calls
  private volatile long lastApiCallTime = 0;
  private static final long MIN_CALL_INTERVAL_MS = 120; // 120ms between API calls (~8 req/sec, safe for Lark rate limits)
  
  public LarkWikiService(LarkTokenService tokenService) {
    this.restTemplate = new RestTemplate();
    this.tokenService = tokenService;
  }
  
  /**
   * Get all nodes (bases) from space with pagination support.
   * Uses global token storage (no session required).
   */
  public List<LarkNode> getAllNodes() throws Exception {
    List<LarkNode> allNodes = new java.util.ArrayList<>();
    String pageToken = "";
    boolean hasMore = true;

    log.info("=== Fetching All Wiki Nodes (no-session, global token) ===");

    while (hasMore) {
      String accessToken = tokenService.getAccessToken(false);
      String url = String.format(
          "https://open.larksuite.com/open-apis/wiki/v2/spaces/%s/nodes?page_size=50",
          LARK_SPACE_ID
      );
      if (!pageToken.isEmpty()) {
        url += "&page_token=" + pageToken;
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
      headers.setBearerAuth(accessToken);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<LarkNodesResponse> response = restTemplate.exchange(
          url, HttpMethod.GET, entity, LarkNodesResponse.class
      );

      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        LarkNodesResponse body = response.getBody();
        if (body.getCode() == 0 && body.getData() != null) {
          allNodes.addAll(body.getData().getItems());
          hasMore = body.getData().isHasMore();
          pageToken = body.getData().getPageToken();
        } else {
          log.warn("API Error: {} - {}", body.getCode(), body.getMsg());
          break;
        }
      } else {
        log.warn("Failed to get nodes: HTTP {}", response.getStatusCode());
        break;
      }
    }

    log.info("Total Wiki nodes fetched: {}", allNodes.size());
    return allNodes;
  }

  /**
   * Get all nodes (bases) from space with pagination support (session-based)
   */
  public List<LarkNode> getAllNodes(HttpSession session) throws Exception {
    return getAllNodes();
  }

  /**
   * Get child nodes of a specific node with pagination support.
   * Uses global token storage (no session required).
   */
  public List<LarkNode> getChildNodesByNodeToken(String nodeToken) throws Exception {
    if (nodeToken == null || nodeToken.isEmpty()) return Collections.emptyList();

    List<LarkNode> childNodes = new java.util.ArrayList<>();
    String pageToken = "";
    boolean hasMore = true;

    while (hasMore) {
      String accessToken = tokenService.getAccessToken(false);
      String url = String.format(
          "https://open.larksuite.com/open-apis/wiki/v2/spaces/%s/nodes?parent_node_token=%s&page_size=50",
          LARK_SPACE_ID, nodeToken
      );
      if (!pageToken.isEmpty()) {
        url += "&page_token=" + pageToken;
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
      headers.setBearerAuth(accessToken);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<LarkNodesResponse> response = restTemplate.exchange(
          url, HttpMethod.GET, entity, LarkNodesResponse.class
      );

      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        LarkNodesResponse body = response.getBody();
        if (body.getCode() == 0 && body.getData() != null) {
          childNodes.addAll(body.getData().getItems());
          hasMore = body.getData().isHasMore();
          pageToken = body.getData().getPageToken();
        } else {
          break;
        }
      } else {
        break;
      }
    }
    return childNodes;
  }

  /**
   * Get child nodes of a specific node with pagination support (session-based)
   */
  public List<LarkNode> getChildNodesByNodeToken(String nodeToken, HttpSession session) throws Exception {
    return getChildNodesByNodeToken(nodeToken);
  }

  /**
   * Get all nodes and their child nodes with pagination support.
   * Uses global token storage (no session required).
   */
  public List<LarkNode> getAllNodesWithChildren() throws Exception {
    List<LarkNode> allNodes = getAllNodes();

    for (LarkNode node : allNodes) {
      if (node.getNodeToken() != null && !node.getNodeToken().isEmpty()) {
        try {
          List<LarkNode> childNodes = getChildNodesByNodeToken(node.getNodeToken());
          node.setChildNodes(childNodes);
        } catch (Exception e) {
          log.warn("Failed to get child nodes for node {}: {}", node.getNodeToken(), e.getMessage());
          node.setChildNodes(Collections.emptyList());
        }
      }
    }

    return allNodes;
  }

  /**
   * Get all nodes and their child nodes with pagination support (session-based, delegates to no-session version)
   */
  public List<LarkNode> getAllNodesWithChildren(HttpSession session) throws Exception {
    return getAllNodesWithChildren();
  }
  
  /**
   * Extract the FIRST phone number from a string.
   * Supports: 0xxx..., +84xxx..., 84xxx...
   */
  private String extractFirstPhoneNumber(String text) {
    if (text == null || text.isEmpty()) return null;

    // Use a more permissive regex to find anything that looks like a phone number
    // Look for digits, but allow common separators in between
    // We'll strip them AFTER matching to verify length
    Pattern pattern = Pattern.compile("(?:\\+84|0|84)[\\s\\.\\-]*[35789][0-9\\s\\.\\-]{7,11}");
    Matcher matcher = pattern.matcher(text);

    if (matcher.find()) {
      String raw = matcher.group();
      String phone = raw.replaceAll("[^0-9]", "");
      
      // Normalize to 0... format
      if (phone.startsWith("84") && phone.length() > 9) {
        phone = "0" + phone.substring(2);
      } else if (!phone.startsWith("0") && phone.length() == 9) {
        phone = "0" + phone;
      }
      
      // Valid VN mobile phones are usually 10 digits starting with 03, 05, 07, 08, 09
      if (phone.length() == 10 && phone.startsWith("0")) {
        return phone;
      }
    }
    
    // Fallback: search for any 10-digit sequence
    Pattern simplePattern = Pattern.compile("[0-9]{10}");
    Matcher simpleMatcher = simplePattern.matcher(text.replaceAll("[^0-9]", ""));
    if (simpleMatcher.find()) {
        return simpleMatcher.group();
    }

    return null;
  }
  
  /**
   * Get phone number from POS user (from phone_number field or extract from name)
   */
  private String getPosUserPhone(PosUser posUser) {
    // 1. HIGHEST PRIORITY: Extract from name (Display phone used for mapping)
    String name = posUser.getName();
    if (name != null) {
      String phoneFromName = extractFirstPhoneNumber(name);
      if (phoneFromName != null && !phoneFromName.isEmpty()) {
        return phoneFromName;
      }
    }

    // 2. FALLBACK: Use official phone_number field
    if (posUser.getUser() != null && posUser.getUser().getPhoneNumber() != null) {
      String phone = posUser.getUser().getPhoneNumber();
      if (phone != null) {
        // Normalize: remove +84 prefix, replace with 0
        phone = phone.replaceAll("[\\s\\.\\-]", "");
        if (phone.startsWith("+84")) {
          phone = "0" + phone.substring(3);
        } else if (phone.startsWith("84") && phone.length() > 2) {
          phone = "0" + phone.substring(2);
        }
        // Remove all non-digit characters for comparison
        phone = phone.replaceAll("[^0-9]", "");
        if (!phone.isEmpty()) {
          return phone;
        }
      }
    }

    return null;
  }
  
  /**
   * Get all nodes including child nodes for matching.
   * Body content is NOT pre-fetched here â€” it's lazy-loaded on demand per Strategy 2.
   */
  private List<LarkNode> getAllNodesForMatching(HttpSession session) throws Exception {
    List<LarkNode> allNodes = getAllNodes(session);
    List<LarkNode> allNodesWithChildren = new java.util.ArrayList<>(allNodes);

    for (LarkNode node : allNodes) {
      if (node.getNodeToken() != null && !node.getNodeToken().isEmpty()) {
        try {
          List<LarkNode> childNodes = getChildNodesByNodeToken(node.getNodeToken(), session);
          allNodesWithChildren.addAll(childNodes);
        } catch (Exception e) {
          log.warn("Failed to get child nodes for node {}: {}", node.getNodeToken(), e.getMessage());
        }
      }
    }

    return allNodesWithChildren;
  }
  
  /**
   * Match POS users with Lark nodes.
   * Strategy:
   *   1. Phone exact match from title + wiki page body (exact phone in title is strongest)
   *   2. Phone loose match: any phone from body (fallback if not in title)
   *   3. Name fuzzy match (Levenshtein distance <= threshold)
   * Returns a map: PosUser -> LarkNode
   */
  public Map<PosUser, LarkNode> matchUsersWithNodes(List<PosUser> posUsers, HttpSession session) {
    Map<PosUser, LarkNode> matchedMap = new HashMap<>();

    try {
      List<LarkNode> allLarkNodes = getAllNodesForMatching(session);

      log.info("=== Matching POS Users with Lark Nodes ===");
      log.info("Total Lark nodes (with body content): {}", allLarkNodes.size());
      log.info("Total POS users: {}", posUsers.size());

      for (PosUser posUser : posUsers) {
        // Collect all potential phone numbers for this POS user
        java.util.Set<String> posUserPhones = new java.util.HashSet<>();
        
        // 1. Phone from POS phone_number field
        String fieldPhone = getPosUserPhone(posUser); 
        if (fieldPhone != null && !fieldPhone.isEmpty()) posUserPhones.add(fieldPhone);
        
        // 2. Phone extracted from POS Display Name (Stronger signal for mapping)
        String namePhone = extractFirstPhoneNumber(posUser.getName());
        if (namePhone != null && !namePhone.isEmpty()) posUserPhones.add(namePhone);

        String posUserName = posUser.getName();
        String posUserNameNorm = normalizeVietnameseName(posUserName);

        log.debug("POS User: {} - Candidate Phones: {}", posUserName, posUserPhones);

        LarkNode matchedNode = null;
        String matchReason = "";

        // === Strategy 1: Exact phone match from TITLE only (Try all candidate phones) ===
        if (!posUserPhones.isEmpty()) {
            for (String pPhone : posUserPhones) {
                matchedNode = allLarkNodes.stream()
                    .filter(node -> {
                        if (node.getTitle() == null) return false;
                        String nodePhone = extractFirstPhoneNumber(node.getTitle());
                        return pPhone.equals(nodePhone);
                    })
                    .findFirst()
                    .orElse(null);
                
                if (matchedNode != null) {
                    matchReason = "phone_in_title";
                    log.info("  [PHONE_TITLE] Matched '{}' with node '{}' (reason: candidate phone '{}' found in title)",
                        posUserName, matchedNode.getTitle(), pPhone);
                    break;
                }
            }
        }

        // === Strategy 2: Phone match from FULL content (title + body) ===
        if (matchedNode == null && !posUserPhones.isEmpty()) {
          for (LarkNode node : allLarkNodes) {
            String fullContent = getNodeFullContent(node, session);
            java.util.Set<String> larkPhones = extractAllPhoneNumbers(fullContent);
            
            for (String pPhone : posUserPhones) {
                if (larkPhones.contains(pPhone)) {
                    // Make sure it's not just a title match (already checked above)
                    String titlePhone = extractFirstPhoneNumber(node.getTitle());
                    if (!pPhone.equals(titlePhone)) {
                        matchedNode = node;
                        matchReason = "phone_in_body";
                        log.info("  [PHONE_BODY] Matched '{}' with node '{}' (reason: candidate phone '{}' found in body)",
                            posUserName, node.getTitle(), pPhone);
                        break;
                    }
                }
            }
            if (matchedNode != null) break;
          }
        }

        if (matchedNode == null && posUserPhones.isEmpty()) {
          log.debug("No phone numbers found for POS user: {}", posUserName);
        }

        // === Strategy 3: Fuzzy name match ===
        if (matchedNode == null && posUserName != null && !posUserName.isEmpty()) {
          LarkNode bestNameMatch = null;
          int bestScore = Integer.MAX_VALUE;
          int threshold = 3;

          for (LarkNode node : allLarkNodes) {
            if (node.getTitle() == null) continue;
            String nodeTitle = node.getTitle();
            String nodeTitleNorm = normalizeVietnameseName(nodeTitle);
            int dist = levenshteinDistance(posUserNameNorm, nodeTitleNorm);
            if (dist <= threshold && dist < bestScore) {
              bestScore = dist;
              bestNameMatch = node;
            }
          }

          if (bestNameMatch != null) {
            matchedNode = bestNameMatch;
            matchReason = "name_fuzzy";
            log.info("  [NAME_FUZZY] Matched '{}' with node '{}' (distance={})",
                posUserName, bestNameMatch.getTitle(), bestScore);
          }
        }

        // === Strategy 4: Name appears inside body (more permissive) ===
        if (matchedNode == null && posUserName != null && !posUserName.isEmpty()) {
          for (LarkNode node : allLarkNodes) {
            if (node.getTitle() == null) continue;
            // Only use body for additional context; skip if title already matched
            String body = node.getBodyContent();
            if (body == null) continue;
            String bodyNorm = normalizeVietnameseName(body);
            String nodeTitleNorm = normalizeVietnameseName(node.getTitle());
            // Check if POS name appears in body (normalized)
            if (bodyNorm.contains(posUserNameNorm) || posUserNameNorm.contains(nodeTitleNorm)) {
              matchedNode = node;
              matchReason = "name_in_body";
              log.info("  [NAME_BODY] Matched '{}' with node '{}' (name found in body)",
                  posUserName, node.getTitle());
              break;
            }
          }
        }

        if (matchedNode != null) {
          matchedMap.put(posUser, matchedNode);
        } else {
          log.info("  [NO_MATCH] No Lark node found for POS user: {} (phones={})", posUserName, posUserPhones);
        }
      }

      log.info("Total matches: {} / {}", matchedMap.size(), posUsers.size());
      log.info("=== End Matching ===");
    } catch (Exception e) {
      log.error("Error matching users with nodes: {}", e.getMessage(), e);
    }

    return matchedMap;
  }
  
  /**
   * Response wrapper for Lark Wiki API
   */
  private static class LarkNodesResponse {
    @JsonProperty("code")
    private int code;
    
    @JsonProperty("msg")
    private String msg;
    
    @JsonProperty("data")
    private LarkNodesData data;
    
    public int getCode() {
      return code;
    }
    
    public String getMsg() {
      return msg;
    }
    
    public LarkNodesData getData() {
      return data;
    }
  }

  private static class LarkNodesData {
    @JsonProperty("items")
    private List<LarkNode> items;

    @JsonProperty("page_token")
    private String pageToken;

    @JsonProperty("has_more")
    private boolean hasMore;

    public List<LarkNode> getItems() {
      return items != null ? items : Collections.emptyList();
    }

    public String getPageToken() {
      return pageToken;
    }

    public boolean isHasMore() {
      return hasMore;
    }
  }

  /**
   * Fetch wiki page body content for a node (node_token).
   * Implements: cache (per nodeToken) + throttling (120ms between calls) + retry (up to 3 attempts).
   * Returns empty string on failure.
   */
  private String fetchNodeBody(String nodeToken, HttpSession session) {
    if (nodeToken == null || nodeToken.isBlank()) return "";

    // 1. Cache check
    String cached = bodyCache.get(nodeToken);
    if (cached != null) {
      return cached;
    }

    // 2. Throttle: wait until minimum interval has passed since last call
    long now = System.currentTimeMillis();
    long elapsed = now - lastApiCallTime;
    if (elapsed < MIN_CALL_INTERVAL_MS) {
      try {
        Thread.sleep(MIN_CALL_INTERVAL_MS - elapsed);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
    lastApiCallTime = System.currentTimeMillis();

    // 3. Retry loop (up to 3 attempts, with backoff)
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        String accessToken = tokenService.getAccessToken(session, false);
        String url = String.format(
            "https://open.larksuite.com/open-apis/wiki/v2/spaces/%s/nodes/%s",
            LARK_SPACE_ID,
            nodeToken
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<NodeDetailResponse> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, NodeDetailResponse.class
        );

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null
            && response.getBody().code == 0 && response.getBody().data != null) {
          String text = response.getBody().data.toTextContent();
          bodyCache.put(nodeToken, text);
          return text;
        }

        // Non-OK or error code
        String errorDetail = response.getBody() != null ? response.getBody().msg : response.getStatusCode().toString();
        log.warn("fetchNodeBody attempt {} failed for node {}: {}", attempt, nodeToken, errorDetail);

      } catch (Exception e) {
        log.warn("fetchNodeBody attempt {} exception for node {}: {}", attempt, nodeToken, e.getMessage());
      }

      // If rate-limited or failed, wait before retry (exponential backoff)
      if (attempt < 3) {
        try {
          Thread.sleep(attempt * 500L); // 500ms, 1000ms backoff
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    // All attempts failed â€” cache empty string so we don't retry the same failed node
    bodyCache.put(nodeToken, "");
    return "";
  }

  /**
   * Full content for a node: title + wiki page body (lazy-loaded, cached).
   */
  private String getNodeFullContent(LarkNode node, HttpSession session) {
    if (node == null) return "";
    String body = node.getBodyContent();
    if (body == null) {
      body = fetchNodeBody(node.getNodeToken(), session);
      node.setBodyContent(body);
    }
    return (node.getTitle() != null ? node.getTitle() : "") + " " + body;
  }

  /**
   * Extract all phone numbers from a given text string.
   * Returns a Set to handle nodes that may have multiple phone numbers.
   */
  private java.util.Set<String> extractAllPhoneNumbers(String text) {
    java.util.Set<String> phones = new java.util.HashSet<>();
    if (text == null || text.isEmpty()) return phones;
    // Pattern: supports 0xxx... or +84xxx... with optional separators
    Pattern pattern = Pattern.compile("(?:\\+84|0)(?:3|5|7|8|9)[0-9]{8,9}");
    Matcher matcher = pattern.matcher(text.replaceAll("[\\s\\.\\-]", ""));
    while (matcher.find()) {
      String phone = matcher.group();
      if (phone.startsWith("+84")) phone = "0" + phone.substring(3);
      phones.add(phone.replaceAll("[^0-9]", ""));
    }
    return phones;
  }

  /**
   * Normalize a Vietnamese name for comparison.
   * Removes accents, extra spaces, and converts to lowercase.
   */
  private String normalizeVietnameseName(String name) {
    if (name == null) return "";
    // Decompose unicode, remove diacritics, then compose back
    String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
        .toLowerCase()
        .replaceAll("[^a-z0-9 ]", " ")
        .replaceAll("\\s+", " ")
        .trim();
    return normalized;
  }

  /**
   * Calculate Levenshtein distance between two strings.
   */
  private int levenshteinDistance(String a, String b) {
    if (a == null) a = "";
    if (b == null) b = "";
    int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
    for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
        dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
      }
    }
    return dp[a.length()][b.length()];
  }

  /**
   * Check if two normalized names are "close enough" using Levenshtein distance.
   * Threshold: max distance = 3 OR if one name contains the other.
   */
  private boolean isNameSimilar(String name1, String name2) {
    if (name1 == null || name2 == null) return false;
    String n1 = normalizeVietnameseName(name1);
    String n2 = normalizeVietnameseName(name2);
    if (n1.isEmpty() || n2.isEmpty()) return false;
    if (n1.equals(n2)) return true;
    if (n1.contains(n2) || n2.contains(n1)) return true;
    int maxLen = Math.max(n1.length(), n2.length());
    int threshold = Math.max(3, maxLen / 4); // adaptive threshold
    return levenshteinDistance(n1, n2) <= threshold;
  }

  // ================== node detail response ==================

  private static class NodeDetailResponse {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("data") NodeDetailData data;
  }

  private static class NodeDetailData {
    @JsonProperty("node") NodeInfo node;

    @SuppressWarnings("unchecked")
    String toTextContent() {
      if (node == null || node.root == null) return "";
      try {
        // The wiki body is a rich-text JSON; extract text from paragraphs
        return extractTextFromBlocks(node.root);
      } catch (Exception e) {
        return "";
      }
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromBlocks(Map<String, Object> block) {
      if (block == null) return "";
      StringBuilder sb = new StringBuilder();
      String type = (String) block.get("block_type");
      if ("2".equals(type) || "3".equals(type)) {
        // Paragraph or Heading
        Object children = block.get("children");
        if (children instanceof List<?>) {
          for (Object childId : (List<?>) children) {
            Map<String, Object> child = findBlockById((String) childId, node.root);
            if (child != null) {
              Object textList = child.get("text");
              if (textList instanceof List<?>) {
                for (Object t : (List<?>) textList) {
                  if (t instanceof Map) {
                    Map<String, Object> tm = (Map<String, Object>) t;
                    Object elements = tm.get("text_elements");
                    if (elements instanceof List<?>) {
                      for (Object e : (List<?>) elements) {
                        if (e instanceof Map) {
                          Map<String, Object> em = (Map<String, Object>) e;
                          Object td = em.get("text");
                          if (td instanceof Map) sb.append(((Map<String, Object>) td).get("content") != null ? ((Map<String, Object>) td).get("content") : "");
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        sb.append(" ");
      }
      // Recurse into children of this block
      Object children = block.get("children");
      if (children instanceof List<?>) {
        for (Object childId : (List<?>) children) {
          Map<String, Object> child = findBlockById((String) childId, node.root);
          if (child != null) sb.append(extractTextFromBlocks(child));
        }
      }
      return sb.toString();
    }

    private Map<String, Object> findBlockById(String id, Map<String, Object> block) {
      if (block == null || id == null) return null;
      if (id.equals(block.get("block_id"))) return block;
      Object children = block.get("children");
      if (children instanceof List<?>) {
        for (Object childId : (List<?>) children) {
          Map<String, Object> found = findBlockById((String) childId, node.root);
          if (found != null) return found;
        }
      }
      return null;
    }
  }

  private static class NodeInfo {
    @JsonProperty("root") Map<String, Object> root;
  }

  // ================== end of class ==================
}
