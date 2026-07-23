package mera.mera_v2.ltkach.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DashboardDto {
    private long totalOrders;
    private long completedOrders;
    private BigDecimal totalRevenue;
    private BigDecimal conversionRate;
    private Long previousTotalOrders;
    private Long previousCompletedOrders;
    private BigDecimal previousRevenue;
}
