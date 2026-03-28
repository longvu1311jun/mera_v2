package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WarehouseDTO {
  private String id;
  private String name;
  private String phoneNumber;
  private String address;
  private String fullAddress;
  private String provinceId;
  private String districtId;
  private String communeId;
}

