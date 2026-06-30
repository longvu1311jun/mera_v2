package mera.mera_v2.repository;

import mera.mera_v2.entity.LarkAttendanceDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface LarkAttendanceDayRepository extends JpaRepository<LarkAttendanceDay, String> {
    
    Optional<LarkAttendanceDay> findByEmployeeIdAndAttendanceDate(String employeeId, LocalDate attendanceDate);
    
    @Modifying
    @Query(value = """
        UPDATE lark_attendance_days
        SET gio_vao = (
            SELECT MIN(punch_time)
            FROM lark_attendance_punches
            WHERE employee_id = :employeeId
            AND attendance_date = :attendanceDate
            AND punch_time IS NOT NULL
        ),
        gio_ra = (
            SELECT MAX(punch_time)
            FROM lark_attendance_punches
            WHERE employee_id = :employeeId
            AND attendance_date = :attendanceDate
            AND punch_time IS NOT NULL
        ),
        updated_at = NOW()
        WHERE employee_id = :employeeId
        AND attendance_date = :attendanceDate
        """, nativeQuery = true)
    void computeWorkHours(@Param("employeeId") String employeeId,
                         @Param("attendanceDate") LocalDate attendanceDate);
}
