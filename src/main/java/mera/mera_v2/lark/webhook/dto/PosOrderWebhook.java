package mera.mera_v2.lark.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * DTO cho webhook data từ POS (Pancake)
 * Hỗ trợ cả 2 format: data ở root level và data trong wrapper
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PosOrderWebhook {
    
    // Webhook có thể có wrapper: { "data": {...}, "success": true }
    @JsonProperty("data")
    private OrderData data;
    
    @JsonProperty("success")
    private Boolean success;
    
    // Các field ở root level (POS gửi trực tiếp)
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("shop_id")
    private Long shopId;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("event_type")
    private String eventType;
    
    @JsonProperty("status")
    private Integer status;
    
    @JsonProperty("customer")
    private CustomerInfo customer;
    
    @JsonProperty("order")
    private OrderInfo order;
    
    @JsonProperty("assigning_seller")
    private AssigningSeller assigningSeller;
    
    // Shipping address ở root level (POS gửi trực tiếp)
    @JsonProperty("shipping_address")
    private ShippingAddress shippingAddress;
    
    // Assigning care ở root level
    @JsonProperty("assigning_care")
    private AssigningSeller assigningCare;
    
    // Histories ở root level
    @JsonProperty("histories")
    private java.util.List<HistoryItem> histories;
    
    // Các field có thể nằm ở root level
    @JsonProperty("full_name")
    private String rootFullName;
    
    @JsonProperty("phone_number")
    private String rootPhoneNumber;
    
    @JsonProperty("full_address")
    private String rootFullAddress;
    
    @JsonProperty("Province_name")
    private String rootProvinceName;
    
    @JsonProperty("conversation_link")
    private String rootConversationLink;
    
    // Fallback fields ở root level
    @JsonProperty("name")
    private String rootName;
    
    @JsonProperty("phone")
    private String rootPhone;
    
    @JsonProperty("address")
    private String rootAddress;
    
    // Order source name (cho status = 6)
    @JsonProperty("order_sources_name")
    private String orderSourcesName;

    // Tags (để kiểm tra "Đồng bộ DATA")
    @JsonProperty("tags")
    private java.util.List<Tag> tags;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tag {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("name")
        private String name;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssigningSeller {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("email")
        private String email;
        
        @JsonProperty("phone_number")
        private String phoneNumber;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomerInfo {
        @JsonProperty("full_name")
        private String fullName;
        
        @JsonProperty("phone_number")
        private String phoneNumber;
        
        @JsonProperty("phone_numbers")
        private java.util.List<String> phoneNumbers;
        
        @JsonProperty("full_address")
        private String fullAddress;
        
        @JsonProperty("Province_name")
        private String provinceName;
        
        @JsonProperty("conversation_link")
        private String conversationLink;
        
        // Fallback fields
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("phone")
        private String phone;
        
        @JsonProperty("address")
        private String address;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderInfo {
        @JsonProperty("order_number")
        private String orderNumber;
        
        @JsonProperty("created_at")
        private Long createdAt;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderData {
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("status")
        private Integer status;
        
        @JsonProperty("assigning_seller")
        private AssigningSeller assigningSeller;

        @JsonProperty("assigning_care")
        private AssigningSeller assigningCare;
        
        @JsonProperty("shipping_address")
        private ShippingAddress shippingAddress;
        
        @JsonProperty("customer")
        private CustomerInfo customer;
        
        @JsonProperty("histories")
        private java.util.List<HistoryItem> histories;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShippingAddress {
        @JsonProperty("full_name")
        private String fullName;
        
        @JsonProperty("phone_number")
        private String phoneNumber;
        
        @JsonProperty("full_address")
        private String fullAddress;
        
        @JsonProperty("province_name")
        private String provinceName;
        
        @JsonProperty("address")
        private String address;
        
        @JsonProperty("new")
        private ShippingAddress newAddress;
        
        @JsonProperty("old")
        private ShippingAddress oldAddress;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistoryItem {
        @JsonProperty("shipping_address")
        private ShippingAddressHistory shippingAddress;

        @JsonProperty("account")
        private FieldChange account;

        @JsonProperty("status")
        private FieldChange status;

        @JsonProperty("order_sources")
        private FieldChange orderSources;

        @JsonProperty("page_id")
        private FieldChange pageId;

        @JsonProperty("assigning_care_id")
        private FieldChange assigningCareId;

        @JsonProperty("editor_id")
        private String editorId;

        @JsonProperty("updated_at")
        private String updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldChange {
        @JsonProperty("new")
        private Object newValue;

        @JsonProperty("old")
        private Object oldValue;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShippingAddressHistory {
        @JsonProperty("new")
        private ShippingAddress newAddress;
        
        @JsonProperty("old")
        private ShippingAddress oldAddress;
    }
    
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Map<String, Object> additionalData;

    /**
     * Kiểm tra xem đơn hàng có tag "Đồng bộ DATA" (id = 32) không
     */
    public boolean hasDongBoDataTag() {
        if (tags == null) {
            return false;
        }
        return tags.stream().anyMatch(tag ->
            tag.getId() != null && tag.getId() == 32
        );
    }

    /**
     * Kiểm tra xem đơn hàng có tag "Đồng bộ DATA" theo tên không
     */
    public boolean hasDongBoDataTagByName() {
        if (tags == null) {
            return false;
        }
        return tags.stream().anyMatch(tag ->
            "Đồng bộ DATA".equals(tag.getName())
        );
    }

    /**
     * Lấy history item mới nhất (dựa trên updated_at)
     */
    public HistoryItem getLatestHistory() {
        if (histories == null || histories.isEmpty()) {
            return null;
        }
        return histories.stream()
            .max((h1, h2) -> {
                String time1 = h1.getUpdatedAt() != null ? h1.getUpdatedAt() : "";
                String time2 = h2.getUpdatedAt() != null ? h2.getUpdatedAt() : "";
                // So sánh ISO 8601 datetime string (YYYY-MM-DDTHH:mm:ss.SSSZZZ)
                // String comparison works for ISO format
                return time1.compareTo(time2);
            })
            .orElse(null);
    }

    /**
     * Kiểm tra xem account có thay đổi trong history không
     * @return true nếu account thay đổi (new != old)
     */
    public boolean hasAccountChanged() {
        HistoryItem latestHistory = getLatestHistory();
        if (latestHistory == null || latestHistory.getAccount() == null) {
            return false;
        }

        FieldChange accountChange = latestHistory.getAccount();
        Object newValue = accountChange.getNewValue();
        Object oldValue = accountChange.getOldValue();

        // Nếu old là null và new không null, hoặc ngược lại, hoặc new != old
        if (oldValue == null && newValue != null) {
            return true;
        }
        if (oldValue != null && newValue == null) {
            // account bị gỡ
            return true;
        }

        if (newValue != null && oldValue != null) {
            return !newValue.equals(oldValue);
        }

        return false;
    }

    /**
     * Lấy giá trị account mới
     */
    public String getNewAccountValue() {
        HistoryItem latestHistory = getLatestHistory();
        if (latestHistory == null || latestHistory.getAccount() == null) {
            return null;
        }
        Object newValue = latestHistory.getAccount().getNewValue();
        return newValue != null ? newValue.toString() : null;
    }

    /**
     * Lấy giá trị account cũ
     */
    public String getOldAccountValue() {
        HistoryItem latestHistory = getLatestHistory();
        if (latestHistory == null || latestHistory.getAccount() == null) {
            return null;
        }
        Object oldValue = latestHistory.getAccount().getOldValue();
        return oldValue != null ? oldValue.toString() : null;
    }
}
