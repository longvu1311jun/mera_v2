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
@Table(name = "lark_sync_jobs")
public class LarkSyncJob {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "job_type", length = 50, nullable = false)
    private String jobType;

    @Column(name = "trigger_source", length = 20)
    private String triggerSource;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "total_employees")
    private Integer totalEmployees;

    @Column(name = "total_requests")
    private Integer totalRequests;

    @Column(name = "total_success_employees")
    private Integer totalSuccessEmployees;

    @Column(name = "total_invalid_employees")
    private Integer totalInvalidEmployees;

    @Column(name = "total_failed_requests")
    private Integer totalFailedRequests;

    @Column(name = "locked_key", length = 255)
    private String lockedKey;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "json")
    private String meta;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
