package mera.mera_v2.lark.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class larkBitableTablesResponse {
  private int code;
  private String msg;
  private TablesData data;

  @Data
  public static class TablesData {
    private List<TableInfo> items;
    
    @JsonProperty("page_token")
    private String pageToken;
    
    @JsonProperty("has_more")
    private boolean hasMore;
    
    @JsonProperty("total")
    private int total;
  }

  @Data
  public static class TableInfo {
    @JsonProperty("table_id")
    private String tableId;
    
    @JsonProperty("revision")
    private int revision;
    
    @JsonProperty("name")
    private String name;
  }
}