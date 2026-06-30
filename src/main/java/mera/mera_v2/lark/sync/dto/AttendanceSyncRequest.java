package mera.mera_v2.lark.sync.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class AttendanceSyncRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> employeeIds;
    private List<Long> departmentIds;
    private boolean force = false;
}
