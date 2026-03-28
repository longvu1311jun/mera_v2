package mera.mera_v2.model;

public class EmployeeStatsDto {
  private String employeeName;
  private long tongKhach; // æ€»å®¢æˆ·æ•°
  private long tongLich; // æ€»é¢„çº¦æ•°
  private long hoanThanhMuon; // å»¶è¿Ÿå®Œæˆ
  private long hoanThanh; // å·²å®Œæˆ
  private long quaHan; // è¿‡æœŸ

  public EmployeeStatsDto() {
  }

  public EmployeeStatsDto(String employeeName) {
    this.employeeName = employeeName;
    this.tongKhach = 0;
    this.tongLich = 0;
    this.hoanThanhMuon = 0;
    this.hoanThanh = 0;
    this.quaHan = 0;
  }

  public String getEmployeeName() {
    return employeeName;
  }

  public void setEmployeeName(String employeeName) {
    this.employeeName = employeeName;
  }

  public long getTongKhach() {
    return tongKhach;
  }

  public void setTongKhach(long tongKhach) {
    this.tongKhach = tongKhach;
  }

  public long getTongLich() {
    return tongLich;
  }

  public void setTongLich(long tongLich) {
    this.tongLich = tongLich;
  }

  public long getHoanThanhMuon() {
    return hoanThanhMuon;
  }

  public void setHoanThanhMuon(long hoanThanhMuon) {
    this.hoanThanhMuon = hoanThanhMuon;
  }

  public long getHoanThanh() {
    return hoanThanh;
  }

  public void setHoanThanh(long hoanThanh) {
    this.hoanThanh = hoanThanh;
  }

  public long getQuaHan() {
    return quaHan;
  }

  public void setQuaHan(long quaHan) {
    this.quaHan = quaHan;
  }
}


