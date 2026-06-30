package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "lark_attendance_punches")
public class LarkAttendancePunch {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "employee_id", length = 64, nullable = false)
    private String employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "employee_name", length = 255)
    private String employeeName;

    @Column(name = "attendance_group_name", length = 255)
    private String attendanceGroupName;

    @Column(name = "weekday", length = 20)
    private String weekday;

    @Column(name = "punch_type")
    private Integer punchType;

    @Column(name = "punch_no")
    private Integer punchNo;

    @Column(name = "shift_time")
    private String shiftTime;

    @Column(name = "punch_time")
    private String punchTime;

    @Column(name = "punch_status", length = 50)
    private String punchStatus;

    @Column(name = "punch_sub_status", length = 50)
    private String punchSubStatus;

    @Column(name = "status_msg", length = 255)
    private String statusMsg;

    @Column(name = "location_name", length = 255)
    private String locationName;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "flow_id", length = 64)
    private String flowId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_features", columnDefinition = "json")
    private String rawFeatures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_item", columnDefinition = "json")
    private String rawItem;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
