package mera.mera_v2.ltkach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.ltkach.dto.LtKhachApiDashboardDto;
import mera.mera_v2.ltkach.dto.RevenueGrowthDto;
import mera.mera_v2.ltkach.dto.RevenueGrowthDto.MonthlyRevenue;
import mera.mera_v2.ltkach.dto.EmployeePerformanceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LtKhachApiService {
  private static final Logger log = LoggerFactory.getLogger(LtKhachApiService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LtKhachService ltKhachService;

    @Value("${pos.api.base-url:https://pos.pages.fm/api/v1}")
    private String posApiBaseUrl;

    @Value("${pos.api.shop-id:1546758}")
    private String shopId;

    @Value("${pos.api.api-key:2a6ed8b51a8d4ae49a851d5876b00018}")
    private String apiKey;

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Lấy dashboard stats từ POS API sử dụng endpoint /get_orders với aggs
     * 
     * @param fromDate LocalDateTime bắt đầu
     * @param toDate LocalDateTime kết thúc
     * @param source all|zalo|facebook
     * @return LtKhachApiDashboardDto chứa các chỉ số
     */
    public LtKhachApiDashboardDto getDashboard(LocalDateTime fromDate, LocalDateTime toDate, String source) {
        long startMs = System.currentTimeMillis();

        if (fromDate == null || toDate == null) {
            LocalDate now = LocalDate.now();
            fromDate = now.withDayOfMonth(1).atStartOfDay();
            toDate = now.atTime(23, 59, 59);
        }

        long startTs = toTimestamp(fromDate);
        long endTs = toTimestamp(toDate);

        log.info("[LtKhachApi] Dashboard: {} -> {} (ts: {} -> {}), source={}", 
                fromDate, toDate, startTs, endTs, source);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        // Build body dựa trên source
        String body;
        if ("zalo".equalsIgnoreCase(source)) {
            body = "{\"order_sources\": [\"-8\"]}";
        } else if ("facebook".equalsIgnoreCase(source)) {
            body = "{\"order_sources\": [\"-1\"]}";
        } else {
            body = "{}";
        }
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        // API 1: Không có status → lấy phân bố status (đơn mới, hủy, hoàn)
        String url1 = buildUrl(startTs, endTs, source, null);
        log.info("[LtKhachApi] API 1 (status distribution): {}", maskUrl(url1));
        
        String response1Body = callApi(url1, entity);
        LtKhachApiDashboardDto dto = parseStatusDistribution(response1Body, source, startTs, endTs, fromDate, toDate);

        // API 2: status=3 → lấy totalRevenue từ đơn hoàn thành
        String url2 = buildUrl(startTs, endTs, source, "3");
        log.info("[LtKhachApi] API 2 (completed revenue): {}", maskUrl(url2));
        
        String response2Body = callApi(url2, entity);
        parseRevenue(response2Body, dto);

        log.info("[LtKhachApi] Dashboard completed in {}ms", System.currentTimeMillis() - startMs);
        return dto;
    }

    private String buildUrl(long startTs, long endTs, String source, String status) {
        String url = String.format("%s/shops/%s/orders/get_orders", posApiBaseUrl, shopId);
        
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("api_key=").append(apiKey);
        queryBuilder.append("&page_size=1");
        queryBuilder.append("&page=1");
        
        // updateStatus theo nguồn:
        // - Zalo: status=1 (ngày xác nhận)
        // - Facebook/All: inserted_at (ngày tạo đơn)
        String updateStatus = "inserted_at";
        if ("zalo".equalsIgnoreCase(source)) {
            updateStatus = "1";
        }
        queryBuilder.append("&updateStatus=").append(updateStatus);
        
        // Lọc status nếu có
        if (status != null) {
            queryBuilder.append("&status=").append(status);
        }
        
        queryBuilder.append("&editorId=none");
        queryBuilder.append("&option_sort=inserted_at_desc");
        queryBuilder.append("&startDateTime=").append(startTs);
        queryBuilder.append("&endDateTime=").append(endTs);
        queryBuilder.append("&es_only=true");
        queryBuilder.append("&extra_fields[]=all");
        queryBuilder.append("&is_filter_multiple_source=false");
        queryBuilder.append("&is_filter_exclude_partner=false");
        queryBuilder.append("&is_filter_multiple_field_address=false");
        queryBuilder.append("&is_filter_exclude_product_tag=false");
        queryBuilder.append("&is_filter_attributes_by_or=true");
        queryBuilder.append("&is_filter_multiple_partner=false");
        queryBuilder.append("&is_filter_customer_tag_by_or=true");
        queryBuilder.append("&is_filter_exclude_customer_tag=false");
        queryBuilder.append("&is_filter_multiple_employee=false");
        queryBuilder.append("&is_filter_conversation_tag_by_or=true");
        queryBuilder.append("&is_filter_exclude_conversation_tag=false");
        queryBuilder.append("&is_filter_multiple_promotion=false");
        queryBuilder.append("&is_filter_exclude_exchange=false");
        queryBuilder.append("&is_filter_multiple_ads_source=false");
        queryBuilder.append("&is_filter_exclude_ads_source=false");
        queryBuilder.append("&is_filter_exclude_warehouse=false");
        queryBuilder.append("&is_filter_exclude=false");
        queryBuilder.append("&is_filter_tag_by_or=true");
        queryBuilder.append("&is_filter_order_tag_by_or=true");
        queryBuilder.append("&is_filter_product_by_or=true");

        return url + "?" + queryBuilder.toString();
    }

    private String callApi(String url, HttpEntity<String> entity) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("[LtKhachApi] Error calling POS API: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse response để lấy phân bố status
     */
    private LtKhachApiDashboardDto parseStatusDistribution(String responseBody, String source, 
            long startTs, long endTs, LocalDateTime fromDate, LocalDateTime toDate) {
        LtKhachApiDashboardDto dto = new LtKhachApiDashboardDto();
        dto.setSource(source);
        dto.setFromTimestamp(startTs);
        dto.setToTimestamp(endTs);
        dto.setFromDate(fromDate.format(DT_FORMATTER));
        dto.setToDate(toDate.format(DT_FORMATTER));

        try {
            if (responseBody == null || responseBody.isEmpty()) {
                log.warn("[LtKhachApi] Empty response");
                return createEmptyDashboard(source, startTs, endTs, fromDate, toDate);
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode aggsNode = root.path("aggs");

            if (aggsNode.isMissingNode()) {
                log.warn("[LtKhachApi] No aggs in response");
                return createEmptyDashboard(source, startTs, endTs, fromDate, toDate);
            }

            // Parse status buckets
            JsonNode statusNode = aggsNode.path("status");
            JsonNode bucketsNode = statusNode.path("buckets");

            long totalOrders = 0;
            long newOrders = 0;
            long completedOrders = 0;
            long returnedOrders = 0;
            long cancelledOrders = 0;

            if (bucketsNode.isArray()) {
                for (JsonNode bucket : bucketsNode) {
                    long key = bucket.path("key").asLong();
                    long count = bucket.path("doc_count").asLong();
                    totalOrders += count;

                    // Status theo user:
                    // key=0: Đơn Mới
                    // key=3: Hoàn thành
                    // key=5: Đơn hoàn
                    // key=6: Đơn hủy
                    switch ((int) key) {
                        case 0:  // Đơn Mới
                            newOrders += count;
                            break;
                        case 3:  // Hoàn thành
                            completedOrders += count;
                            break;
                        case 5:  // Hoàn trả
                            returnedOrders += count;
                            break;
                        case 6:  // Hủy
                            cancelledOrders += count;
                            break;
                        default:
                            // Các status khác (2, 4, 7, 15...) không hiển thị
                            break;
                    }
                }
            }

            dto.setTotalOrders(totalOrders);
            dto.setNewOrders(newOrders);
            dto.setCompletedOrders(completedOrders);
            dto.setReturnedOrders(returnedOrders);
            dto.setCancelledOrders(cancelledOrders);
            dto.setTotalRevenue(BigDecimal.ZERO);
            dto.setCod(BigDecimal.ZERO);
            dto.setPrepaid(BigDecimal.ZERO);

            log.info("[LtKhachApi] Status distribution - total:{}, new:{}, completed:{}, returned:{}, cancelled:{}",
                    totalOrders, newOrders, completedOrders, returnedOrders, cancelledOrders);

        } catch (Exception e) {
            log.error("[LtKhachApi] Error parsing status distribution: {}", e.getMessage(), e);
            return createEmptyDashboard(source, startTs, endTs, fromDate, toDate);
        }

        return dto;
    }

    /**
     * Parse response để lấy revenue từ đơn hoàn thành
     */
    private void parseRevenue(String responseBody, LtKhachApiDashboardDto dto) {
        if (responseBody == null || responseBody.isEmpty()) {
            log.warn("[LtKhachApi] Empty revenue response");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode aggsNode = root.path("aggs");

            if (aggsNode.isMissingNode()) {
                return;
            }

            // Parse status=3 count (completed orders) - Tìm đúng bucket có key=3
            long completedCount = 0;
            JsonNode statusBuckets = aggsNode.path("status").path("buckets");
            if (statusBuckets.isArray()) {
                for (JsonNode bucket : statusBuckets) {
                    long bucketKey = bucket.path("key").asLong();
                    if (bucketKey == 3) {
                        completedCount = bucket.path("doc_count").asLong(0);
                        log.info("[LtKhachApi] Found status=3 bucket with doc_count={}", completedCount);
                        break;
                    }
                }
            }
            dto.setCompletedOrders(completedCount);

            // Parse total_price từ đơn hoàn thành
            double totalPrice = aggsNode.path("total_price").path("value").asDouble(0);
            dto.setTotalRevenue(BigDecimal.valueOf(totalPrice));
            
            // Parse cod
            double cod = aggsNode.path("cod").path("value").asDouble(0);
            dto.setCod(BigDecimal.valueOf(cod));

            // Parse prepaid
            double prepaid = aggsNode.path("prepaid").path("value").asDouble(0);
            dto.setPrepaid(BigDecimal.valueOf(prepaid));

            log.info("[LtKhachApi] Revenue from completed orders: totalRevenue={}, cod={}, prepaid={}",
                    totalPrice, cod, prepaid);

        } catch (Exception e) {
            log.error("[LtKhachApi] Error parsing revenue: {}", e.getMessage());
        }
    }

    private LtKhachApiDashboardDto createEmptyDashboard(String source, long startTs, long endTs, 
            LocalDateTime fromDate, LocalDateTime toDate) {
        LtKhachApiDashboardDto dto = new LtKhachApiDashboardDto();
        dto.setSource(source);
        dto.setFromTimestamp(startTs);
        dto.setToTimestamp(endTs);
        dto.setFromDate(fromDate.format(DT_FORMATTER));
        dto.setToDate(toDate.format(DT_FORMATTER));
        dto.setTotalOrders(0);
        dto.setNewOrders(0);
        dto.setCompletedOrders(0);
        dto.setReturnedOrders(0);
        dto.setCancelledOrders(0);
        dto.setTotalRevenue(BigDecimal.ZERO);
        dto.setCod(BigDecimal.ZERO);
        dto.setPrepaid(BigDecimal.ZERO);
        return dto;
    }

    private long toTimestamp(LocalDateTime dateTime) {
        ZonedDateTime zdt = dateTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        return zdt.toEpochSecond();
    }

    private String maskUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("api_key=[^&]+", "api_key=***");
    }

    /**
     * Lấy dữ liệu tăng trưởng doanh thu 6 tháng gần nhất
     * 
     * @param source all|zalo|facebook
     * @return RevenueGrowthDto chứa dữ liệu 6 tháng
     */
    public RevenueGrowthDto getRevenueGrowth(String source) {
        RevenueGrowthDto result = new RevenueGrowthDto();
        result.setSource(source);

        LocalDate now = LocalDate.now();
        List<MonthlyRevenue> months = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        String body;
        if ("zalo".equalsIgnoreCase(source)) {
            body = "{\"order_sources\": [\"-8\"]}";
        } else if ("facebook".equalsIgnoreCase(source)) {
            body = "{\"order_sources\": [\"-1\"]}";
        } else {
            body = "{}";
        }
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        // Lấy 6 tháng gần nhất
        for (int i = 5; i >= 0; i--) {
            LocalDate monthStart = now.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

            long startTs = toTimestamp(monthStart.atStartOfDay());
            long endTs = toTimestamp(monthEnd.atTime(23, 59, 59));

            String url = buildUrl(startTs, endTs, source, "3");
            
            try {
                String responseBody = callApi(url, entity);
                MonthlyRevenue mr = parseMonthlyRevenue(responseBody, monthStart);
                months.add(mr);
                log.info("[LtKhachApi] Month {} - orders:{}, revenue:{}", 
                        mr.getMonth(), mr.getOrderCount(), mr.getRevenue());
            } catch (Exception e) {
                log.error("[LtKhachApi] Error getting revenue for {}: {}", monthStart, e.getMessage());
                MonthlyRevenue mr = new MonthlyRevenue();
                mr.setMonth(monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM")));
                mr.setMonthName("Tháng " + monthStart.getMonthValue() + "/" + monthStart.getYear());
                mr.setOrderCount(0);
                mr.setRevenue(BigDecimal.ZERO);
                mr.setCod(BigDecimal.ZERO);
                mr.setPrepaid(BigDecimal.ZERO);
                months.add(mr);
            }
        }

        result.setMonths(months);
        return result;
    }

    private MonthlyRevenue parseMonthlyRevenue(String responseBody, LocalDate month) {
        MonthlyRevenue mr = new MonthlyRevenue();
        mr.setMonth(month.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        mr.setMonthName("Tháng " + month.getMonthValue() + "/" + month.getYear());

        if (responseBody == null || responseBody.isEmpty()) {
            mr.setOrderCount(0);
            mr.setRevenue(BigDecimal.ZERO);
            mr.setCod(BigDecimal.ZERO);
            mr.setPrepaid(BigDecimal.ZERO);
            return mr;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode aggsNode = root.path("aggs");

            // Parse count
            long count = 0;
            JsonNode statusBuckets = aggsNode.path("status").path("buckets");
            if (statusBuckets.isArray()) {
                for (JsonNode bucket : statusBuckets) {
                    if (bucket.path("key").asLong() == 3) {
                        count = bucket.path("doc_count").asLong(0);
                        break;
                    }
                }
            }
            mr.setOrderCount(count);

            // Parse revenue
            double totalPrice = aggsNode.path("total_price").path("value").asDouble(0);
            mr.setRevenue(BigDecimal.valueOf(totalPrice));

            double cod = aggsNode.path("cod").path("value").asDouble(0);
            mr.setCod(BigDecimal.valueOf(cod));

            double prepaid = aggsNode.path("prepaid").path("value").asDouble(0);
            mr.setPrepaid(BigDecimal.valueOf(prepaid));

        } catch (Exception e) {
            log.error("[LtKhachApi] Error parsing monthly revenue: {}", e.getMessage());
            mr.setOrderCount(0);
            mr.setRevenue(BigDecimal.ZERO);
            mr.setCod(BigDecimal.ZERO);
            mr.setPrepaid(BigDecimal.ZERO);
        }

        return mr;
    }

    public List<EmployeePerformanceDto> getPerformanceReport(LocalDateTime fromDate, LocalDateTime toDate, String source) {
        return ltKhachService.getPerformanceReport(fromDate, toDate, source);
    }

    public List<?> getOrderDetails(LocalDateTime fromDate, LocalDateTime toDate, String creatorId, Boolean useInsertedAt, Boolean zaloOnly, int limit) {
        return ltKhachService.getOrderDetails(fromDate, toDate, creatorId, useInsertedAt, zaloOnly, limit);
    }
}
