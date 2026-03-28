package mera.mera_v2.lark.webhook.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.config.SalesTablesConfig;
import mera.mera_v2.lark.webhook.lark.LarkBaseClient;
import mera.mera_v2.lark.webhook.model.CustomerRecord;
import mera.mera_v2.lark.webhook.model.SalesSummary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final LarkBaseClient larkBaseClient;
    private final SalesTablesConfig salesTablesConfig;

    @Getter
    private volatile List<SalesSummary> cachedReport = new ArrayList<>();

    @PostConstruct
    public void initOnStartup() {
        // log.info("Init report on startup...");
        // refreshReport();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void refreshReport() {
        // Comment để test webhook trước
        return;
        /*
        try {
            log.info("Refreshing sales report from Lark Base...");

            // Tự động refresh tables từ API trước khi lấy data
            salesTablesConfig.refreshTables();

            List<SalesSummary> reportList = new ArrayList<>();
            List<SalesTablesConfig.SalesTable> tables = salesTablesConfig.getTables();
            
            log.info("Total tables to process: {}", tables.size());
            
            if (tables.isEmpty()) {
                log.warn("No tables found! Make sure tokens are set and tables are loaded from API.");
                cachedReport = reportList;
                return;
            }

          int successCount = 0;
          int errorCount = 0;
          int emptyCount = 0;
          
          for (int i = 0; i < tables.size(); i++) {
            SalesTablesConfig.SalesTable table = tables.get(i);
            try {
                log.info("Processing table {}/{}: {} (ID: {})", 
                    i + 1, tables.size(), table.getDisplayName(), table.getTableId());
                
                // Thêm delay giữa các request để tránh rate limiting (trừ request đầu tiên)
                if (i > 0) {
                    try {
                        Thread.sleep(200); // 200ms delay giữa các request
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                List<CustomerRecord> records = larkBaseClient.fetchRecords(table);
                SalesSummary summary = buildSummary(table.getDisplayName(), records);
                
                // Luôn add summary vào list, kể cả khi không có data (để hiển thị 0)
                reportList.add(summary);
                successCount++;
                
                if (summary.getTongMes() == 0) {
                    emptyCount++;
                    log.info("Table {} has no data for current month", table.getDisplayName());
                } else {
                    log.info("Fetched {} records for {} (Total mess: {})", 
                        records.size(), table.getDisplayName(), summary.getTongMes());
                }
            } catch (Exception e) {
                errorCount++;
                // Log ngắn gọn, chỉ thông tin quan trọng
                String errorMsg = e.getMessage();
                Throwable cause = e.getCause();
                String causeMsg = cause != null ? cause.getMessage() : null;
                
                // Chỉ log message chính, không log full stack trace trừ khi cần debug
                if (causeMsg != null && !causeMsg.equals(errorMsg)) {
                    log.error("ERROR [{}] {} ({}): {} - {}", 
                        table.getTableId(), table.getDisplayName(), 
                        errorMsg, causeMsg);
                } else {
                    log.error("ERROR [{}] {}: {}", 
                        table.getTableId(), table.getDisplayName(), errorMsg);
                }
                
                // Thử retry một lần nếu lỗi có thể do rate limiting hoặc timeout
                if (errorMsg != null && (errorMsg.contains("code") || errorMsg.contains("timeout") || 
                    errorMsg.contains("rate") || errorMsg.contains("429"))) {
                    log.info("Retrying table {} after delay...", table.getDisplayName());
                    try {
                        Thread.sleep(1000); // Đợi 1 giây trước khi retry
                        List<CustomerRecord> records = larkBaseClient.fetchRecords(table);
                        SalesSummary summary = buildSummary(table.getDisplayName(), records);
                        reportList.add(summary);
                        successCount++;
                        errorCount--; // Giảm error count vì đã retry thành công
                        log.info("Retry successful for table {}", table.getDisplayName());
                        continue;
                    } catch (Exception retryException) {
                        log.error("Retry also failed for table {}: {}", 
                            table.getDisplayName(), retryException.getMessage());
                    }
                }
                
                // Vẫn tạo summary với giá trị 0 để hiển thị trong báo cáo
                SalesSummary errorSummary = new SalesSummary();
                errorSummary.setStaffName(table.getDisplayName() + " (Lỗi)");
                reportList.add(errorSummary);
            }
          }
          
          log.info("Report processing complete: {} success, {} empty, {} errors, total in report: {}", 
              successCount, emptyCount, errorCount, reportList.size());

            cachedReport = reportList;
            log.info("Report refreshed. Total staff = {}", reportList.size());

        } catch (Exception e) {
            log.error("Error refreshing report", e);
        }
        */
    }

    /**
     * Build SalesSummary theo bảng KPI mới bro yêu cầu
     */
    private SalesSummary buildSummary(String staff, List<CustomerRecord> records) {

        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int year  = now.getYear();

        SalesSummary s = new SalesSummary();
        s.setStaffName(staff);

        long nhuCau = 0;
        long trung = 0;
        long rac = 0;
        long ktt = 0;
        long chotNong = 0;
        long chotCu = 0;
        long donHuy = 0;

        for (CustomerRecord r : records) {

            // lọc theo tháng hiện tại
            if (r.getCreatedDate().getYear() != year ||
                    r.getCreatedDate().getMonthValue() != month) {
                continue;
            }

            String st = r.getStatus();
            if (st == null) continue;

            String lower = st.toLowerCase();

            if (lower.contains("nhu cầu")) nhuCau++;
            else if (lower.contains("trùng")) trung++;
            else if (lower.contains("rác")) rac++;
            else if (lower.contains("không tương tác")) ktt++;
            else if (lower.contains("chốt nóng")) chotNong++;
            else if (lower.contains("chốt cũ")) chotCu++;
            else if (lower.contains("đơn huỷ") || lower.contains("đơn hủy")) donHuy++;
        }

        // set vào summary
        s.setNhuCau(nhuCau);
        s.setTrung(trung);
        s.setRac(rac);
        s.setKhongTuongTac(ktt);
        s.setChotNong(chotNong);
        s.setChotCu(chotCu);
        s.setDonHuy(donHuy);

        long tongMes = nhuCau + trung + rac + ktt + chotNong + chotCu + donHuy;
        long tongDon = chotNong + chotCu + donHuy   ;

        s.setTongMes(tongMes);
        s.setTongDon(tongDon);

        // công thức t tính cho bro
        double donMesNhuCau = safeDiv(tongDon, (nhuCau + chotNong + chotCu + donHuy))*100;
        double donMesTong   = safeDiv(tongDon, tongMes)*100;
        double tiLeHuy      = safeDiv(donHuy,  tongDon)*100;

        s.setDonMesNhuCau(round2(donMesNhuCau));
        s.setDonMesTong(round2(donMesTong));
        s.setTiLeHuy(round2(tiLeHuy));

        return s;
    }

    private double safeDiv(long a, long b) {
        return b == 0 ? 0 : (double) a / b;
    }

    private double round2(double v) {
        return Math.round(v * 100.0)/100.0;
    }
}
