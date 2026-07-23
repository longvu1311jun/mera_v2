package mera.mera_v2.ltkach.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PosApiAggsResponseDto {
    private Aggs aggs;

    @Data
    public static class Aggs {
        private TotalPrice total_price;
        private Cod cod;
        private Prepaid prepaid;
        private ReconciledCod reconciled_cod;
        private Status status;
        private Tag tag;
    }

    @Data
    public static class TotalPrice {
        private Double value;
    }

    @Data
    public static class Cod {
        private Double value;
    }

    @Data
    public static class Prepaid {
        private Double value;
    }

    @Data
    public static class ReconciledCod {
        private Double value;
    }

    @Data
    public static class Status {
        private String doc_count_error_upper_bound;
        private String sum_other_doc_count;
        private List<StatusBucket> buckets;
    }

    @Data
    public static class StatusBucket {
        private long doc_count;
        private String key;
    }

    @Data
    public static class Tag {
        private Object buckets;
        @JsonProperty("doc_count")
        private long docCount;
    }
}
