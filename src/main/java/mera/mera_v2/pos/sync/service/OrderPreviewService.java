package mera.mera_v2.pos.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.sync.client.OrderApiClient;
import mera.mera_v2.pos.sync.dto.OrderApiDto;
import mera.mera_v2.pos.sync.dto.OrderPreviewRequest;
import mera.mera_v2.pos.sync.exceptions.ApiClientException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPreviewService {

  private final OrderApiClient orderApiClient;

  /**
   * Fetch orders from Pancake API with dynamic parameters.
   *
   * Date logic:
   * - startDate: take the selected date at 00:00:00, subtract 7 hours
   *   Example: user picks 2025-12-01 -> 2025-12-01 00:00:00 -> minus 7h -> 2025-11-30 17:00:00 UTC
   *            -> Unix timestamp (seconds) = 1732976400
   * - endDate: take the selected date at 23:59:59, subtract 7 hours
   *   Example: user picks 2025-12-31 -> 2025-12-31 23:59:59 -> minus 7h -> 2025-12-31 16:59:59 UTC
   *            -> Unix timestamp (seconds) = 1735669799
   *
   * This is because the Pancake API server treats timestamps as UTC+0,
   * and our server is at UTC+7, so we need to shift back 7 hours.
   */
  public OrderPreviewResult preview(OrderPreviewRequest req) {
    // Parse and validate dates
    LocalDate startLocal = parseDate(req.getStartDate(), "startDate");
    LocalDate endLocal = parseDate(req.getEndDate(), "endDate");

    if (endLocal.isBefore(startLocal)) {
      throw new IllegalArgumentException("endDate must not be before startDate");
    }

    // Convert to Unix timestamp (seconds)
    // startDate: 00:00:00 local, minus 7h -> UTC
    LocalDateTime startDt = LocalDateTime.of(startLocal, LocalTime.of(0, 0, 0));
    long startTs = startDt.minusHours(7).toEpochSecond(ZoneOffset.UTC);

    // endDate: 23:59:59 local, minus 7h -> UTC
    LocalDateTime endDt = LocalDateTime.of(endLocal, LocalTime.of(23, 59, 59));
    long endTs = endDt.minusHours(7).toEpochSecond(ZoneOffset.UTC);

    int pageNum = req.getPageNumber() != null ? req.getPageNumber() : 1;
    int pageSz = req.getPageSize() != null ? req.getPageSize() : 200;
    String updStatus = req.getUpdateStatus() != null ? req.getUpdateStatus() : "inserted_at";
    String status = req.getStatus();

    // Log request summary
    log.info("Preview request: startDate={}, endDate={}, status={}, page={}, size={}, updateStatus={}",
        req.getStartDate(), req.getEndDate(),
        (status != null && !status.isBlank()) ? status : "none",
        pageNum, pageSz, updStatus);
    log.info("Timestamps: startDateTime={}, endDateTime={}", startTs, endTs);

    // Call API via client with dynamic params
    var resp = orderApiClient.fetchOrdersPage(
        startTs, endTs, pageNum, pageSz, updStatus, status
    );
    List<OrderApiDto> orders = resp.getData();
    if (orders == null) {
      orders = List.of();
    }

    return OrderPreviewResult.builder()
        .totalFromApi(orders.size())
        .startTimestamp(startTs)
        .endTimestamp(endTs)
        .startDate(req.getStartDate())
        .endDate(req.getEndDate())
        .pageNumber(pageNum)
        .pageSize(pageSz)
        .updateStatus(updStatus)
        .status(status)
        .orders(orders)
        .build();
  }

  private LocalDate parseDate(String dateStr, String fieldName) {
    if (dateStr == null || dateStr.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required (format: yyyy-MM-dd)");
    }
    try {
      return LocalDate.parse(dateStr);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          fieldName + " must be in format yyyy-MM-dd, got: " + dateStr);
    }
  }
}

