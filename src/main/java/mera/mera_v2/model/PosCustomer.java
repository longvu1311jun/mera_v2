package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class PosCustomer {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("phone_number")
  private String phoneNumber;

  @JsonProperty("address")
  private String address;

  @JsonProperty("full_address")
  private String fullAddress;

  @JsonProperty("orders")
  private List<PosOrder> orders;

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getFullAddress() {
    return fullAddress;
  }

  public void setFullAddress(String fullAddress) {
    this.fullAddress = fullAddress;
  }

  public List<PosOrder> getOrders() {
    return orders;
  }

  public void setOrders(List<PosOrder> orders) {
    this.orders = orders;
  }
}