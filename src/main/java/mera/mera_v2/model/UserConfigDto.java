package mera.mera_v2.model;

public class UserConfigDto {
  private PosUser posUser;
  private LarkNode larkNode;

  // Override posName cho các base đặc biệt (Từ chối chăm, Hoàn, Hủy)
  private String specialPosName;
  
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
  
  public String getLarkName() {
    return larkNode != null ? larkNode.getTitle() : "";
  }
  
  public String getBaseId() {
    return larkNode != null ? larkNode.getObjToken() : "";
  }

  private String khachHangTableId;
  private String lichHenTableId;
  private String traoDoiTableId;

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

