package mera.mera_v2.customer.Dto;

import mera.mera_v2.entity.SearchConfig;

public class SearchConfigDto {
    private String baseId;
    private String larkName;
    private String posName;
    private String khachHangTableId;
    private String khachHangViewId;
    private String traoDoiTableId;
    private String traoDoiViewId;
    private String lichHenTableId;
    private String lichHenViewId;
    private Integer syncStatus;
    private boolean systemBase;

    public SearchConfigDto() {}

    public SearchConfigDto(SearchConfig config) {
        this.baseId = config.getLarkBaseId();
        this.larkName = config.getLarkBaseName();
        this.posName = config.getPosName();
        this.khachHangTableId = config.getKhachHangTableId();
        this.traoDoiTableId = config.getTraoDoiTableId();
        this.lichHenTableId = config.getLichHenTableId();
        this.khachHangViewId = config.getKhachHangViewId();
        this.traoDoiViewId = config.getTraoDoiViewId();
        this.lichHenViewId = config.getLichHenViewId();
        this.syncStatus = config.getSyncStatus();
        // System base = base "Hệ Thống 360", "Hủy", "Đơn Hoàn", "Từ chối chăm sóc"
        String larkName = config.getLarkBaseName() != null ? config.getLarkBaseName().toLowerCase() : "";
        boolean namedAsSystemBase = larkName.contains("hệ thống")
                || larkName.contains("hủy")
                || larkName.contains("đơn hoàn")
                || larkName.contains("từ chối");
        this.systemBase = namedAsSystemBase
                || (config.getPosName() == null && config.getPosUserId() == null && config.getPosPhone() == null);
    }

    public String getBaseId() { return baseId; }
    public String getLarkName() { return larkName; }
    public String getPosName() { return posName; }
    public String getKhachHangTableId() { return khachHangTableId; }
    public void setKhachHangTableId(String khachHangTableId) { this.khachHangTableId = khachHangTableId; }
    public String getTraoDoiTableId() { return traoDoiTableId; }
    public void setTraoDoiTableId(String traoDoiTableId) { this.traoDoiTableId = traoDoiTableId; }
    public String getLichHenTableId() { return lichHenTableId; }
    public void setLichHenTableId(String lichHenTableId) { this.lichHenTableId = lichHenTableId; }
    public String getKhachHangViewId() { return khachHangViewId; }
    public void setKhachHangViewId(String khachHangViewId) { this.khachHangViewId = khachHangViewId; }
    public String getTraoDoiViewId() { return traoDoiViewId; }
    public void setTraoDoiViewId(String traoDoiViewId) { this.traoDoiViewId = traoDoiViewId; }
    public String getLichHenViewId() { return lichHenViewId; }
    public void setLichHenViewId(String lichHenViewId) { this.lichHenViewId = lichHenViewId; }
    public Integer getSyncStatus() { return syncStatus; }
    public boolean isSystemBase() { return systemBase; }
}
