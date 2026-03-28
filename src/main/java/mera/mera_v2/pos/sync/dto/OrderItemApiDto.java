package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderItemApiDto {

  private String id;

  @JsonProperty("product_id")
  private String productId;

  @JsonProperty("variation_id")
  private String variationId;

  private Integer quantity;

  @JsonProperty("discount_each_product")
  private BigDecimal discountEachProduct;

  @JsonProperty("total_discount")
  private BigDecimal totalDiscount;

  @JsonProperty("variation_info")
  private VariationInfoApiDto variationInfo;
}