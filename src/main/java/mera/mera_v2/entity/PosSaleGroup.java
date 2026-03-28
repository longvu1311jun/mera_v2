package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pos_sale_groups")
public class PosSaleGroup {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;
}