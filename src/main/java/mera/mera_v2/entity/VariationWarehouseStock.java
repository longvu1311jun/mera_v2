package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "variation_warehouse_stock")
@IdClass(VariationWarehouseStockId.class)
public class VariationWarehouseStock {

    @Id
    @Column(name = "variation_id", length = 64, nullable = false)
    private String variationId;

    @Id
    @Column(name = "warehouse_id", length = 64, nullable = false)
    private String warehouseId;

    @Column(name = "remain_quantity", nullable = false)
    private Integer remainQuantity = 0;

    @Column(name = "actual_remain_qty", nullable = false)
    private Integer actualRemainQty = 0;

    @Column(name = "pending_quantity", nullable = false)
    private Integer pendingQuantity = 0;

    @Column(name = "waiting_quantity", nullable = false)
    private Integer waitingQuantity = 0;

    @Column(name = "returning_quantity", nullable = false)
    private Integer returningQuantity = 0;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity = 0;

    @Column(name = "selling_avg", precision = 18, scale = 6, nullable = false)
    private BigDecimal sellingAvg = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variation_id", referencedColumnName = "id", insertable = false, updatable = false)
    private ProductVariation productVariation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Warehouse warehouse;
}

