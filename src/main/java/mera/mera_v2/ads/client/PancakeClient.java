package mera.mera_v2.ads.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import mera.mera_v2.ads.config.PancakeProperties;
import mera.mera_v2.ads.model.PancakeListOrdersResponse;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
public class PancakeClient {

    private final WebClient pancakeWebClient;
    private final PancakeProperties props;
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

    private long startOfDayEpochSec(LocalDate d) {
        ZonedDateTime z = d.atStartOfDay(zone);
        return z.toEpochSecond();
    }

    private long endOfDayEpochSec(LocalDate d) {
        ZonedDateTime z = d.plusDays(1).atStartOfDay(zone).minusSeconds(1);
        return z.toEpochSecond();
    }

    public PancakeListOrdersResponse getOrdersFirstPage(LocalDate from, LocalDate to, int page) {
        long startSec = startOfDayEpochSec(from);
        long endSec = endOfDayEpochSec(to);

        System.out.println("[PancakeClient] GET orders page=" + page + " from=" + from + " to=" + to);

        PancakeListOrdersResponse response = pancakeWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/shops/{shopId}/orders")
                        .queryParam("api_key", props.getApiKey())
                        .queryParam("page_size", 100)
                        .queryParam("status", -1)
                        .queryParam("page", page)
                        .queryParam("updateStatus", "inserted_at")
                        .queryParam("editorId", "none")
                        .queryParam("option_sort", "inserted_at_desc")
                        .queryParam("startDateTime", startSec)
                        .queryParam("endDateTime", endSec)
                        .queryParam("is_filter_tag_by_or", true)
                        .queryParam("is_filter_order_tag_by_or", true)
                        .queryParam("is_filter_product_by_or", true)
                        .queryParam("is_filter_multiple_source", false)
                        .queryParam("is_filter_exclude_partner", false)
                        .queryParam("is_filter_multiple_field_address", false)
                        .queryParam("is_filter_exclude_product_tag", false)
                        .queryParam("is_filter_attributes_by_or", true)
                        .queryParam("is_filter_multiple_partner", false)
                        .queryParam("is_filter_customer_tag_by_or", true)
                        .queryParam("is_filter_exclude_customer_tag", false)
                        .queryParam("is_filter_multiple_employee", false)
                        .queryParam("is_filter_conversation_tag_by_or", true)
                        .queryParam("is_filter_exclude_conversation_tag", false)
                        .queryParam("is_filter_multiple_promotion", false)
                        .queryParam("is_filter_exclude_exchange", false)
                        .queryParam("is_filter_multiple_ads_source", false)
                        .queryParam("is_filter_exclude_ads_source", false)
                        .queryParam("is_filter_exclude_warehouse", false)
                        .queryParam("is_filter_exclude", false)
                        .queryParam("es_only", true)
                        .queryParam("extra_fields[]", "all")
                        .build(props.getShopId()))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(PancakeListOrdersResponse.class)
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .block();

        System.out.println("[PancakeClient] Response - pageNumber: " + (response != null ? response.pageNumberSafe() : "null")
                + ", totalEntries: " + (response != null ? response.totalSafe() : 0)
                + ", orders size: " + (response != null ? response.ordersSafe().size() : 0));

        return response;
    }

    public String getOrdersRaw(LocalDate from, LocalDate to, int page, Integer status) {
        long startSec = startOfDayEpochSec(from);
        long endSec = endOfDayEpochSec(to);

        var builder = pancakeWebClient.get()
                .uri(uriBuilder -> {
                    var b = uriBuilder
                            .path("/shops/{shopId}/orders")
                            .queryParam("api_key", props.getApiKey())
                            .queryParam("page_size", 100)
                            .queryParam("page", page)
                            .queryParam("updateStatus", "inserted_at")
                            .queryParam("editorId", "none")
                            .queryParam("option_sort", "inserted_at_desc")
                            .queryParam("startDateTime", startSec)
                            .queryParam("endDateTime", endSec)
                            .queryParam("is_filter_tag_by_or", true)
                            .queryParam("is_filter_order_tag_by_or", true)
                            .queryParam("is_filter_product_by_or", true)
                            .queryParam("is_filter_multiple_source", false)
                            .queryParam("is_filter_exclude_partner", false)
                            .queryParam("is_filter_multiple_field_address", false)
                            .queryParam("is_filter_exclude_product_tag", false)
                            .queryParam("is_filter_attributes_by_or", true)
                            .queryParam("is_filter_multiple_partner", false)
                            .queryParam("is_filter_customer_tag_by_or", true)
                            .queryParam("is_filter_exclude_customer_tag", false)
                            .queryParam("is_filter_multiple_employee", false)
                            .queryParam("is_filter_conversation_tag_by_or", true)
                            .queryParam("is_filter_exclude_conversation_tag", false)
                            .queryParam("is_filter_multiple_promotion", false)
                            .queryParam("is_filter_exclude_exchange", false)
                            .queryParam("is_filter_multiple_ads_source", false)
                            .queryParam("is_filter_exclude_ads_source", false)
                            .queryParam("is_filter_exclude_warehouse", false)
                            .queryParam("is_filter_exclude", false)
                            .queryParam("es_only", true)
                            .queryParam("extra_fields[]", "all");
                    if (status != null) {
                        b.queryParam("status", status);
                    }
                    return b.build(props.getShopId());
                })
                .accept(MediaType.APPLICATION_JSON);

        return builder.retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .block();
    }
}
