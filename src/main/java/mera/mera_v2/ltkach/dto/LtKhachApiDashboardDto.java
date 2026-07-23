package mera.mera_v2.ltkach.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LtKhachApiDashboardDto {
    private long totalOrders;
    private long newOrders;
    private long completedOrders;
    private long returnedOrders;
    private long cancelledOrders;
    private BigDecimal totalRevenue;
    private BigDecimal cod;
    private BigDecimal prepaid;
    private String source;
    private long fromTimestamp;
    private long toTimestamp;
    private String fromDate;
    private String toDate;
}
