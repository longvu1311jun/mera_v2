package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pos_sale_group_members")
@IdClass(PosSaleGroupMemberId.class)
public class PosSaleGroupMember {

    @Id
    @Column(name = "shop_user_id", length = 64, nullable = false)
    private String shopUserId;

    @Id
    @Column(name = "sale_group_id", nullable = false)
    private Integer saleGroupId;

    @Column(name = "permission", length = 32)
    private String permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private PosShopUser posShopUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_group_id", referencedColumnName = "id", insertable = false, updatable = false)
    private PosSaleGroup posSaleGroup;
}