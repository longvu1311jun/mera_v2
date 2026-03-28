package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StatusHistoryDto {

  @JsonProperty("old_status")
  private Integer oldStatus;

  @JsonProperty("status")
  private Integer newStatus;

  @JsonProperty("editor_id")
  private String editorId;

  @JsonProperty("editor_fb")
  private String editorFb;

  @JsonProperty("name")
  private String editorName;

  @JsonProperty("updated_at")
  private String updatedAt;
}
