package mera.mera_v2.lark.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO cho Bitable batch update request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitableBatchUpdateRequest {
    
    @JsonProperty("records")
    private List<UpdateRecord> records;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRecord {
        @JsonProperty("record_id")
        private String recordId;
        
        @JsonProperty("fields")
        private Map<String, Object> fields;
    }
}
