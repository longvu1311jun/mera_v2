package mera.mera_v2.customer.DTO;

import java.util.List;

/** Kết quả xử lý file Excel "Từ chối chăm" trả về cho giao diện. */
public class RefusedCareUploadResult {

    private boolean success;
    private int totalPhones;       // số SĐT (9 số cuối, distinct) đọc được trong file
    private int matchedPhones;     // số SĐT khớp được khách trong DB
    private int savedCustomers;    // số khách trong file này được thêm/cập nhật vào TCC
    private int totalAfter;        // tổng số khách trong danh sách TCC sau khi gộp
    private int unmatchedPhones;   // số SĐT không tìm thấy khách
    private List<String> unmatchedSample; // vài SĐT không khớp (để đối chiếu)
    private List<UnmatchedDetail> unmatchedDetails; // toàn bộ SĐT không khớp + lý do (để tải xuống)
    private String errorMessage;

    /** Một SĐT không khớp kèm lý do cụ thể. */
    public static class UnmatchedDetail {
        private String phone;
        private String reason;

        public UnmatchedDetail() { }
        public UnmatchedDetail(String phone, String reason) {
            this.phone = phone;
            this.reason = reason;
        }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public int getTotalPhones() { return totalPhones; }
    public void setTotalPhones(int totalPhones) { this.totalPhones = totalPhones; }
    public int getMatchedPhones() { return matchedPhones; }
    public void setMatchedPhones(int matchedPhones) { this.matchedPhones = matchedPhones; }
    public int getSavedCustomers() { return savedCustomers; }
    public void setSavedCustomers(int savedCustomers) { this.savedCustomers = savedCustomers; }
    public int getTotalAfter() { return totalAfter; }
    public void setTotalAfter(int totalAfter) { this.totalAfter = totalAfter; }
    public int getUnmatchedPhones() { return unmatchedPhones; }
    public void setUnmatchedPhones(int unmatchedPhones) { this.unmatchedPhones = unmatchedPhones; }
    public List<String> getUnmatchedSample() { return unmatchedSample; }
    public void setUnmatchedSample(List<String> unmatchedSample) { this.unmatchedSample = unmatchedSample; }
    public List<UnmatchedDetail> getUnmatchedDetails() { return unmatchedDetails; }
    public void setUnmatchedDetails(List<UnmatchedDetail> unmatchedDetails) { this.unmatchedDetails = unmatchedDetails; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
