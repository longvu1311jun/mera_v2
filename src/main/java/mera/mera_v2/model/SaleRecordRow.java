package mera.mera_v2.model;

public class SaleRecordRow {

  private String tableName;
  private String tableId;

  private String ngayTao;
  private String dienThoai;
  private String trangThaiMess;

  public SaleRecordRow() {}

  public SaleRecordRow(String tableName, String tableId, String ngayTao, String dienThoai, String trangThaiMess) {
    this.tableName = tableName;
    this.tableId = tableId;
    this.ngayTao = ngayTao;
    this.dienThoai = dienThoai;
    this.trangThaiMess = trangThaiMess;
  }

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

  public String getNgayTao() {
    return ngayTao;
  }

  public void setNgayTao(String ngayTao) {
    this.ngayTao = ngayTao;
  }

  public String getDienThoai() {
    return dienThoai;
  }

  public void setDienThoai(String dienThoai) {
    this.dienThoai = dienThoai;
  }

  public String getTrangThaiMess() {
    return trangThaiMess;
  }

  public void setTrangThaiMess(String trangThaiMess) {
    this.trangThaiMess = trangThaiMess;
  }
}


