package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PosOrder {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("code")
  private String code;

  @JsonProperty("total")
  private Double total;

  @JsonProperty("created_at")
  private String createdAt;

  @JsonProperty("line_items")
  private List<PosSale> lineItems;


  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public Double getTotal() {
    return total;
  }

  public void setTotal(Double total) {
    this.total = total;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public List<PosSale> getLineItems() {
    return lineItems;
  }

  public void setLineItems(List<PosSale> lineItems) {
    this.lineItems = lineItems;
  }
}

