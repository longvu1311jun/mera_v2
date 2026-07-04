package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerListResponseDto {

    private List<CustomerApiDto> data = new ArrayList<>();

    private Integer currentPage;
    private Integer pageNumber;
    private Integer totalPages;
    private Integer totalEntries;
    private Integer total;
}