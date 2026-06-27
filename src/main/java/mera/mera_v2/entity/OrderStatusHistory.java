package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "order_status_histories",
    indexes = {
        @Index(name = "idx_status_history_order_id", columnList = "order_id"),
        @Index(name = "idx_status_history_updated_at", columnList = "updated_at")
    }
)
public class OrderStatusHistory {

  @Id
  @SequenceGenerator(name = "order_status_history_seq", sequenceName = "order_status_history_seq", allocationSize = 50)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_status_history_seq")
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "order_id", insertable = false, updatable = false)
  private Long orderId;

  @Column(name = "old_status")
  private Integer oldStatus;

  @Column(name = "new_status", nullable = false)
  private Integer newStatus;

  @Column(name = "editor_id", length = 64)
  private String editorId;

  @Column(name = "editor_name", length = 255)
  private String editorName;

  @Column(name = "editor_fb", length = 64)
  private String editorFb;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}