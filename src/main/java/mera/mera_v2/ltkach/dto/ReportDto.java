package mera.mera_v2.ltkach.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ReportDto {
    private String fromDate;
    private String toDate;
    private List<EmployeePerformanceDto> data;
    private int totalEmployees;
    private long totalDataReceived;
    private BigDecimal totalRevenue;
}
