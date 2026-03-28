package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "product_substitutions")
public class ProductSubstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "group_id", nullable = false)
    private Long groupId = 0L;

    @Column(name = "group_name", length = 255, nullable = false)
    private String groupName = "";

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId = "";

    @Column(name = "variation_id", length = 64)
    private String variationId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;
}

