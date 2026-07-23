package mera.mera_v2.ltkach.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChartDataDto {
    private List<String> labels;
    private List<Double> revenues;
}
