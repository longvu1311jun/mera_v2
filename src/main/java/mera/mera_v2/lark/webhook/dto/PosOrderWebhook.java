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
    
    @JsonProperty("inserted_at")
    private String insertedAt;
    
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

    // === Page & Account ===
    @JsonProperty("page_id")
    private String pageId;

    @JsonProperty("account_name")
    private String accountName;

    // === Warehouse ===
    @JsonProperty("warehouse_id")
    private String warehouseId;

    private WarehouseInfo warehouseInfo;

    // === Page Info ===
    private PageInfo page;

    // === Money Fields ===
    @JsonProperty("total_price")
    private Double totalPrice;

    @JsonProperty("total_price_after_sub_discount")
    private Double totalPriceAfterSubDiscount;

    @JsonProperty("total_discount")
    private Double totalDiscount;

    @JsonProperty("cod")
    private Double cod;

    @JsonProperty("prepaid")
    private Double prepaid;

    @JsonProperty("shipping_fee")
    private Double shippingFee;

    private Double surcharge;

    private Double tax;

    @JsonProperty("money_to_collect")
    private Double moneyToCollect;

    private Double cash;

    @JsonProperty("transfer_money")
    private Double transferMoney;

    @JsonProperty("charged_by_momo")
    private Double chargedByMomo;

    @JsonProperty("charged_by_card")
    private Double chargedByCard;

    @JsonProperty("charged_by_qrpay")
    private Double chargedByQrpay;

    @JsonProperty("exchange_payment")
    private Double exchangePayment;

    @JsonProperty("exchange_value")
    private Double exchangeValue;

    @JsonProperty("partner_fee")
    private Double partnerFee;

    @JsonProperty("fee_marketplace")
    private Double feeMarketplace;

    @JsonProperty("buyer_total_amount")
    private Double buyerTotalAmount;

    @JsonProperty("levera_point")
    private Double leveraPoint;

    // === User IDs ===
    @JsonProperty("creator_id")
    private String creatorId;

    @JsonProperty("assigning_seller_id")
    private String assigningSellerId;

    @JsonProperty("assigning_care_id")
    private String assigningCareId;

    @JsonProperty("last_editor_id")
    private String lastEditorId;

    @JsonProperty("marketer_id")
    private String marketerId;

    @JsonProperty("ad_id")
    private String adId;

    // === Tracking & Link ===
    @JsonProperty("order_link")
    private String orderLink;

    @JsonProperty("tracking_link")
    private String trackingLink;

    @JsonProperty("link")
    private String link;

    // === Reason ===
    @JsonProperty("returned_reason")
    private String returnedReason;

    @JsonProperty("returned_reason_name")
    private String returnedReasonName;

    // === Boolean Flags ===
    @JsonProperty("is_livestream")
    private Boolean isLivestream;

    @JsonProperty("is_live_shopping")
    private Boolean isLiveShopping;

    @JsonProperty("is_exchange_order")
    private Boolean isExchangeOrder;

    @JsonProperty("is_free_shipping")
    private Boolean isFreeShipping;

    @JsonProperty("is_smc")
    private Boolean isSmc;

    @JsonProperty("is_calculation_tax")
    private Boolean isCalculationTax;

    @JsonProperty("customer_pay_fee")
    private Boolean customerPayFee;

    @JsonProperty("received_at_shop")
    private Boolean receivedAtShop;

    // === Note ===
    private String note;

    @JsonProperty("note_print")
    private String notePrint;

    // === UTM Fields ===
    @JsonProperty("p_utm_source")
    private String pUtmSource;

    @JsonProperty("p_utm_medium")
    private String pUtmMedium;

    @JsonProperty("p_utm_campaign")
    private String pUtmCampaign;

    @JsonProperty("p_utm_content")
    private String pUtmContent;

    @JsonProperty("p_utm_term")
    private String pUtmTerm;

    @JsonProperty("p_utm_id")
    private String pUtmId;

    // === Times ===
    @JsonProperty("time_assign_seller")
    private String timeAssignSeller;

    @JsonProperty("time_assign_care")
    private String timeAssignCare;

    @JsonProperty("time_send_partner")
    private String timeSendPartner;

    // === Bill Info ===
    @JsonProperty("bill_full_name")
    private String billFullName;

    @JsonProperty("bill_phone_number")
    private String billPhoneNumber;

    @JsonProperty("bill_email")
    private String billEmail;

    // === Conversation ===
    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("post_id")
    private String postId;

    @JsonProperty("order_sources")
    private Long orderSources;

    @JsonProperty("sub_status")
    private String subStatus;

    @JsonProperty("items_length")
    private Integer itemsLength;

    // === Creator object (for extracting info) ===
    private CreatorInfo creator;

    // === Last Editor object ===
    @JsonProperty("last_editor")
    private CreatorInfo lastEditor;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WarehouseInfo {
        private String id;
        private String name;
        private String phoneNumber;
        private String address;
        private String fullAddress;

        @JsonProperty("commune_id")
        private String communeId;

        @JsonProperty("district_id")
        private String districtId;

        @JsonProperty("province_id")
        private String provinceId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        @JsonProperty("id")
        private String id;

        private String name;
        private String username;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreatorInfo {
        private String id;
        private String name;
        private String email;
        private String phoneNumber;

        @JsonProperty("fb_id")
        private String fbId;

        @JsonProperty("avatar_url")
        private String avatarUrl;
    }

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
        
        @JsonProperty("inserted_at")
        private String insertedAt;
        
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

        @JsonProperty("district_name")
        private String districtName;

        @JsonProperty("commune_name")
        private String communeName;

        @JsonProperty("province_id")
        private String provinceId;

        @JsonProperty("district_id")
        private String districtId;

        @JsonProperty("commune_id")
        private String communeId;

        @JsonProperty("country_code")
        private String countryCode;

        @JsonProperty("post_code")
        private String postCode;

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
