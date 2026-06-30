package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "lark_attendance_days")
public class LarkAttendanceDay {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "employee_id", length = 64, nullable = false)
    private String employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "employee_name", length = 255)
    private String employeeName;

    @Column(name = "employee_no", length = 64)
    private String employeeNo;

    @Column(name = "department_name", length = 255)
    private String departmentName;

    @Column(name = "attendance_group_name", length = 255)
    private String attendanceGroupName;

    @Column(name = "shift_name", length = 255)
    private String shiftName;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "weekday", length = 20)
    private String weekday;

    @Column(name = "required_hours", precision = 10, scale = 2)
    private BigDecimal requiredHours;

    @Column(name = "actual_hours", precision = 10, scale = 2)
    private BigDecimal actualHours;

    @Column(name = "leave_hours", precision = 10, scale = 2)
    private BigDecimal leaveHours;

    @Column(name = "overtime_hours", precision = 10, scale = 2)
    private BigDecimal overtimeHours;

    @Column(name = "resigned_date")
    private LocalDate resignedDate;

    @Column(name = "gio_vao")
    private String gioVao;

    @Column(name = "gio_ra")
    private String gioRa;

    @Column(name = "gio_thuc_te", precision = 10, scale = 2)
    private BigDecimal gioThucTe;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "json")
    private String rawData;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
