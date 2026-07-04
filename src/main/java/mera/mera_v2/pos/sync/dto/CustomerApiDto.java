package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerApiDto {

    private String id;

    @JsonProperty("customer_id")
    private String customerId;

    private String name;

    private String gender;

    @JsonProperty("fb_id")
    private String fbId;

    @JsonProperty("referral_code")
    private String referralCode;

    @JsonProperty("phone_numbers")
    private List<String> phoneNumbers = new ArrayList<>();

    @JsonProperty("shop_id")
    private Long shopId;

    @JsonProperty("inserted_at")
    private String insertedAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    private List<NoteApiDto> notes = new ArrayList<>();
}