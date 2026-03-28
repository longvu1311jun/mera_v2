package mera.mera_v2.pos.sync.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderPreviewRequest {

  private String startDate;
  private String endDate;
  private String status;
  private Integer pageNumber = 1;
  private Integer pageSize = 200;
  private Integer startPage = 1;
  private String updateStatus = "inserted_at";
}

