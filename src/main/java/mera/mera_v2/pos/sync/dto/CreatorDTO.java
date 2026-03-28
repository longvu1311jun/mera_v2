package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreatorDTO {

  private String id;

  private String name;

  private String email;

  private String phoneNumber;

  private String fbId;

  private String avatarUrl;
}


