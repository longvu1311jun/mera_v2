package mera.mera_v2.report.sale;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Khoảng inserted_at cho sale report — khớp logic UTC+7 lưu trong DB
 * (00:00 VN = 17:00 ngày hôm trước, 23:59:59 VN = 16:59:59 cùng ngày).
 */
public final class SaleReportMonthRange {

  private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

  private final LocalDateTime startInclusive;
  private final LocalDateTime endInclusive;

  private SaleReportMonthRange(LocalDateTime startInclusive, LocalDateTime endInclusive) {
    this.startInclusive = startInclusive;
    this.endInclusive = endInclusive;
  }

  public static SaleReportMonthRange fromRangeKey(String range) {
    LocalDate today = LocalDate.now(VN);
    YearMonth ym = "LastMonth".equals(range)
        ? YearMonth.from(today.minusMonths(1))
        : YearMonth.from(today);

    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();

    LocalDateTime startAt = LocalDateTime.of(start, LocalTime.MIN).minusHours(7);
    LocalDateTime endAt = LocalDateTime.of(end, LocalTime.of(23, 59, 59)).minusHours(7);
    return new SaleReportMonthRange(startAt, endAt);
  }

  public LocalDateTime getStartInclusive() {
    return startInclusive;
  }

  public LocalDateTime getEndInclusive() {
    return endInclusive;
  }
}
