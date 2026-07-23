package mera.mera_v2.ltkach.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class EmployeePerformanceDto {
    private int rank;
    private String userId;
    private String fullName;
    private String phone;
    private int totalDataReceived;
    private int totalOrdersReceived;
    private int completedOrders;
    private int cancelledOrders;
    private BigDecimal completionRate;
    private int lt;
    private BigDecimal revenue;
    private int lt1;
    private int lt2;
    private int lt3;
    private int lt4;
    // DOANH SỐ view
    private int ltLe;
    private BigDecimal dtLtLe;
    private BigDecimal dtLt1;
    private BigDecimal dtLt2;
    private BigDecimal dtLt3;
    private BigDecimal dtLt4;
}
