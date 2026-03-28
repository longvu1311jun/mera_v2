package mera.mera_v2.lark.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BitableRecordResponse {
    
    @JsonProperty("code")
    private Integer code;
    
    @JsonProperty("msg")
    private String msg;
    
    @JsonProperty("data")
    private RecordData data;
    
    @Data
    public static class RecordData {
        @JsonProperty("record")
        private Record record;
    }
    
    @Data
    public static class Record {
        @JsonProperty("record_id")
        private String recordId;
        
        @JsonProperty("fields")
        private Object fields;
    }
    
    public boolean isSuccess() {
        return code != null && code == 0;
    }
}
