package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitableTable {
  @JsonProperty("name")
  private String name;

  @JsonProperty("table_id")
  private String tableId;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTableId() {
    return tableId;
  }

  public void setTableId(String tableId) {
    this.tableId = tableId;
  }
}
