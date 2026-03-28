package mera.mera_v2.lark.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO cho Bitable search response
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitableSearchResponse {
    
    @JsonProperty("code")
    private Integer code;
    
    @JsonProperty("msg")
    private String msg;
    
    @JsonProperty("data")
    private SearchData data;
    
    public boolean isSuccess() {
        return code != null && code == 0;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchData {
        
        @JsonProperty("items")
        private List<SearchItem> items;
        
        @JsonProperty("has_more")
        private Boolean hasMore;
        
        @JsonProperty("page_token")
        private String pageToken;
        
        @JsonProperty("total")
        private Integer total;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchItem {
        
        @JsonProperty("record_id")
        private String recordId;
        
        @JsonProperty("fields")
        private Map<String, Object> fields;
    }
}
