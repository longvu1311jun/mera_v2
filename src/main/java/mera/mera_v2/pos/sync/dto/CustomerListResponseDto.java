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
public class CustomerListResponseDto {

    private Boolean success;

    private List<CustomerApiDto> data = new ArrayList<>();

    @JsonProperty("current_page")
    private Integer currentPage;

    @JsonProperty("page_number")
    private Integer pageNumber;

    @JsonProperty("page_size")
    private Integer pageSize;

    @JsonProperty("total_pages")
    private Integer totalPages;

    @JsonProperty("total_entries")
    private Integer totalEntries;

    private Integer total;
}
