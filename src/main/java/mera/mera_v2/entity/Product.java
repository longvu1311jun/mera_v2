package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "display_id", length = 255)
    private String displayId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "brand_id", length = 255)
    private String brandId;

    @Column(name = "is_composite")
    private Integer isComposite;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "custom_id", length = 255)
    private String customId;

    @Column(name = "image", columnDefinition = "TEXT")
    private String image;

    @Column(name = "is_published")
    private Boolean isPublished;

    @Column(name = "measure_group_id", length = 255)
    private String measureGroupId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "note_product", columnDefinition = "TEXT")
    private String noteProduct;

    @Column(name = "type", length = 255)
    private String type;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<ProductVariation> variations = new ArrayList<>();
}

