package mera.mera_v2.cskh.mapping;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
