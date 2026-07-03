package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "order_assignments", indexes = {
    @Index(name = "idx_order_assign_order_id", columnList = "order_id"),
    @Index(name = "idx_order_assign_lark_employee_id", columnList = "lark_employee_id"),
    @Index(name = "idx_order_assign_assigned_at", columnList = "assigned_at")
})
public class OrderAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_code", length = 64)
    private String orderCode;

    @Column(name = "employee_mapping_id", nullable = false)
    private Long employeeMappingId;

    @Column(name = "lark_employee_id", length = 64, nullable = false)
    private String larkEmployeeId;

    @Column(name = "lark_employee_name", length = 255)
    private String larkEmployeeName;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "customer_phone", length = 32)
    private String customerPhone;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }
}
