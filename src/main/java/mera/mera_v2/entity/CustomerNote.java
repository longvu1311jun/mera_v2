package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "customer_notes")
public class CustomerNote {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "customer_id", length = 64, nullable = false)
    private String customerId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "created_by_id", length = 64)
    private String createdById;

    @Column(name = "created_by_name", length = 255)
    private String createdByName;

    @Column(name = "created_by_pancake_id", length = 64)
    private String createdByPancakeId;

    @Column(name = "created_by_token", length = 255)
    private String createdByToken;

    @Column(name = "images", columnDefinition = "JSON")
    private String images;

    @Column(name = "links", columnDefinition = "JSON")
    private String links;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Customer customer;
}