package mera.mera_v2.customer.DTO;

import java.util.List;

/**
 * Kết quả trang "Số thả nổi" — <b>phân trang server-side</b>: {@code rows} là 1 trang, cùng số
 * đếm nhóm A/B/C/D (cho stat card, tính trên toàn tập theo khoảng ngày) và thông tin phân trang
 * ({@code matched}/{@code page}/{@code pageSize}/{@code totalPages}). Đọc từ bảng precompute
 * {@code problem_customer_facts} (đã đánh index) nên nhẹ.
 */
public class ProblemCustomerResult {

    private List<ProblemCustomerRow> rows;
    private int total;            // tổng khách thuộc ≥1 nhóm (theo khoảng ngày) — cho ô "Tổng"
    private int groupACount;
    private int groupBCount;
    private int groupCCount;
    private int groupDCount;
    private boolean capped;

    // Phân trang server-side
    private int matched;         // số dòng khớp bộ lọc hiện tại (nhóm + tìm kiếm) — dùng phân trang
    private int page;
    private int pageSize;
    private int totalPages;

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
    public int getGroupDCount() { return groupDCount; }
    public void setGroupDCount(int groupDCount) { this.groupDCount = groupDCount; }
    public boolean isCapped() { return capped; }
    public void setCapped(boolean capped) { this.capped = capped; }
    public int getMatched() { return matched; }
    public void setMatched(int matched) { this.matched = matched; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
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
