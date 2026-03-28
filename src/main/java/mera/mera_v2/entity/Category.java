package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "shop_id")
    private Long shopId = 1546758L;

    @Column(name = "is_admin_category")
    private Integer isAdminCategory = 0;

    @Column(name = "nodes", columnDefinition = "LONGTEXT")
    private String nodes;

    @Column(name = "text", length = 500)
    private String text;

    @Column(name = "third_party", length = 255)
    private String thirdParty;

    @Column(name = "inserted_at")
    private LocalDateTime insertedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

