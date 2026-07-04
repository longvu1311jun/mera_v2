package mera.mera_v2.pos.sync.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerSyncRequest {

    private String startDate;
    private String endDate;
    private Integer pageSize;
    private Integer startPage;
}