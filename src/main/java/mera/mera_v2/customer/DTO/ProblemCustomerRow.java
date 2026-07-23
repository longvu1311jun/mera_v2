package mera.mera_v2.customer.DTO;

/**
 * Dòng hiển thị trên bảng "Số thả nổi", đã format sẵn cho JSON (client render).
 * Tách từ inner class của ProblemCustomerController để dùng chung.
 */
public class ProblemCustomerRow {
    private String customerId;
    private String name;
    private String phone;
    private int noteCount;
    private int orderCount;
    private int succeedOrderCount;
    private String insertedAt;
    private String lastNoteAt;
    private String lastReceivedAt;
    private boolean groupA;
    private boolean groupB;
    private boolean groupC;
    private boolean groupD;   // Nhóm D: Từ Chối Chăm (từ file upload)
    private String reason;

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public int getNoteCount() { return noteCount; }
    public void setNoteCount(int noteCount) { this.noteCount = noteCount; }
    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
    public int getSucceedOrderCount() { return succeedOrderCount; }
    public void setSucceedOrderCount(int succeedOrderCount) { this.succeedOrderCount = succeedOrderCount; }
    public String getInsertedAt() { return insertedAt; }
    public void setInsertedAt(String insertedAt) { this.insertedAt = insertedAt; }
    public String getLastNoteAt() { return lastNoteAt; }
    public void setLastNoteAt(String lastNoteAt) { this.lastNoteAt = lastNoteAt; }
    public String getLastReceivedAt() { return lastReceivedAt; }
    public void setLastReceivedAt(String lastReceivedAt) { this.lastReceivedAt = lastReceivedAt; }
    public boolean isGroupA() { return groupA; }
    public void setGroupA(boolean groupA) { this.groupA = groupA; }
    public boolean isGroupB() { return groupB; }
    public void setGroupB(boolean groupB) { this.groupB = groupB; }
    public boolean isGroupC() { return groupC; }
    public void setGroupC(boolean groupC) { this.groupC = groupC; }
    public boolean isGroupD() { return groupD; }
    public void setGroupD(boolean groupD) { this.groupD = groupD; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
