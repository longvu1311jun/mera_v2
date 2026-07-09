package mera.mera_v2.customer.DTO;

/**
 * Dòng hiển thị trên bảng "Khách đã tối ưu", đã format sẵn cho JSON (client render).
 */
public class OptimizedCustomerRow {
    private String customerId;
    private String name;
    private String phone;
    private int noteCount;
    private String reason;
    private String customerCreatedText;
    private int orderCountBefore;
    private int succeedBefore;
    private int orderCountAfter;
    private int succeedAfter;
    private String firstSeenAt;
    private String optimizedAt;

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public int getNoteCount() { return noteCount; }
    public void setNoteCount(int noteCount) { this.noteCount = noteCount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCustomerCreatedText() { return customerCreatedText; }
    public void setCustomerCreatedText(String customerCreatedText) { this.customerCreatedText = customerCreatedText; }
    public int getOrderCountBefore() { return orderCountBefore; }
    public void setOrderCountBefore(int orderCountBefore) { this.orderCountBefore = orderCountBefore; }
    public int getSucceedBefore() { return succeedBefore; }
    public void setSucceedBefore(int succeedBefore) { this.succeedBefore = succeedBefore; }
    public int getOrderCountAfter() { return orderCountAfter; }
    public void setOrderCountAfter(int orderCountAfter) { this.orderCountAfter = orderCountAfter; }
    public int getSucceedAfter() { return succeedAfter; }
    public void setSucceedAfter(int succeedAfter) { this.succeedAfter = succeedAfter; }
    public String getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(String firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public String getOptimizedAt() { return optimizedAt; }
    public void setOptimizedAt(String optimizedAt) { this.optimizedAt = optimizedAt; }
}
