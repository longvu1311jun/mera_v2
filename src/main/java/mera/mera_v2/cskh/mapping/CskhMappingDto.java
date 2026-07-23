package mera.mera_v2.cskh.mapping;

public class CskhMappingDto {

    private String id;
    private String posName;
    private String posPhone;
    private String larkBaseName;
    private String larkBaseId;
    private String khachHangTableId;
    private String traoDoiTableId;
    private String lichHenTableId;
    private String viewId;
    private Boolean isActive;
    private String departmentName;

    public CskhMappingDto() {}

    public CskhMappingDto(String id, String posName, String posPhone, String larkBaseName, String larkBaseId,
            String khachHangTableId, String traoDoiTableId, String lichHenTableId, String viewId,
            Boolean isActive, String departmentName) {
        this.id = id;
        this.posName = posName;
        this.posPhone = posPhone;
        this.larkBaseName = larkBaseName;
        this.larkBaseId = larkBaseId;
        this.khachHangTableId = khachHangTableId;
        this.traoDoiTableId = traoDoiTableId;
        this.lichHenTableId = lichHenTableId;
        this.viewId = viewId;
        this.isActive = isActive;
        this.departmentName = departmentName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPosName() { return posName; }
    public void setPosName(String posName) { this.posName = posName; }

    public String getPosPhone() { return posPhone; }
    public void setPosPhone(String posPhone) { this.posPhone = posPhone; }

    public String getLarkBaseName() { return larkBaseName; }
    public void setLarkBaseName(String larkBaseName) { this.larkBaseName = larkBaseName; }

    public String getLarkBaseId() { return larkBaseId; }
    public void setLarkBaseId(String larkBaseId) { this.larkBaseId = larkBaseId; }

    public String getKhachHangTableId() { return khachHangTableId; }
    public void setKhachHangTableId(String khachHangTableId) { this.khachHangTableId = khachHangTableId; }

    public String getTraoDoiTableId() { return traoDoiTableId; }
    public void setTraoDoiTableId(String traoDoiTableId) { this.traoDoiTableId = traoDoiTableId; }

    public String getLichHenTableId() { return lichHenTableId; }
    public void setLichHenTableId(String lichHenTableId) { this.lichHenTableId = lichHenTableId; }

    public String getViewId() { return viewId; }
    public void setViewId(String viewId) { this.viewId = viewId; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public static CskhMappingDtoBuilder builder() { return new CskhMappingDtoBuilder(); }

    public static class CskhMappingDtoBuilder {
        private String id;
        private String posName;
        private String posPhone;
        private String larkBaseName;
        private String larkBaseId;
        private String khachHangTableId;
        private String traoDoiTableId;
        private String lichHenTableId;
        private String viewId;
        private Boolean isActive;
        private String departmentName;

        public CskhMappingDtoBuilder id(String id) { this.id = id; return this; }
        public CskhMappingDtoBuilder posName(String posName) { this.posName = posName; return this; }
        public CskhMappingDtoBuilder posPhone(String posPhone) { this.posPhone = posPhone; return this; }
        public CskhMappingDtoBuilder larkBaseName(String larkBaseName) { this.larkBaseName = larkBaseName; return this; }
        public CskhMappingDtoBuilder larkBaseId(String larkBaseId) { this.larkBaseId = larkBaseId; return this; }
        public CskhMappingDtoBuilder khachHangTableId(String khachHangTableId) { this.khachHangTableId = khachHangTableId; return this; }
        public CskhMappingDtoBuilder traoDoiTableId(String traoDoiTableId) { this.traoDoiTableId = traoDoiTableId; return this; }
        public CskhMappingDtoBuilder lichHenTableId(String lichHenTableId) { this.lichHenTableId = lichHenTableId; return this; }
        public CskhMappingDtoBuilder viewId(String viewId) { this.viewId = viewId; return this; }
        public CskhMappingDtoBuilder isActive(Boolean isActive) { this.isActive = isActive; return this; }
        public CskhMappingDtoBuilder departmentName(String departmentName) { this.departmentName = departmentName; return this; }

        public CskhMappingDto build() {
            return new CskhMappingDto(id, posName, posPhone, larkBaseName, larkBaseId,
                    khachHangTableId, traoDoiTableId, lichHenTableId, viewId, isActive, departmentName);
        }
    }
}
