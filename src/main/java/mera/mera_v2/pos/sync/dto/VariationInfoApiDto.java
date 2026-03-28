package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariationInfoApiDto {

  private String name;

  @JsonProperty("retail_price")
  private BigDecimal retailPrice;

  private String barcode;

  private BigDecimal weight;
}