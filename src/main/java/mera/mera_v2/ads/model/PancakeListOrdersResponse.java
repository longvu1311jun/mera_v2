package mera.mera_v2.ads.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PancakeListOrdersResponse {

    @JsonProperty("orders")
    private List<PancakeOrder> orders;

    @JsonProperty("data")
    private List<PancakeOrder> data;

    @JsonProperty("total_pages")
    private Integer totalPages;

    @JsonProperty("total")
    private Integer total;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("total_count")
    private Integer totalCount;

    @JsonProperty("total_entries")
    private Integer totalEntries;

    @JsonProperty("page_number")
    private Integer pageNumber;

    public List<PancakeOrder> ordersSafe() {
        if (orders != null) return orders;
        if (data != null) return data;
        return Collections.emptyList();
    }

    public int totalPagesSafe() {
        return totalPages == null ? 0 : totalPages;
    }

    public int totalSafe() {
        if (totalEntries != null) return totalEntries;
        if (total != null) return total;
        if (totalCount != null) return totalCount;
        if (count != null) return count;
        return 0;
    }

    public int pageNumberSafe() {
        return pageNumber == null ? 1 : pageNumber;
    }

    public List<PancakeOrder> getOrders() { return orders; }
    public void setOrders(List<PancakeOrder> orders) { this.orders = orders; }

    public List<PancakeOrder> getData() { return data; }
    public void setData(List<PancakeOrder> data) { this.data = data; }

    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getTotalEntries() { return totalEntries; }
    public void setTotalEntries(Integer totalEntries) { this.totalEntries = totalEntries; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
}
