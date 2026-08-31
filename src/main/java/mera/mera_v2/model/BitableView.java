package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitableView {
  @JsonProperty("view_id")
  private String viewId;

  @JsonProperty("view_name")
  private String name;

  @JsonProperty("view_type")
  private String viewType;

  public String getViewId() {
    return viewId;
  }

  public void setViewId(String viewId) {
    this.viewId = viewId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getViewType() {
    return viewType;
  }

  public void setViewType(String viewType) {
    this.viewType = viewType;
  }
}
