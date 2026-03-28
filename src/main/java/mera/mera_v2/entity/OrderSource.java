package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "order_sources")
public class OrderSource {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "shop_id")
    private Long shopId = 1546758L;

    @Column(name = "custom_id", length = 50)
    private String customId;

    @Column(name = "name", length = 500)
    private String name;

    @Column(name = "image", length = 1000)
    private String image;

    @Column(name = "parent_id")
    private Long parentId = 0L;

    @Column(name = "project_id", length = 100)
    private String projectId;

    @Column(name = "link_source_id", length = 100)
    private String linkSourceId;

    @Column(name = "is_removed")
    private Integer isRemoved = 0;

    @Column(name = "inserted_at")
    private LocalDateTime insertedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}