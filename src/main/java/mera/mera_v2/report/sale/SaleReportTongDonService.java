package mera.mera_v2.report.sale;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.model.SaleSummaryRow;
import mera.mera_v2.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleReportTongDonService {

  private static final int ORDER_STATUS_COMPLETED = 3;
  private static final int ORDER_STATUS_CANCELLED = 6;
  /** order_status_histories.new_status khi đơn chuyển sang trạng thái mới (theo báo cáo hủy) */
  private static final int ORDER_HISTORY_NEW_STATUS = 1;

  private final OrderRepository orderRepository;

  public void enrichTongDonFromDb(List<SaleSummaryRow> rows, String range) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    SaleReportMonthRange monthRange = SaleReportMonthRange.fromRangeKey(range);
    for (SaleSummaryRow row : rows) {
      String baseLark = row.getTableName();
      if (baseLark == null || baseLark.isBlank()) {
        applyOrderMetricsAndDerivedRates(row, 0, 0);
        continue;
      }
      String baseLarkKey = baseLark.trim();
      long tongDon = orderRepository.countByBaseLarkAndInsertedAtBetween(
          baseLarkKey,
          monthRange.getStartInclusive(),
          monthRange.getEndInclusive(),
          ORDER_STATUS_COMPLETED
      );
      long donHuyDb = orderRepository.countCancelledOrdersByBaseLarkWithHistory(
          baseLarkKey,
          monthRange.getStartInclusive(),
          monthRange.getEndInclusive(),
          ORDER_STATUS_CANCELLED,
          ORDER_HISTORY_NEW_STATUS
      );
      applyOrderMetricsAndDerivedRates(row, tongDon, donHuyDb);
    }
    log.debug("Enriched tongDon/donHuyDb from DB for {} rows, range={}", rows.size(), range);
  }

  static void applyOrderMetricsAndDerivedRates(SaleSummaryRow row, long tongDon, long donHuyDb) {
    row.setTongDon(tongDon);
    row.setDonHuyDb(donHuyDb);

    long nhuCau = row.getNhuCau();
    long chotNong = row.getChotNong();
    long chotCu = row.getChotCu();
    long donHuyMess = row.getDonHuy();
    long tongMess = row.getTongMess();

    long nhuCauDenominator = nhuCau + chotNong + chotCu + donHuyMess;
    row.setDonPerMessNhuCau(percentRatio(tongDon, nhuCauDenominator));
    row.setDonPerMessTong(percentRatio(tongDon, tongMess));
    row.setTiLeHuyPercent(percentRatio(donHuyDb, tongDon));
  }

  private static double percentRatio(long numerator, long denominator) {
    if (denominator == 0) {
      return 0.0;
    }
    return Math.round(((double) numerator / (double) denominator) * 1000.0) / 10.0;
  }
}
