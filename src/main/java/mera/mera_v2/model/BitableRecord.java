package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class BitableRecord {

  @JsonProperty("record_id")
  private String recordId;

  @JsonProperty("fields")
  private Map<String, Object> fields;

  public String getRecordId() {
    return recordId;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public Map<String, Object> getFields() {
    return fields;
  }

  public void setFields(Map<String, Object> fields) {
    this.fields = fields;
  }
}
