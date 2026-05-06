package mera.mera_v2.lark.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LarkBitableConfigDto {
    private Long id;
    private String configName;
    private String baseName;
    private String baseId;
    private String tableName;
    private String tableId;
    private String viewId;
    private String userAccessToken;
    private Boolean isDefault;
    private Long shopId;
}
