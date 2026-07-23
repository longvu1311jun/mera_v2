package mera.mera_v2.ltkach.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RevenueGrowthDto {
    private String source;
    private List<MonthlyRevenue> months;

    @Data
    public static class MonthlyRevenue {
        private String month;        // "2026-06"
        private String monthName;    // "Tháng 6/2026"
        private long orderCount;
        private BigDecimal revenue;
        private BigDecimal cod;
        private BigDecimal prepaid;
    }
}
