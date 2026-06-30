package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "lark_employees")
public class LarkEmployee {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "open_id", length = 128)
    private String openId;

    @Column(name = "union_id", length = 128)
    private String unionId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "employee_no", length = 64)
    private String employeeNo;

    @Column(name = "department_id", length = 64)
    private String departmentId;

    @Column(name = "job_title", length = 255)
    private String jobTitle;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "pos_user_id", length = 64)
    private String posUserId;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "fb_id", length = 64)
    private String fbId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", referencedColumnName = "id", insertable = false, updatable = false)
    private LarkDepartment department;
}