package mera.mera_v2.lark.webhook.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.auth.larkAuthService;
import mera.mera_v2.lark.auth.larkBitableTablesResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@Getter
@Slf4j
public class SalesTablesConfig {
  
  @Autowired(required = false)
  private larkAuthService larkAuthService;
  
  @Autowired
  private LarkBaseProperties larkProps;

  private volatile List<SalesTable> tables = new ArrayList<>();

  /**
   * Refresh tables với token từ config (fallback)
   */
  public void refreshTables() {
    String userToken = larkProps.getUserAccessToken();
    String appToken = larkProps.getAppToken();
    refreshTables(userToken, appToken);
  }

  /**
   * Refresh tables với token được truyền vào (ưu tiên)
   * @param userAccessToken User access token
   * @param appToken App token (base ID)
   */
  public void refreshTables(String userAccessToken, String appToken) {
    try {
      if (userAccessToken == null || userAccessToken.isBlank() || 
          appToken == null || appToken.isBlank() ||
          larkAuthService == null) {
        log.warn("Cannot refresh tables: missing token or auth service");
        return;
      }

      log.info("Refreshing tables from API with provided tokens...");
      List<larkBitableTablesResponse.TableInfo> tableInfos = 
          larkAuthService.getTablesFilteredByName(userAccessToken, appToken);
      
      // Convert sang SalesTable format
      List<SalesTable> newTables = tableInfos.stream()
          .map(info -> new SalesTable(
              info.getName(), 
              info.getTableId(), 
              "vewE3Ope6x" // viewId mặc định, có thể lấy từ API nếu cần
          ))
          .collect(Collectors.toList());
      
      tables = newTables;
      log.info("Loaded {} tables from API", tables.size());
      
      // Kiểm tra các table ID bị lỗi có trong danh sách không
      String[] errorTableIds = {
          "tbl35gdJGRs3GZGJ", "tbl3ig26TdAzwmTI", "tblbKuNFnOFhpBVf",
          "tbl8gdEU158IHJLN", "tblxwBOZh9ZAYsyE", "tblk9aRTpSWjhqt0", "tblJXl2F4m68R8z7"
      };
      for (String errorTableId : errorTableIds) {
          boolean found = newTables.stream()
              .anyMatch(t -> t.getTableId().equals(errorTableId));
          if (!found) {
              log.warn("ERROR TABLE ID NOT FOUND IN API: {}", errorTableId);
          } else {
              log.info("Found error table ID in API: {}", errorTableId);
          }
      }
      
    } catch (Exception e) {
      log.error("Error refreshing tables from API", e);
    }
  }

  @Data
  @AllArgsConstructor
  public static class SalesTable {
    private String displayName;
    private String tableId;
    private String viewId;
  }

}
