package mera.mera_v2.lark.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class larkWikiResponse {
  private int code;
  private String msg;
  private WikiData data;

  @Data
  public static class WikiData {
    private NodeInfo node;
  }

  @Data
  public static class NodeInfo {
    @JsonProperty("obj_token")
    private String objToken;
    
    @JsonProperty("obj_type")
    private String objType;
    
    @JsonProperty("node_token")
    private String nodeToken;
    
    @JsonProperty("title")
    private String title;
  }
}