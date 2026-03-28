package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "pos_departments")
public class PosDepartment {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

