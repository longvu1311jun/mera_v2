package mera.mera_v2.customer.DTO;

import java.util.List;

/**
 * Kết quả đầy đủ (không phân trang) của trang "Khách đã tối ưu". Client tự phân trang / tìm kiếm.
 */
public class OptimizedCustomerResult {

    private List<OptimizedCustomerRow> rows;
    private int total;
    private int optimizedToday;
    private String generatedAt;
    private String errorMessage;

    public List<OptimizedCustomerRow> getRows() { return rows; }
    public void setRows(List<OptimizedCustomerRow> rows) { this.rows = rows; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getOptimizedToday() { return optimizedToday; }
    public void setOptimizedToday(int optimizedToday) { this.optimizedToday = optimizedToday; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
