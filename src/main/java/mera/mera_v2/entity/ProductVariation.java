package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "product_variations")
public class ProductVariation {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "display_id", length = 255)
    private String displayId;

    @Column(name = "barcode", length = 255)
    private String barcode;

    @Column(name = "retail_price", precision = 18, scale = 4, nullable = false)
    private BigDecimal retailPrice = BigDecimal.ZERO;

    @Column(name = "retail_price_original", precision = 18, scale = 4)
    private BigDecimal retailPriceOriginal;

    @Column(name = "avg_price", precision = 18, scale = 4, nullable = false)
    private BigDecimal avgPrice = BigDecimal.ZERO;

    @Column(name = "last_imported_price", precision = 18, scale = 4, nullable = false)
    private BigDecimal lastImportedPrice = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "weight", precision = 10, scale = 2)
    private BigDecimal weight;

    @Column(name = "is_upsale_product")
    private Integer isUpsaleProduct;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_hidden")
    private Integer isHidden;

    @Column(name = "is_locked")
    private Integer isLocked;

    @Column(name = "is_sell_negative")
    private Integer isSellNegative;

    @Column(name = "average_imported_price", precision = 18, scale = 4)
    private BigDecimal averageImportedPrice;

    @Column(name = "inserted_at")
    private LocalDateTime insertedAt;

    @Column(name = "is_composite")
    private Boolean isComposite;

    @Column(name = "is_removed")
    private Boolean isRemoved;

    @Column(name = "is_sell_negative_variation")
    private Boolean isSellNegativeVariation;

    @Column(name = "price_at_counter", precision = 18, scale = 4)
    private BigDecimal priceAtCounter;

    @Column(name = "remain_quantity")
    private Integer remainQuantity;

    @Column(name = "retail_price_after_discount", precision = 18, scale = 4)
    private BigDecimal retailPriceAfterDiscount;

    @Column(name = "total_purchase_price", precision = 18, scale = 4)
    private BigDecimal totalPurchasePrice;

    @Column(name = "wholesale_price", precision = 18, scale = 4)
    private BigDecimal wholesalePrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Product product;
}

