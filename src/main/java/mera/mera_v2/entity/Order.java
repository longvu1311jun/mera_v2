package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "order_code", length = 255)
    private String orderCode;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "page_id", length = 255)
    private String pageId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "conversation_id", length = 255)
    private String conversationId;

    @Column(name = "post_id", length = 255)
    private String postId;

    @Column(name = "ad_id", length = 128)
    private String adId;

    @Column(name = "creator_id", length = 64)
    private String creatorId;

    @Column(name = "assigning_seller_id", length = 64)
    private String assigningSellerId;

    @Column(name = "assigning_care_id", length = 64)
    private String assigningCareId;

    @Column(name = "marketer_id", length = 255)
    private String marketerId;

    @Column(name = "last_editor_id", length = 255)
    private String lastEditorId;

    @Column(name = "warehouse_id", length = 64)
    private String warehouseId;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "sub_status", length = 255)
    private String subStatus;

    @Column(name = "status_name", length = 255)
    private String statusName;

    @Column(name = "bill_full_name", length = 255)
    private String billFullName;

    @Column(name = "bill_phone_number", length = 20)
    private String billPhoneNumber;

    @Column(name = "bill_email", length = 255)
    private String billEmail;

    @Column(name = "shipping_full_name", length = 255)
    private String shippingFullName;

    @Column(name = "shipping_phone_number", length = 255)
    private String shippingPhoneNumber;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "shipping_full_address", columnDefinition = "TEXT")
    private String shippingFullAddress;

    @Column(name = "shipping_province_id", length = 255)
    private String shippingProvinceId;

    @Column(name = "shipping_province_name", length = 255)
    private String shippingProvinceName;

    @Column(name = "shipping_district_id", length = 255)
    private String shippingDistrictId;

    @Column(name = "shipping_district_name", length = 255)
    private String shippingDistrictName;

    @Column(name = "shipping_commune_id", length = 255)
    private String shippingCommuneId;

    @Column(name = "shipping_commune_name", length = 255)
    private String shippingCommuneName;

    @Column(name = "shipping_country_code", length = 255)
    private String shippingCountryCode;

    @Column(name = "shipping_post_code", length = 255)
    private String shippingPostCode;

    @Column(name = "order_sources")
    private Integer orderSources;

    @Column(name = "order_sources_name", length = 255)
    private String orderSourcesName;

    @Column(name = "ads_source", length = 64)
    private String adsSource;

    @Column(name = "p_utm_source", length = 255)
    private String pUtmSource;

    @Column(name = "p_utm_medium", length = 255)
    private String pUtmMedium;

    @Column(name = "p_utm_campaign", length = 255)
    private String pUtmCampaign;

    @Column(name = "p_utm_content", length = 255)
    private String pUtmContent;

    @Column(name = "p_utm_term", length = 255)
    private String pUtmTerm;

    @Column(name = "p_utm_id", length = 255)
    private String pUtmId;

    @Column(name = "is_livestream")
    private Integer isLivestream;

    @Column(name = "is_live_shopping")
    private Integer isLiveShopping;

    @Column(name = "total_price")
    private Double totalPrice;

    @Column(name = "total_price_after_sub_discount", precision = 18, scale = 4)
    private BigDecimal totalPriceAfterSubDiscount = BigDecimal.ZERO;

    @Column(name = "total_discount", precision = 18, scale = 4)
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", precision = 18, scale = 4)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "surcharge", precision = 18, scale = 4)
    private BigDecimal surcharge = BigDecimal.ZERO;

    @Column(name = "tax", precision = 18, scale = 4)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "cod", precision = 18, scale = 4)
    private BigDecimal cod;

    @Column(name = "money_to_collect", precision = 18, scale = 4)
    private BigDecimal moneyToCollect = BigDecimal.ZERO;

    @Column(name = "prepaid", precision = 18, scale = 4)
    private BigDecimal prepaid = BigDecimal.ZERO;

    @Column(name = "cash", precision = 18, scale = 4)
    private BigDecimal cash = BigDecimal.ZERO;

    @Column(name = "transfer_money", precision = 18, scale = 4)
    private BigDecimal transferMoney = BigDecimal.ZERO;

    @Column(name = "charged_by_momo", precision = 18, scale = 4)
    private BigDecimal chargedByMomo = BigDecimal.ZERO;

    @Column(name = "charged_by_card", precision = 18, scale = 4)
    private BigDecimal chargedByCard = BigDecimal.ZERO;

    @Column(name = "charged_by_qrpay", precision = 18, scale = 4)
    private BigDecimal chargedByQrpay = BigDecimal.ZERO;

    @Column(name = "exchange_payment", precision = 18, scale = 4)
    private BigDecimal exchangePayment = BigDecimal.ZERO;

    @Column(name = "exchange_value", precision = 18, scale = 4)
    private BigDecimal exchangeValue = BigDecimal.ZERO;

    @Column(name = "partner_fee", precision = 18, scale = 4)
    private BigDecimal partnerFee = BigDecimal.ZERO;

    @Column(name = "return_fee", precision = 38, scale = 2)
    private BigDecimal returnFee;

    @Column(name = "fee_marketplace", precision = 18, scale = 4)
    private BigDecimal feeMarketplace;

    @Column(name = "buyer_total_amount", precision = 18, scale = 4)
    private BigDecimal buyerTotalAmount;

    @Column(name = "levera_point")
    private Integer leveraPoint = 0;

    @Column(name = "is_free_shipping")
    private Integer isFreeShipping;

    @Column(name = "is_exchange_order")
    private Boolean isExchangeOrder;

    @Column(name = "is_calculation_tax")
    private Boolean isCalculationTax;

    @Column(name = "is_smc")
    private Integer isSmc;

    @Column(name = "customer_pay_fee")
    private Boolean customerPayFee;

    @Column(name = "received_at_shop")
    private Integer receivedAtShop;

    @Column(name = "partner", length = 255)
    private String partner;

    @Column(name = "tracking_link", columnDefinition = "TEXT")
    private String trackingLink;

    @Column(name = "time_send_partner")
    private LocalDateTime timeSendPartner;

    @Column(name = "estimate_delivery_date")
    private LocalDate estimateDeliveryDate;

    @Column(name = "returned_reason", length = 255)
    private String returnedReason;

    @Column(name = "returned_reason_name", length = 255)
    private String returnedReasonName;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "note_print", columnDefinition = "TEXT")
    private String notePrint;

    @Column(name = "link", length = 1024)
    private String link;

    @Column(name = "time_assign_seller")
    private LocalDateTime timeAssignSeller;

    @Column(name = "time_assign_care")
    private LocalDateTime timeAssignCare;

    @Column(name = "inserted_at", nullable = false)
    private LocalDateTime insertedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "lt_type", length = 255)
    private String ltType;

    @Column(name = "tick")
    private Integer tick;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "customer_address", columnDefinition = "TEXT")
    private String customerAddress;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "customer_phone", length = 255)
    private String customerPhone;

    @Column(name = "last_editor_name", length = 255)
    private String lastEditorName;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "partner_delivery_name", length = 255)
    private String partnerDeliveryName;

    @Column(name = "partner_tracking_id", length = 255)
    private String partnerTrackingId;

    @Column(name = "raw_data", columnDefinition = "LONGTEXT")
    private String rawData;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<OrderPayment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<OrderStatusHistory> statusHistories = new ArrayList<>();

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<OrderEditHistory> editHistories = new ArrayList<>();
}