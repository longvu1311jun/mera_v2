package mera.mera_v2.customer.DTO;

import java.util.List;

/**
 * Kết quả đầy đủ (không phân trang) của trang "Số thả nổi". Toàn bộ tập được trả về;
 * việc phân trang, lọc theo nhóm và tìm kiếm theo SĐT/tên được xử lý phía client để
 * thao tác tức thời, tránh chạy lại query nặng cho mỗi lần đổi trang.
 */
public class ProblemCustomerResult {

    private List<ProblemCustomerRow> rows;
    private int total;
    private int groupACount;
    private int groupBCount;
    private int groupCCount;
    private boolean capped;

    // Filter đã clamp — echo lại cho form/meta
    private int minNotes;
    private int hours;
    private int maxDays;
    private int months;

    // Khoảng lọc Ngày tạo KH đang áp dụng (yyyy-MM-dd, rỗng nếu không lọc) — echo lại cho form
    private String fromDate;
    private String toDate;

    private String generatedAt;
    private String errorMessage;

    public List<ProblemCustomerRow> getRows() { return rows; }
    public void setRows(List<ProblemCustomerRow> rows) { this.rows = rows; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getGroupACount() { return groupACount; }
    public void setGroupACount(int groupACount) { this.groupACount = groupACount; }
    public int getGroupBCount() { return groupBCount; }
    public void setGroupBCount(int groupBCount) { this.groupBCount = groupBCount; }
    public int getGroupCCount() { return groupCCount; }
    public void setGroupCCount(int groupCCount) { this.groupCCount = groupCCount; }
    public boolean isCapped() { return capped; }
    public void setCapped(boolean capped) { this.capped = capped; }
    public int getMinNotes() { return minNotes; }
    public void setMinNotes(int minNotes) { this.minNotes = minNotes; }
    public int getHours() { return hours; }
    public void setHours(int hours) { this.hours = hours; }
    public int getMaxDays() { return maxDays; }
    public void setMaxDays(int maxDays) { this.maxDays = maxDays; }
    public int getMonths() { return months; }
    public void setMonths(int months) { this.months = months; }
    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }
    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
