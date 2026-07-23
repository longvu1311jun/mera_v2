package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OrderApiDto {

  @JsonIgnore
  private String rawData;

  // === Order Identity ===
  private String id;

  @JsonProperty("system_id")
  private Long systemId;

  @JsonProperty("order_code")
  private String orderCode;

  @JsonProperty("shop_id")
  private Long shopId;

  @JsonProperty("page_id")
  private String pageId;

  @JsonProperty("ad_id")
  private String adId;

  // === Nested objects ===
  private CustomerDTO customer;

  @JsonProperty("shipping_address")
  private ShippingAddressDTO shippingAddress;

  private WarehouseDTO warehouseInfo;

  private PageDTO page;

  // === Customer (extracted from nested) ===
  @JsonProperty("customer_id")
  private String customerId;

  // === Order Info ===
  private Integer status;

  @JsonProperty("status_name")
  private String statusName;

  @JsonProperty("sub_status")
  private String subStatus;

  // API thường trả số, nhưng đơn marketplace trả chuỗi kiểu "pzl_332931694312735907" —
  // cột DB là BIGINT nên id dạng chuỗi đành bỏ (order_sources_name vẫn giữ tên nguồn).
  @JsonIgnore
  private Long orderSourcesId;

  @JsonProperty("order_sources")
  public void setOrderSources(String raw) {
    try {
      this.orderSourcesId = (raw == null || raw.isBlank()) ? null : Long.valueOf(raw.trim());
    } catch (NumberFormatException e) {
      this.orderSourcesId = null;
    }
  }

  public Long getOrderSources() {
    return orderSourcesId;
  }

  @JsonProperty("order_sources_name")
  private String orderSourcesName;

  @JsonProperty("ads_source")
  private String adsSource;

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

  @JsonProperty("duplicated_phone")
  private Boolean duplicatedPhone;

  // === Bill Info ===
  @JsonProperty("bill_full_name")
  private String billFullName;

  @JsonProperty("bill_phone_number")
  private String billPhoneNumber;

  @JsonProperty("bill_email")
  private String billEmail;

  // === Shipping Address (flat fields) ===
  @JsonProperty("shipping_full_name")
  private String shippingFullName;

  @JsonProperty("shipping_phone_number")
  private String shippingPhoneNumber;

  @JsonProperty("shipping_full_address")
  private String shippingFullAddress;

  @JsonProperty("shipping_province_id")
  private String shippingProvinceId;

  @JsonProperty("shipping_province_name")
  private String shippingProvinceName;

  @JsonProperty("shipping_district_id")
  private String shippingDistrictId;

  @JsonProperty("shipping_district_name")
  private String shippingDistrictName;

  @JsonProperty("shipping_commune_id")
  private String shippingCommuneId;

  @JsonProperty("shipping_commune_name")
  private String shippingCommuneName;

  @JsonProperty("shipping_country_code")
  private String shippingCountryCode;

  @JsonProperty("shipping_post_code")
  private String shippingPostCode;

  // === Warehouse (flat) ===
  @JsonProperty("warehouse_id")
  private String warehouseId;

  // === Money ===
  @JsonProperty("total_price")
  private Double totalPrice;

  @JsonProperty("total_price_after_sub_discount")
  private Double totalPriceAfterSubDiscount;

  @JsonProperty("total_discount")
  private Double totalDiscount;

  @JsonProperty("shipping_fee")
  private Double shippingFee;

  private Double surcharge;

  private Double tax;

  private Double cod;

  @JsonProperty("money_to_collect")
  private Double moneyToCollect;

  private Double prepaid;

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

  @JsonProperty("return_fee")
  private Object returnFee;

  @JsonProperty("fee_marketplace")
  private Double feeMarketplace;

  @JsonProperty("buyer_total_amount")
  private Double buyerTotalAmount;

  @JsonProperty("total_quantity")
  private Double totalQuantity;

  @JsonProperty("levera_point")
  private Double leveraPoint;

  // === Partner / Tracking ===
  private Object partner;

  @JsonProperty("tracking_link")
  private String trackingLink;

  @JsonProperty("time_send_partner")
  private String timeSendPartner;

  @JsonProperty("estimate_delivery_date")
  private String estimateDeliveryDate;

  @JsonProperty("returned_reason")
  private String returnedReason;

  @JsonProperty("returned_reason_name")
  private String returnedReasonName;

  @JsonProperty("order_link")
  private String orderLink;

  // === Note ===
  private String note;

  @JsonProperty("note_print")
  private String notePrint;

  private String link;

  // === Items ===
  private List<OrderItemApiDto> items;

  // === UTM ===
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

  @JsonProperty("customer_referral_code")
  private String customerReferralCode;

  // === User IDs ===
  @JsonProperty("creator_id")
  private String creatorId;

  @JsonProperty("assigning_seller_id")
  private String assigningSellerId;

  @JsonProperty("assigning_care_id")
  private String assigningCareId;

  @JsonProperty("marketer_id")
  private String marketerId;

  @JsonProperty("last_editor_id")
  private String lastEditorId;

  @JsonProperty("conversation_id")
  private String conversationId;

  @JsonProperty("post_id")
  private String postId;

  private String account;

  // === Nested objects ===
  private CreatorDTO creator;

  // === Times (String táº¡m thá»i, parse sang LocalDateTime á»Ÿ bÆ°á»›c service) ===
  @JsonProperty("inserted_at")
  private String insertedAt;

  @JsonProperty("updated_at")
  private String updatedAt;

  @JsonProperty("time_assign_seller")
  private String timeAssignSeller;

  @JsonProperty("time_assign_care")
  private String timeAssignCare;

  // === Response metadata ===
  @JsonProperty("account_name")
  private String accountName;

  // === Status History ===
  private List<StatusHistoryDto> statusHistory;
}
