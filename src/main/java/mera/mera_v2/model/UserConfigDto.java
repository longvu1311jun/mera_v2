package mera.mera_v2.model;

public class UserConfigDto {
  private PosUser posUser;
  private LarkNode larkNode;

  // Override posName cho các base đặc biệt (Từ chối chăm, Hoàn, Hủy)
  private String specialPosName;

  // Base ID loaded directly from DB (không phụ thuộc larkNode)
  private String baseId;
  private String larkName;

  private String khachHangViewId;
  private String lichHenViewId;
  private String traoDoiViewId;

  private String khachHangTableId;
  private String lichHenTableId;
  private String traoDoiTableId;

  public UserConfigDto(PosUser posUser, LarkNode larkNode) {
    this.posUser = posUser;
    this.larkNode = larkNode;
  }

  public PosUser getPosUser() {
    return posUser;
  }

  public void setPosUser(PosUser posUser) {
    this.posUser = posUser;
  }

  public LarkNode getLarkNode() {
    return larkNode;
  }

  public void setLarkNode(LarkNode larkNode) {
    this.larkNode = larkNode;
  }

  public String getPosName() {
    if (specialPosName != null) return specialPosName;
    return posUser != null ? posUser.getName() : "";
  }

  public void setPosName(String name) {
    this.specialPosName = name;
  }

  public String getBaseId() {
    return baseId != null ? baseId : (larkNode != null ? larkNode.getObjToken() : "");
  }

  public void setBaseId(String baseId) {
    this.baseId = baseId;
  }

  public String getLarkName() {
    return larkName != null ? larkName : (larkNode != null ? larkNode.getTitle() : "");
  }

  public void setLarkName(String larkName) {
    this.larkName = larkName;
  }

  public String getKhachHangViewId() {
    return khachHangViewId != null ? khachHangViewId : "";
  }

  public void setKhachHangViewId(String khachHangViewId) {
    this.khachHangViewId = khachHangViewId;
  }

  public String getLichHenViewId() {
    return lichHenViewId != null ? lichHenViewId : "";
  }

  public void setLichHenViewId(String lichHenViewId) {
    this.lichHenViewId = lichHenViewId;
  }

  public String getTraoDoiViewId() {
    return traoDoiViewId != null ? traoDoiViewId : "";
  }

  public void setTraoDoiViewId(String traoDoiViewId) {
    this.traoDoiViewId = traoDoiViewId;
  }

  public String getKhachHangTableId() {
    return khachHangTableId != null ? khachHangTableId : "";
  }

  public void setKhachHangTableId(String khachHangTableId) {
    this.khachHangTableId = khachHangTableId;
  }

  public String getLichHenTableId() {
    return lichHenTableId != null ? lichHenTableId : "";
  }

  public void setLichHenTableId(String lichHenTableId) {
    this.lichHenTableId = lichHenTableId;
  }

  public String getTraoDoiTableId() {
    return traoDoiTableId != null ? traoDoiTableId : "";
  }

  public void setTraoDoiTableId(String traoDoiTableId) {
    this.traoDoiTableId = traoDoiTableId;
  }
}
