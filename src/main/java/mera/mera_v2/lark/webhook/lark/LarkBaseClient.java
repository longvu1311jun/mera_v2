package mera.mera_v2.lark.webhook.lark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.config.LarkBaseProperties;
import mera.mera_v2.lark.webhook.config.SalesTablesConfig;
import mera.mera_v2.lark.webhook.model.CustomerRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LarkBaseClient {
  private final WebClient larkWebClient; // WebClient đã được config với buffer 20MB trong LarkWebClientConfig
  private final LarkBaseProperties props;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Lấy user access token do bạn nhập ở UI (u-....)
   */
  private String getAccessTokenForBitable() {
    String token = props.getUserAccessToken();
    if (token == null || token.isBlank()) {
      throw new IllegalStateException(
          "User access token is not set. Please input token on the report page.");
    }
    token = token.trim();
    // Chỉ log một phần token để debug, không log toàn bộ
    if (token.length() > 10) {
      log.debug("Using token: {}...", token.substring(0, 10));
    }
    return token;
  }

  /**
   * Gọi Bitable records/search cho 1 bảng (table + viewId đã fix trong SalesTablesConfig)
   */
  public List<CustomerRecord> fetchRecords(SalesTablesConfig.SalesTable table) {
      log.info("Fetching records for: {} (Table: {})", table.getDisplayName(), table.getTableId());

      String userToken = getAccessTokenForBitable();

      List<CustomerRecord> result = new ArrayList<>();
      String pageToken = null;

      do {
          // Giảm page_size để tránh response quá lớn, có thể tăng lại nếu cần
          String url = props.getBaseUrl()
                  + "/open-apis/bitable/v1/apps/"
                  + props.getAppToken()
                  + "/tables/"
                  + table.getTableId()
                  + "/records/search?page_size=200";

          if (pageToken != null && !pageToken.isEmpty()) {
              url += "&page_token=" + pageToken;
          }

          // 🟩 BODY — default filter lấy dữ liệu trong tháng (CurrentMonth)
          ObjectNode body = objectMapper.createObjectNode();
          body.put("automatic_fields", false);

          ArrayNode fieldNames = body.putArray("field_names");
          fieldNames.add("Ngày tạo");
          fieldNames.add("Trạng thái mess");

          body.put("view_id", table.getViewId());

          // 🟢 FILTER (NEW)
          ObjectNode filter = body.putObject("filter");
          filter.put("conjunction", "and");

          ArrayNode conditions = filter.putArray("conditions");

          ObjectNode cond = conditions.addObject();
          cond.put("field_name", "Ngày tạo");
          cond.put("operator", "is");

          // value phải là array -> dùng CurrentMonth
          ArrayNode v = cond.putArray("value");
          v.add("CurrentMonth");

          // 🟦 CALL BITABLE với timeout và error handling tốt hơn
          // Sử dụng larkWebClient đã được config với buffer size lớn (20MB)
          String rawJson;
          try {
              rawJson = larkWebClient.post()
                      .uri(url)
                      .header("Authorization", "Bearer " + userToken)
                      .header("Content-Type", "application/json")
                      .bodyValue(body)
                      .retrieve()
                      .bodyToMono(String.class)
                      .block();
              
              if (rawJson == null || rawJson.isEmpty()) {
                  log.error("Empty response from API for table: {}", table.getDisplayName());
                  throw new RuntimeException("Empty response from bitable API");
              }
          } catch (Exception e) {
              log.error("API call failed for table {} ({}): {}", 
                  table.getDisplayName(), table.getTableId(), e.getMessage());
              throw new RuntimeException("Failed to fetch records from bitable: " + e.getMessage(), e);
          }

          JsonNode root;
          try {
              root = objectMapper.readTree(rawJson);
          } catch (Exception e) {
              log.error("JSON parse error for table {}: {}", table.getDisplayName(), e.getMessage());
              throw new RuntimeException("Cannot parse JSON from bitable", e);
          }

          int code = root.path("code").asInt(-1);
          if (code != 0) {
              String errorMsg = root.path("msg").asText("Unknown error");
              log.error("API error for table {} ({}): code={}, msg={}", 
                  table.getDisplayName(), table.getTableId(), code, errorMsg);
              
              // Nếu là rate limit error (code 99991663 hoặc 429), throw exception đặc biệt
              if (code == 99991663 || code == 429 || errorMsg.toLowerCase().contains("rate limit")) {
                  throw new RuntimeException("Rate limit error, code = " + code + ", msg = " + errorMsg);
              }
              
              throw new RuntimeException("API error, code = " + code + ", msg = " + errorMsg);
          }

          JsonNode dataNode = root.path("data");
          JsonNode itemsNode = dataNode.path("items");

          if (itemsNode.isArray()) {
              for (JsonNode item : itemsNode) {
                  JsonNode fields = item.path("fields");
                  if (fields.isMissingNode()) continue;

                  // Ngày tạo trả về dạng timestamp
                  JsonNode createdNode = fields.path("Ngày tạo");
                  if (!createdNode.isNumber()) continue;

                  long createdMillis = createdNode.asLong();
                  LocalDate createdDate = Instant.ofEpochMilli(createdMillis)
                          .atZone(ZoneId.systemDefault())
                          .toLocalDate();

                  JsonNode statusNode = fields.path("Trạng thái mess");
                  String status = null;
                  if (statusNode.isArray() && statusNode.size() > 0) {
                      status = statusNode.get(0).asText();
                  }

                  CustomerRecord cr = new CustomerRecord(createdDate, status);
                  result.add(cr);
              }
          }

          boolean hasMore = dataNode.path("has_more").asBoolean(false);
          String nextToken = dataNode.path("page_token").asText(null);

          pageToken = (hasMore && nextToken != null && !nextToken.isEmpty()) ? nextToken : null;

      } while (pageToken != null);

      log.info("Fetched {} records for {}", result.size(), table.getDisplayName());
      return result;
  }

}
