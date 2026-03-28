package mera.mera_v2.lark.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO để lưu mapping giữa Base và Table
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseTableMapping {
    private String baseName;
    private String baseId;
    private String tableId;
    private String tableName;
}
