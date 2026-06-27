package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "pos_profiles")
public class PosProfile {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
