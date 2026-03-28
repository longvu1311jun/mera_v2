package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "pos_shop_users")
public class PosShopUser {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "role", length = 64)
    private String role;

    @Column(name = "permission_in_sale_group", length = 32)
    private String permissionInSaleGroup;

    @Column(name = "is_assigned", nullable = false)
    private Boolean isAssigned = false;

    @Column(name = "enable_api", nullable = false)
    private Boolean enableApi = false;

    @Column(name = "api_key", length = 64)
    private String apiKey;

    @Column(name = "note_api_key", length = 255)
    private String noteApiKey;

    @Column(name = "is_api_key", nullable = false)
    private Boolean isApiKey = false;

    @Column(name = "pending_order_count", nullable = false)
    private Integer pendingOrderCount = 0;

    @Column(name = "preferred_shop")
    private Integer preferredShop;

    @Column(name = "app_warehouse", length = 64)
    private String appWarehouse;

    @Column(name = "creator_id", length = 64)
    private String creatorId;

    @Column(name = "profile_id", length = 64)
    private String profileId;

    @Column(name = "inserted_at")
    private LocalDateTime insertedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private PosUser posUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", referencedColumnName = "id", insertable = false, updatable = false)
    private PosDepartment department;
}