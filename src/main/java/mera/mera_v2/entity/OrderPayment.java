package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "order_payments")
public class OrderPayment {

    @Id
    @SequenceGenerator(name = "order_payment_seq", sequenceName = "order_payment_seq", allocationSize = 50)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_payment_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "method", length = 255)
    private String method;

    @Column(name = "bank_name", length = 255)
    private String bankName;

    @Column(name = "account_number", length = 255)
    private String accountNumber;

    @Column(name = "account_name", length = 255)
    private String accountName;

    @Column(name = "amount", precision = 18, scale = 4, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Order order;
}