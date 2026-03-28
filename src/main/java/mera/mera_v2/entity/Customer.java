package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "gender", length = 255)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "fb_id", length = 255)
    private String fbId;

    @Column(name = "referral_code", length = 255)
    private String referralCode;

    @Column(name = "customer_referral_code", length = 255)
    private String customerReferralCode;

    @Column(name = "is_discount_by_level")
    private Integer isDiscountByLevel;

    @Column(name = "reward_point")
    private Integer rewardPoint = 0;

    @Column(name = "used_reward_point")
    private Integer usedRewardPoint = 0;

    @Column(name = "current_debts", precision = 18, scale = 4)
    private BigDecimal currentDebts = BigDecimal.ZERO;

    @Column(name = "level_id", length = 255)
    private String levelId;

    @Column(name = "is_block")
    private Integer isBlock;

    @Column(name = "order_count")
    private Integer orderCount = 0;

    @Column(name = "succeed_order_count")
    private Integer succeedOrderCount = 0;

    @Column(name = "returned_order_count")
    private Integer returnedOrderCount = 0;

    @Column(name = "purchased_amount", precision = 18, scale = 4)
    private BigDecimal purchasedAmount = BigDecimal.ZERO;

    @Column(name = "last_order_at")
    private LocalDateTime lastOrderAt;

    @Column(name = "assigned_user_id", length = 255)
    private String assignedUserId;

    @Column(name = "creator_id", length = 255)
    private String creatorId;

    @Column(name = "inserted_at", nullable = false)
    private LocalDateTime insertedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "lt_real", precision = 10, scale = 2)
    private BigDecimal ltReal;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<CustomerAddress> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<CustomerPhoneNumber> phoneNumbers = new ArrayList<>();

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<CustomerNote> notes = new ArrayList<>();
}