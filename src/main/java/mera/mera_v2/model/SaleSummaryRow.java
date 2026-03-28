package mera.mera_v2.model;

public class SaleSummaryRow {

  private String tableName;
  private String tableId;

  private long nhuCau;
  private long trung;
  private long rac;
  private long khongTuongTac;

  private long chotNong;
  private long chotCu;

  private long donHuy;

  private long tongMess;
  private long tongDon;

  private double donPerMessNhuCau;
  private double donPerMessTong;

  private double tiLeHuyPercent;

  public SaleSummaryRow() {}

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public String getTableId() {
    return tableId;
  }

  public void setTableId(String tableId) {
    this.tableId = tableId;
  }

  public long getNhuCau() {
    return nhuCau;
  }

  public void setNhuCau(long nhuCau) {
    this.nhuCau = nhuCau;
  }

  public long getTrung() {
    return trung;
  }

  public void setTrung(long trung) {
    this.trung = trung;
  }

  public long getRac() {
    return rac;
  }

  public void setRac(long rac) {
    this.rac = rac;
  }

  public long getKhongTuongTac() {
    return khongTuongTac;
  }

  public void setKhongTuongTac(long khongTuongTac) {
    this.khongTuongTac = khongTuongTac;
  }

  public long getChotNong() {
    return chotNong;
  }

  public void setChotNong(long chotNong) {
    this.chotNong = chotNong;
  }

  public long getChotCu() {
    return chotCu;
  }

  public void setChotCu(long chotCu) {
    this.chotCu = chotCu;
  }

  public long getDonHuy() {
    return donHuy;
  }

  public void setDonHuy(long donHuy) {
    this.donHuy = donHuy;
  }

  public long getTongMess() {
    return tongMess;
  }

  public void setTongMess(long tongMess) {
    this.tongMess = tongMess;
  }

  public long getTongDon() {
    return tongDon;
  }

  public void setTongDon(long tongDon) {
    this.tongDon = tongDon;
  }

  public double getDonPerMessNhuCau() {
    return donPerMessNhuCau;
  }

  public void setDonPerMessNhuCau(double donPerMessNhuCau) {
    this.donPerMessNhuCau = donPerMessNhuCau;
  }

  public double getDonPerMessTong() {
    return donPerMessTong;
  }

  public void setDonPerMessTong(double donPerMessTong) {
    this.donPerMessTong = donPerMessTong;
  }

  public double getTiLeHuyPercent() {
    return tiLeHuyPercent;
  }

  public void setTiLeHuyPercent(double tiLeHuyPercent) {
    this.tiLeHuyPercent = tiLeHuyPercent;
  }
}