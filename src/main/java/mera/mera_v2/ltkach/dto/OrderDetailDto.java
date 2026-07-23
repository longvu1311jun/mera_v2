package mera.mera_v2.ltkach.dto;

import lombok.Data;

@Data
public class OrderDetailDto {
    private Long id;
    private String orderCode;
    private Integer status;
    private String statusName;
    private java.math.BigDecimal totalPrice;
    private java.math.BigDecimal cod;
    private java.math.BigDecimal prepaid;
    private java.time.LocalDateTime insertedAt;
    private String customerName;
    private String customerPhone;
    private String creatorName;
    private String creatorId;
    private String orderSourcesName;
    private String products;
    private Integer ltCount;
    private Boolean ltType;
}
