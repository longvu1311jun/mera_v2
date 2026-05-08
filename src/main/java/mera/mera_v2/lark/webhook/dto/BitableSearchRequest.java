package mera.mera_v2.lark.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO cho Bitable search request
 */
@Data
@Builder
public class BitableSearchRequest {
    
    @JsonProperty("automatic_fields")
    private Boolean automaticFields;
    
    @JsonProperty("field_names")
    private List<String> fieldNames;
    
    @JsonProperty("view_id")
    private String viewId;
    
    @JsonProperty("page_token")
    private String pageToken;
    
    @JsonProperty("page_size")
    private Integer pageSize;
    
    @JsonProperty("filter")
    private Filter filter;
    
    @Data
    @Builder
    public static class Filter {
        @JsonProperty("conjunction")
        private String conjunction; // "and" or "or"
        
        @JsonProperty("conditions")
        private List<Condition> conditions;
        
        @JsonProperty("children")
        private List<ChildFilter> children;
    }
    
    @Data
    @Builder
    public static class Condition {
        @JsonProperty("field_name")
        private String fieldName;
        
        @JsonProperty("operator")
        private String operator; // "is", "isNot", "contains", etc.
        
        @JsonProperty("value")
        private List<String> value;
    }
    
    @Data
    @Builder
    public static class ChildFilter {
        @JsonProperty("children")
        private List<ChildFilter> children;
        
        @JsonProperty("conditions")
        private List<Condition> conditions;
        
        @JsonProperty("conjunction")
        private String conjunction;
    }
}
