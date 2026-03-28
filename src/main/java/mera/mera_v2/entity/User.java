package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "profile_id", length = 100)
    private String profileId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "name", length = 500)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "is_assigned")
    private Integer isAssigned = 0;

    @Column(name = "is_assigned_break_time")
    private Integer isAssignedBreakTime = 0;

    @Column(name = "enable_api")
    private Integer enableApi = 0;

    @Column(name = "inserted_at")
    private LocalDateTime insertedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

