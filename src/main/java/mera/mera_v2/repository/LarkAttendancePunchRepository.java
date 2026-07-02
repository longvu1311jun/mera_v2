package mera.mera_v2.repository;

import mera.mera_v2.entity.LarkAttendancePunch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LarkAttendancePunchRepository extends JpaRepository<LarkAttendancePunch, String> {
    
    List<LarkAttendancePunch> findByEmployeeIdAndAttendanceDate(String employeeId, LocalDate attendanceDate);
    
    List<LarkAttendancePunch> findByAttendanceDateBetweenOrderByAttendanceDateDescEmployeeIdAscSyncedAtDesc(
            LocalDate startDate, LocalDate endDate);
    
    @Modifying
    @Query(value = """
        DELETE FROM lark_attendance_punches
        WHERE employee_id = :employeeId
        AND attendance_date = :attendanceDate
        AND code NOT IN (:currentCodes)
        """, nativeQuery = true)
    void deleteStalePunches(@Param("employeeId") String employeeId,
                            @Param("attendanceDate") LocalDate attendanceDate,
                            @Param("currentCodes") List<String> currentCodes);
    
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO lark_attendance_punches 
        (id, employee_id, employee_name, attendance_date, code, title, attendance_group_name, 
         weekday, punch_type, punch_no, shift_time, punch_time, punch_status, punch_sub_status,
         status_msg, location_name, task_id, flow_id, note, raw_features, raw_item, synced_at, created_at, updated_at)
        VALUES (:id, :employeeId, :employeeName, :attendanceDate, :code, :title, :attendanceGroupName,
                :weekday, :punchType, :punchNo, :shiftTime, :punchTime, :punchStatus, :punchSubStatus,
                :statusMsg, :locationName, :taskId, :flowId, :note, :rawFeatures, :rawItem, :syncedAt, :createdAt, :updatedAt)
        ON DUPLICATE KEY UPDATE
            employee_name = VALUES(employee_name),
            title = VALUES(title),
            attendance_group_name = VALUES(attendance_group_name),
            weekday = VALUES(weekday),
            punch_type = VALUES(punch_type),
            punch_no = VALUES(punch_no),
            shift_time = VALUES(shift_time),
            punch_time = VALUES(punch_time),
            punch_status = VALUES(punch_status),
            punch_sub_status = VALUES(punch_sub_status),
            status_msg = VALUES(status_msg),
            location_name = VALUES(location_name),
            task_id = VALUES(task_id),
            flow_id = VALUES(flow_id),
            note = VALUES(note),
            raw_features = VALUES(raw_features),
            raw_item = VALUES(raw_item),
            synced_at = VALUES(synced_at),
            updated_at = VALUES(updated_at)
        """, nativeQuery = true)
    void upsertPunch(@Param("id") String id,
                     @Param("employeeId") String employeeId,
                     @Param("employeeName") String employeeName,
                     @Param("attendanceDate") LocalDate attendanceDate,
                     @Param("code") String code,
                     @Param("title") String title,
                     @Param("attendanceGroupName") String attendanceGroupName,
                     @Param("weekday") String weekday,
                     @Param("punchType") Integer punchType,
                     @Param("punchNo") Integer punchNo,
                     @Param("shiftTime") String shiftTime,
                     @Param("punchTime") String punchTime,
                     @Param("punchStatus") String punchStatus,
                     @Param("punchSubStatus") String punchSubStatus,
                     @Param("statusMsg") String statusMsg,
                     @Param("locationName") String locationName,
                     @Param("taskId") String taskId,
                     @Param("flowId") String flowId,
                     @Param("note") String note,
                     @Param("rawFeatures") String rawFeatures,
                     @Param("rawItem") String rawItem,
                     @Param("syncedAt") LocalDateTime syncedAt,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    @Query("SELECT DISTINCT p.employeeId FROM LarkAttendancePunch p WHERE p.attendanceDate = :date AND p.punchType = 1")
    List<String> findDistinctEmployeeIdsCheckedIn(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT p.employeeId FROM LarkAttendancePunch p WHERE p.attendanceDate = :date")
    List<String> findDistinctEmployeeIdsWithAnyPunch(@Param("date") LocalDate date);

    @Query("""
        SELECT DISTINCT p.employeeId FROM LarkAttendancePunch p
        WHERE p.attendanceDate = :date
        AND p.punchType = 1
        AND p.punchTime IS NULL
        """)
    List<String> findEmployeeIdsMissingCheckin(@Param("date") LocalDate date);
}
