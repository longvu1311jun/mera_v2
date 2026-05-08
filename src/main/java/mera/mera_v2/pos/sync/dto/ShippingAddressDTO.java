package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingAddressDTO {

  @JsonProperty("full_name")
  private String fullName;

  @JsonProperty("phone_number")
  private String phoneNumber;

  private String address;

  @JsonProperty("full_address")
  private String fullAddress;

  @JsonProperty("province_name")
  private String provinceName;

  @JsonProperty("district_name")
  private String districtName;

  // API cÃ³ thá»ƒ sai chÃ­nh táº£ "commnue_name"
  @JsonAlias({"commnue_name", "commune_name"})
  @JsonProperty("commune_name")
  private String communeName;

  @JsonProperty("province_id")
  private String provinceId;

  @JsonProperty("district_id")
  private String districtId;

  @JsonProperty("commune_id")
  private String communeId;

  @JsonProperty("country_code")
  private String countryCode;

  @JsonProperty("post_code")
  private String postCode;
}