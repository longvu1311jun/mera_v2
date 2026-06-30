package mera.mera_v2.pos.assignment;

import lombok.Builder;
import lombok.Getter;
import mera.mera_v2.entity.EmployeeMapping;

@Getter
@Builder
public class AssignmentResult {
    private final EmployeeMapping employee;
    private final String cskhOpenId;
    private final String customerName;
    private final String customerPhone;

    public String getEmployeeName() {
        return employee != null ? employee.getLarkEmployeeName() : null;
    }

    public String getPosUserId() {
        return employee != null ? employee.getPosUserId() : null;
    }

    public String getLarkEmployeeId() {
        return employee != null ? employee.getLarkEmployeeId() : null;
    }
}
