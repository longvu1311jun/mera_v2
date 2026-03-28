package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PosCustomerAddress {
    @JsonProperty("address")
    private String address;

    @JsonProperty("commune_id")
    private String communeId;

    @JsonProperty("country_code")
    private Integer countryCode;

    @JsonProperty("district_id")
    private String districtId;

    @JsonProperty("full_address")
    private String fullAddress;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("id")
    private String id;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("post_code")
    private String postCode;

    @JsonProperty("province_id")
    private String provinceId;

    // Getters and setters
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCommuneId() { return communeId; }
    public void setCommuneId(String communeId) { this.communeId = communeId; }

    public Integer getCountryCode() { return countryCode; }
    public void setCountryCode(Integer countryCode) { this.countryCode = countryCode; }

    public String getDistrictId() { return districtId; }
    public void setDistrictId(String districtId) { this.districtId = districtId; }

    public String getFullAddress() { return fullAddress; }
    public void setFullAddress(String fullAddress) { this.fullAddress = fullAddress; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getPostCode() { return postCode; }
    public void setPostCode(String postCode) { this.postCode = postCode; }

    public String getProvinceId() { return provinceId; }
    public void setProvinceId(String provinceId) { this.provinceId = provinceId; }
}

