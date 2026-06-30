package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "employee_mappings", indexes = {
    @Index(name = "idx_lark_employee_id", columnList = "lark_employee_id"),
    @Index(name = "idx_pos_user_id", columnList = "pos_user_id")
})
public class EmployeeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lark_employee_id", length = 64)
    private String larkEmployeeId;

    @Column(name = "lark_employee_name", length = 255)
    private String larkEmployeeName;

    @Column(name = "lark_employee_no", length = 64)
    private String larkEmployeeNo;

    @Column(name = "lark_department", length = 255)
    private String larkDepartment;

    @Column(name = "lark_hire_date")
    private LocalDate hireDate;

    @Column(name = "pos_user_id", length = 64)
    private String posUserId;

    @Column(name = "pos_user_name", length = 255)
    private String posUserName;

    @Column(name = "pos_user_email", length = 255)
    private String posUserEmail;

    @Column(name = "is_mapped", nullable = false)
    private Boolean isMapped = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    private int assignmentCountCache;

    public int getAssignmentCountCache() {
        return assignmentCountCache;
    }

    public void setAssignmentCountCache(int assignmentCountCache) {
        this.assignmentCountCache = assignmentCountCache;
    }
}
