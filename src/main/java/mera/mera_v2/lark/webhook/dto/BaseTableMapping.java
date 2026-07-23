package mera.mera_v2.lark.webhook.dto;

/**
 * DTO để lưu mapping giữa Base và Table
 */
public class BaseTableMapping {
    private String baseName;
    private String baseId;
    private String tableId;
    private String tableName;

    public BaseTableMapping() {}

    public BaseTableMapping(String baseName, String baseId, String tableId, String tableName) {
        this.baseName = baseName;
        this.baseId = baseId;
        this.tableId = tableId;
        this.tableName = tableName;
    }

    public String getBaseName() { return baseName; }
    public void setBaseName(String baseName) { this.baseName = baseName; }

    public String getBaseId() { return baseId; }
    public void setBaseId(String baseId) { this.baseId = baseId; }

    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
}
