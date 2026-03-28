package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", length = 255)
    private String productId;

    @Column(name = "variation_id", length = 64)
    private String variationId;

    @Column(name = "variation_name", length = 255)
    private String variationName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "retail_price")
    private Double retailPrice;

    @Column(name = "discount_each_product", precision = 18, scale = 4)
    private BigDecimal discountEachProduct = BigDecimal.ZERO;

    @Column(name = "is_discount_percent")
    private Integer isDiscountPercent;

    @Column(name = "same_price_discount", precision = 18, scale = 4)
    private BigDecimal samePriceDiscount = BigDecimal.ZERO;

    @Column(name = "total_discount")
    private Double totalDiscount;

    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "weight", precision = 10, scale = 2)
    private BigDecimal weight;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "note_product", columnDefinition = "TEXT")
    private String noteProduct;

    @Column(name = "is_bonus_product")
    private Integer isBonusProduct;

    @Column(name = "is_composite")
    private Integer isComposite;

    @Column(name = "is_wholesale")
    private Integer isWholesale;

    @Column(name = "one_time_product")
    private Integer oneTimeProduct;

    @Column(name = "return_quantity")
    private Integer returnQuantity = 0;

    @Column(name = "returning_quantity")
    private Integer returningQuantity = 0;

    @Column(name = "returned_count")
    private Integer returnedCount = 0;

    @Column(name = "exchange_count")
    private Integer exchangeCount = 0;

    @Column(name = "added_to_cart_quantity")
    private Integer addedToCartQuantity = 0;

    @Column(name = "composite_item_id", length = 255)
    private String compositeItemId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "product_name", length = 255)
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Order order;
}

