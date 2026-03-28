package mera.mera_v2.lark.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitableRecordRequest {
    
    @JsonProperty("fields")
    private Map<String, Object> fields;
    
    public static class FieldValue {
        public static Object text(String value) {
            return value;
        }
        
        public static Object number(Number value) {
            return value;
        }
        
        public static Object singleSelect(String value) {
            return List.of(value);
        }
        
        public static Object multiSelect(List<String> values) {
            return values;
        }
        
        public static Object person(String userId) {
            return Map.of("id", userId);
        }
        
        public static Object persons(List<String> userIds) {
            return userIds.stream()
                    .map(id -> Map.of("id", id))
                    .toList();
        }
        
        public static Object date(long timestampMs) {
            return timestampMs;
        }
        
        /**
         * URL field format cho Bitable
         * Có thể là:
         * 1. String URL đơn giản: "https://example.com"
         * 2. Object với link và text: {"link": "url", "text": "text"}
         * 
         * Thử dùng string trước vì đơn giản hơn
         */
        public static Object url(String url) {
            if (url == null || url.isBlank()) {
                return null;
            }
            // Thử dùng string URL trước (format đơn giản nhất)
            return url;
        }
        
        /**
         * URL field với text tùy chỉnh (object format)
         */
        public static Object url(String url, String text) {
            if (url == null || url.isBlank()) {
                return null;
            }
            // Nếu có text, dùng object format
            if (text != null && !text.isBlank()) {
                return Map.of("link", url, "text", text);
            }
            // Nếu không có text, chỉ dùng string URL
            return url;
        }
        
        public static Object nullValue() {
            return null;
        }
    }
}
