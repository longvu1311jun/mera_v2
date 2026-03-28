package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerDTO {

  private String id;

  private String name;

  @JsonProperty("customer_id")
  private String customerId;

  @JsonProperty("fb_id")
  private String fbId;

  private String gender;

  @JsonProperty("referral_code")
  private String referralCode;

  @JsonProperty("phone_numbers")
  private List<String> phoneNumbers;

  @JsonProperty("inserted_at")
  private String insertedAt;

  @JsonProperty("updated_at")
  private String updatedAt;
}

