package mera.mera_v2.lark.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PancakeOrderResponse {
    private Boolean success;
    private List<PancakeOrder> data;
    private Integer pageNumber;
    private Integer pageSize;
    private Integer totalEntries;
    private Integer totalPages;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PancakeOrder {
        @JsonDeserialize(using = CustomDeserializers.FlexibleLongDeserializer.class)
        private Long systemId;

        private String id;

        @JsonDeserialize(using = CustomDeserializers.FlexibleLongDeserializer.class)
        private Long shopId;

        @JsonDeserialize(using = CustomDeserializers.FlexibleIntegerDeserializer.class)
        private Integer status;

        private String statusName;
        private String note;
        private String insertedAt;
        private String updatedAt;

        private Customer customer;
        private List<Item> items;

        @JsonDeserialize(using = CustomDeserializers.StatusHistoryDeserializer.class)
        private List<StatusHistory> statusHistory;

        private List<History> histories;
        private User creator;
        private User assigningSeller;
        private User assigningCare;
        private User marketer;
        private ShippingAddress shippingAddress;

        // Basic fields
        private String orderCode;
        private String pageId;
        private String conversationId;
        private String postId;
        private String adId;
        private String subStatus;
        private String billFullName;
        private String billPhoneNumber;
        private String billEmail;

        private Long orderSources;

        private String orderSourcesName;
        private String adsSource;
        private String link;
        private String trackingLink;
        private String returnedReason;
        private String returnedReasonName;
        private String estimateDeliveryDate;
        private String timeSendPartner;
        private String timeAssignSeller;
        private String timeAssignCare;

        // Money fields
        private Double totalPrice;
        private Double totalPriceAfterSubDiscount;
        private Double totalDiscount;
        private Double shippingFee;
        private Double surcharge;
        private Double tax;
        private Double cod;
        private Double moneyToCollect;
        private Double prepaid;
        private Double cash;
        private Double transferMoney;
        private Double chargedByMomo;
        private Double chargedByCard;
        private Double chargedByQrpay;
        private Double exchangePayment;
        private Double exchangeValue;
        private Double partnerFee;

        @JsonDeserialize(using = CustomDeserializers.ReturnFeeDeserializer.class)
        private Double returnFee;

        private Double feeMarketplace;
        private Double buyerTotalAmount;

        // Boolean fields
        private Boolean isLivestream;
        private Boolean isLiveShopping;
        private Boolean isFreeShipping;
        private Boolean isExchangeOrder;
        private Boolean isCalculationTax;
        private Boolean isSmc;
        private Boolean customerPayFee;
        private Boolean receivedAtShop;

        // UTM
        private String pUtmSource;
        private String pUtmMedium;
        private String pUtmCampaign;
        private String pUtmContent;
        private String pUtmTerm;
        private String pUtmId;

        // Other
        @JsonDeserialize(using = CustomDeserializers.FlexibleIntegerDeserializer.class)
        private Integer totalQuantity;

        @JsonDeserialize(using = CustomDeserializers.FlexibleIntegerDeserializer.class)
        private Integer leveraPoint;

        private Object partner;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Customer {
        private String customerId;
        private String id;
        private String name;
        private String gender;
        private String dateOfBirth;
        private String fbId;
        private String referralCode;
        private String customerReferralCode;

        @JsonDeserialize(using = CustomDeserializers.FlexibleIntegerDeserializer.class)
        private Integer orderCount;

        @JsonDeserialize(using = CustomDeserializers.FlexibleIntegerDeserializer.class)
        private Integer succeedOrderCount;

        @JsonDeserialize(using = CustomDeserializers.FlexibleIntegerDeserializer.class)
        private Integer returnedOrderCount;

        private Double purchasedAmount;
        private String lastOrderAt;
        private String insertedAt;
        private String updatedAt;
        private List<String> phoneNumbers;
        private List<ShopCustomerAddress> shopCustomerAddresses;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ShopCustomerAddress {
        private String id;
        private String fullName;
        private String phoneNumber;
        private String address;
        private String fullAddress;
        private String provinceId;
        private String provinceName;
        private String districtId;
        private String districtName;
        private String communeId;
        private String communeName;
        private String countryCode;
        private String postCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Item {
        @JsonDeserialize(using = CustomDeserializers.FlexibleLongDeserializer.class)
        private Long id;

        private String productId;
        private String variationId;
        private String variationName;

        @JsonDeserialize(using = CustomDeserializers.FlexibleIntegerDeserializer.class)
        private Integer quantity;

        private Double retailPrice;
        private Double discountEachProduct;
        private Boolean isDiscountPercent;
        private Double samePriceDiscount;
        private Double totalDiscount;
        private Double taxRate;
        private Double weight;
        private String note;
        private String noteProduct;
        private Boolean isBonusProduct;
        private Boolean isComposite;
        private Boolean isWholesale;
        private VariationInfo variationInfo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VariationInfo {
        private String name;
        private Double retailPrice;
        private Double weight;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class StatusHistory {
        @JsonDeserialize(using = CustomDeserializers.FlexibleIntegerDeserializer.class)
        private Integer status;

        @JsonDeserialize(using = CustomDeserializers.FlexibleIntegerDeserializer.class)
        private Integer oldStatus;

        private String updatedAt;
        private String editorId;
        private Object editor;
        private String editorFb;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class History {
        private String field;
        private Object oldValue;
        private Object newValue;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class User {
        private String id;
        private String name;
        private String email;
        private String fbId;
        private String phoneNumber;
        private String avatarUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ShippingAddress {
        private String fullName;
        private String phoneNumber;
        private String address;
        private String fullAddress;
        private String provinceId;
        private String provinceName;
        private String districtId;
        private String districtName;
        private String communeId;
        private String communeName;
        private String countryCode;
        private String postCode;
    }
}
