package mera.mera_v2.lark.webhook.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailViewModel {

    // Bảng 1: Thông tin đơn hàng
    private OrderInfo orderInfo;

    // Bảng 2: Khách hàng
    private CustomerInfo customer;

    // Bảng 3: Địa chỉ khách hàng
    private List<AddressInfo> addresses;

    // Bảng 4: Sản phẩm trong đơn
    private List<ItemInfo> items;

    // Bảng 5: Lịch sử trạng thái
    private List<StatusHistoryInfo> statusHistories;

    // Bảng 6: Lịch sử chỉnh sửa
    private List<EditHistoryInfo> editHistories;

    // Bảng 7: Nhân viên liên quan
    private List<UserInfo> users;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderInfo {
        private Long id;
        private String orderCode;
        private Long shopId;
        private Integer status;
        private String statusName;
        private String billFullName;
        private String billPhoneNumber;
        private String billEmail;
        private String shippingFullName;
        private String shippingPhoneNumber;
        private String shippingFullAddress;
        private String shippingProvinceName;
        private String shippingDistrictName;
        private String shippingCommuneName;
        private BigDecimal totalPrice;
        private BigDecimal totalDiscount;
        private BigDecimal shippingFee;
        private BigDecimal tax;
        private BigDecimal cod;
        private BigDecimal moneyToCollect;
        private String orderSourcesName;
        private String adsSource;
        private String note;
        private String creatorId;
        private String assigningSellerId;
        private String assigningCareId;
        private String marketerId;
        private String warehouseId;
        private LocalDateTime insertedAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        private String customerId;
        private String name;
        private String gender;
        private String phoneNumbers;
        private String fbId;
        private Integer orderCount;
        private Integer succeedOrderCount;
        private BigDecimal purchasedAmount;
        private Integer rewardPoint;
        private Boolean isBlock;
        private LocalDateTime insertedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressInfo {
        private String id;
        private String fullName;
        private String phoneNumber;
        private String address;
        private String fullAddress;
        private String provinceId;
        private String districtId;
        private String communeId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemInfo {
        private Long itemId;
        private String variationId;
        private String variationName;
        private Integer quantity;
        private BigDecimal retailPrice;
        private BigDecimal taxRate;
        private BigDecimal weight;
        private BigDecimal discountEachProduct;
        private BigDecimal totalDiscount;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryInfo {
        private Long orderId;
        private Integer oldStatus;
        private Integer newStatus;
        private String editorId;
        private String editorName;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EditHistoryInfo {
        private Long orderId;
        private String editorId;
        private String fieldChanged;
        private String oldValue;
        private String newValue;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String name;
        private String email;
        private String fbId;
        private String phoneNumber;
        private String role;
    }
}
