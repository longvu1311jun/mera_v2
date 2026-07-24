package mera.mera_v2.ads.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import mera.mera_v2.ads.client.PancakeClient;
import mera.mera_v2.ads.model.AdsReportResponse;
import mera.mera_v2.ads.model.AdsReportRow;
import mera.mera_v2.ads.model.PancakeListOrdersResponse;
import mera.mera_v2.ads.model.PancakeOrder;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AdsReportService {

    private final PancakeClient pancakeClient;
    private final Cache<String, AdsReportResponse> cache;
    private final ExecutorService executor;

    public AdsReportService(PancakeClient pancakeClient,
                          @Value("${cache.ttl-minutes:60}") long ttlMinutes) {
        this.pancakeClient = pancakeClient;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(200)
                .build();
        // 10 threads for parallel API calls
        this.executor = Executors.newFixedThreadPool(10);
    }

    public AdsReportResponse getReport(LocalDate from, LocalDate to, String sort, boolean refresh) {
        String key = from + "_" + to + "_" + (sort == null ? "" : sort);
        if (!refresh) {
            AdsReportResponse cached = cache.getIfPresent(key);
            if (cached != null) return cached;
        }

        return buildReportInternal(from, to, sort);
    }

    public String testApiCall(LocalDate from, LocalDate to) {
        return "Raw response:\n" + pancakeClient.getOrdersRaw(from, to, 1, null);
    }

    private AdsReportResponse buildReportInternal(LocalDate from, LocalDate to, String sort) {
        Map<String, Agg> aggs = new ConcurrentHashMap<>();
        AtomicLong pulled = new AtomicLong(0);

        log.info("[AdsReportService] Starting fetch - from={} to={}", from, to);

        try {
            // First call to get total entries
            PancakeListOrdersResponse first = pancakeClient.getOrdersFirstPage(from, to, 1);
            int totalEntries = first.totalSafe();
            int page1Size = first.ordersSafe().size();

            log.info("[AdsReportService] Total entries: {}, page1 size: {}", totalEntries, page1Size);

            if (totalEntries == 0) {
                log.info("[AdsReportService] No orders found!");
                return buildResponse(from, to, sort, new ArrayList<>(), 0);
            }

            // Process page 1
            processOrders(first.ordersSafe(), aggs, pulled);

            int totalPages = (int) Math.ceil((double) totalEntries / 100);

            if (totalPages > 1) {
                log.info("[AdsReportService] Fetching {} more pages with 10 parallel threads", totalPages - 1);

                // Create list of page numbers to fetch
                List<Integer> pagesToFetch = new ArrayList<>();
                for (int p = 2; p <= totalPages; p++) {
                    pagesToFetch.add(p);
                }

                // Fetch all pages in parallel
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int page : pagesToFetch) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            PancakeListOrdersResponse pageData = pancakeClient.getOrdersFirstPage(from, to, page);
                            log.debug("[AdsReportService] Fetched page {}/{} with {} orders",
                                    page, totalPages, pageData.ordersSafe().size());
                            processOrders(pageData.ordersSafe(), aggs, pulled);
                        } catch (Exception e) {
                            log.error("[AdsReportService] Error fetching page {}: {}", page, e.getMessage());
                        }
                    }, executor);
                    futures.add(future);
                }

                // Wait for all pages to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

        } catch (Exception e) {
            log.error("[AdsReportService] Error building report: {}", e.getMessage(), e);
        }

        // Build response
        List<AdsReportRow> rows = new ArrayList<>();
        for (Agg a : aggs.values()) {
            rows.add(new AdsReportRow(
                    a.postId,
                    a.adsName,
                    a.totalOrders,
                    a.doneOrders,
                    a.returnOrders,
                    a.totalCod
            ));
        }

        rows.sort(buildComparator(sort));

        AdsReportResponse res = buildResponse(from, to, sort, rows, (int) pulled.get());
        log.info("[AdsReportService] Report built: {} orders -> {} posts", pulled.get(), rows.size());

        // Cache the result
        String key = from + "_" + to + "_" + (sort == null ? "" : sort);
        cache.put(key, res);

        return res;
    }

    private void processOrders(List<PancakeOrder> orders, Map<String, Agg> aggs, AtomicLong pulled) {
        for (PancakeOrder order : orders) {
            try {
                pulled.incrementAndGet();

                String postId = safe(order.getPostId());
                if (postId == null) continue;

                String orderId = safe(order.getId());
                if (orderId == null) continue;

                Agg a = aggs.computeIfAbsent(postId, k -> {
                    Agg agg = new Agg();
                    agg.postId = postId;
                    agg.uniqueOrderIds = ConcurrentHashMap.newKeySet();
                    agg.doneOrderIds = ConcurrentHashMap.newKeySet();
                    agg.returnOrderIds = ConcurrentHashMap.newKeySet();
                    return agg;
                });

                String adsName = safe(order.marketerNameSafe());
                if (adsName != null && a.adsName == null) {
                    a.adsName = adsName;
                }

                if (a.uniqueOrderIds.add(orderId)) {
                    a.totalOrders++;

                    Integer status = order.getStatus();
                    if (status != null) {
                        if (status == 3) {
                            if (a.doneOrderIds.add(orderId)) {
                                a.doneOrders++;
                            }
                            long cod = order.getCod() != null ? order.getCod()
                                    : (order.getMoneyToCollect() != null ? order.getMoneyToCollect() : 0L);
                            a.totalCod += cod;
                        } else if (status == 5) {
                            if (a.returnOrderIds.add(orderId)) {
                                a.returnOrders++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[AdsReportService] Error processing order: {}", e.getMessage());
            }
        }
    }

    private AdsReportResponse buildResponse(LocalDate from, LocalDate to, String sort, List<AdsReportRow> rows, int orderCount) {
        return new AdsReportResponse(
                new AdsReportResponse.Range(from.toString(), to.toString()),
                new AdsReportResponse.Meta(orderCount, rows.size()),
                rows
        );
    }

    private Comparator<AdsReportRow> buildComparator(String sort) {
        if (sort == null) sort = "total_desc";
        return switch (sort) {
            case "cod_asc" -> Comparator.comparingLong(AdsReportRow::getTotalCod);
            case "cod_desc" -> Comparator.comparingLong(AdsReportRow::getTotalCod).reversed();
            case "total_asc" -> Comparator.comparingLong(AdsReportRow::getTotalOrders);
            case "done_desc" -> Comparator.comparingLong(AdsReportRow::getDoneOrders).reversed();
            case "done_asc" -> Comparator.comparingLong(AdsReportRow::getDoneOrders);
            case "cr_asc" -> Comparator.comparingDouble(AdsReportRow::getConversionRate);
            case "cr_desc" -> Comparator.comparingDouble(AdsReportRow::getConversionRate).reversed();
            default -> Comparator.comparingLong(AdsReportRow::getTotalOrders).reversed();
        };
    }

    private static String safe(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static class Agg {
        String postId;
        String adsName;
        Set<String> uniqueOrderIds = ConcurrentHashMap.newKeySet();
        Set<String> doneOrderIds = ConcurrentHashMap.newKeySet();
        Set<String> returnOrderIds = ConcurrentHashMap.newKeySet();
        long totalOrders;
        long doneOrders;
        long returnOrders;
        long totalCod;
    }
}
