package mera.mera_v2.pos.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.sync.client.OrderApiClient;
import mera.mera_v2.pos.sync.dto.CustomerDTO;
import mera.mera_v2.pos.sync.dto.OrderApiDto;
import mera.mera_v2.pos.sync.dto.OrderItemApiDto;
import mera.mera_v2.pos.sync.dto.OrderListResponseDto;
import mera.mera_v2.pos.sync.dto.ShippingAddressDTO;
import mera.mera_v2.pos.sync.dto.StatusHistoryDto;
import mera.mera_v2.pos.sync.dto.VariationInfoApiDto;
import mera.mera_v2.entity.Customer;
import mera.mera_v2.entity.Order;
import mera.mera_v2.entity.OrderItem;
import mera.mera_v2.entity.OrderStatusHistory;
import mera.mera_v2.repository.CustomerRepository;
import mera.mera_v2.repository.OrderItemRepository;
import mera.mera_v2.repository.OrderRepository;
import mera.mera_v2.repository.OrderStatusHistoryRepository;
import mera.mera_v2.repository.ProductVariationRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncService {

  private final OrderApiClient orderApiClient;
  private final CustomerRepository customerRepository;
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;
  private final ProductVariationRepository productVariationRepository;

  @Lazy
  @org.springframework.beans.factory.annotation.Autowired
  private OrderSyncService self;

  // ============================================================
  // Public entry points
  // ============================================================

  public OrderSyncResult syncOrders() {
    return syncOrdersWithVariations(0, 0, 1, 200, "inserted_at", null);
  }

  public OrderSyncResult syncOrders(
      long startTimestamp,
      long endTimestamp,
      int pageNumber,
      int pageSize,
      String updateStatus,
      String status
  ) {
    return syncOrdersWithVariations(startTimestamp, endTimestamp, pageNumber, pageSize, updateStatus, status);
  }

  public OrderSyncResult syncOrdersWithVariations(
      long startTimestamp,
      long endTimestamp,
      int pageNumber,
      int pageSize,
      String updateStatus,
      String status
  ) {
    log.info("Starting order sync with pagination: startTs={}, endTs={}, page={}, size={}, updateStatus={}, status={}",
        startTimestamp, endTimestamp, pageNumber, pageSize, updateStatus, status);

    int totalEntries = 0;
    int insertedCustomers = 0;
    int updatedCustomers = 0;
    int insertedOrders = 0;
    int updatedOrders = 0;
    int insertedOrderItems = 0;
    int updatedOrderItems = 0;
    int insertedStatusHistories = 0;
    int updatedStatusHistories = 0;
    int skippedOrders = 0;
    var errorMessages = new ArrayList<String>();
    var skippedOrderIds = new ArrayList<String>();

    int currentPage = pageNumber;
    int totalPages = 1;
    int totalSynced = 0;

    do {
      log.info("Fetching page {} of {}...", currentPage, totalPages);

      OrderListResponseDto resp = null;
      List<OrderApiDto> orders = List.of();
      int retries = 0;
      while (retries < 3) {
        try {
          resp = orderApiClient.fetchOrdersPage(
              startTimestamp, endTimestamp, currentPage, pageSize, updateStatus, status
          );
          break;
        } catch (Exception e) {
          retries++;
          log.warn("Page {} fetch attempt {} failed: {}", currentPage, retries, e.getMessage());
          if (retries >= 3) {
            log.error("Page {} failed after 3 retries, skipping page", currentPage);
            errorMessages.add("Page " + currentPage + " failed after 3 retries: " + e.getMessage());
            skippedOrders += orders.isEmpty() ? 0 : orders.size();
            currentPage++;
            continue;
          }
          try { Thread.sleep(2000L * retries); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
      }
      if (resp == null) { currentPage++; continue; }

      orders = resp.getData() != null ? resp.getData() : List.of();

      if (resp.getTotalPages() != null) totalPages = resp.getTotalPages();
      if (resp.getTotalEntries() != null) totalEntries = resp.getTotalEntries();

      log.info("Page {}: fetched {} orders (totalEntries={}, totalPages={})",
          currentPage, orders.size(), resp.getTotalEntries(), resp.getTotalPages());

      // Collect variation IDs from this page
      Set<String> pageVariationIds = new HashSet<>();
      for (OrderApiDto order : orders) {
        List<OrderItemApiDto> items = order.getItems();
        if (items != null) {
          for (OrderItemApiDto item : items) {
            String vid = item.getVariationId();
            if (vid != null && !vid.isBlank()) pageVariationIds.add(vid);
          }
        }
      }

      // Pre-load existing variations for this page
      Set<String> existingVarIds = new HashSet<>();
      if (!pageVariationIds.isEmpty()) {
        productVariationRepository.findAllById(pageVariationIds)
            .forEach(pv -> existingVarIds.add(pv.getId()));
      }

      BatchSyncResult batchResult = self.syncPageBatch(orders, existingVarIds);

      insertedCustomers += batchResult.insertedCustomers;
      updatedCustomers += batchResult.updatedCustomers;
      insertedOrders += batchResult.insertedOrders;
      updatedOrders += batchResult.updatedOrders;
      insertedOrderItems += batchResult.insertedOrderItems;
      updatedOrderItems += batchResult.updatedOrderItems;
      insertedStatusHistories += batchResult.insertedStatusHistories;
      updatedStatusHistories += batchResult.updatedStatusHistories;
      skippedOrders += batchResult.skippedOrders;
      errorMessages.addAll(batchResult.errorMessages);
      skippedOrderIds.addAll(batchResult.skippedOrderIds);
      totalSynced += orders.size();

      currentPage++;

    } while (currentPage <= totalPages);

    OrderSyncResult result = OrderSyncResult.builder()
        .totalOrdersFromApi(totalEntries)
        .insertedCustomers(insertedCustomers)
        .updatedCustomers(updatedCustomers)
        .insertedOrders(insertedOrders)
        .updatedOrders(updatedOrders)
        .insertedOrderItems(insertedOrderItems)
        .updatedOrderItems(updatedOrderItems)
        .insertedStatusHistories(insertedStatusHistories)
        .updatedStatusHistories(updatedStatusHistories)
        .skippedOrders(skippedOrders)
        .errorMessages(errorMessages)
        .build();

    log.info("Sync completed. totalEntries={}, synced={}, customers={}, orders={}, items={}, skipped={}",
        result.getTotalOrdersFromApi(), totalSynced,
        result.getCustomerChanges(), result.getOrderChanges(),
        result.getOrderItemChanges(), result.getSkippedOrders());

    saveSkippedOrders(skippedOrderIds, errorMessages);

    return result;
  }

  // ============================================================
  // Batch sync â€” one transaction per page
  // ============================================================

  @Transactional
  public BatchSyncResult syncPageBatch(List<OrderApiDto> orders) {
    return syncPageBatch(orders, null);
  }

  @Transactional
  public BatchSyncResult syncPageBatch(List<OrderApiDto> orders, Set<String> existingVariationIds) {
    if (orders == null || orders.isEmpty()) {
      return new BatchSyncResult(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    // --- Step 1: Collect IDs ---
    List<Long> orderIds = new ArrayList<>();
    List<Long> itemIds = new ArrayList<>();
    List<String> customerIds = new ArrayList<>();

    for (OrderApiDto dto : orders) {
      Long orderId = parseOrderId(dto.getId());
      if (orderId != null) {
        orderIds.add(orderId);
      }

      if (dto.getCustomer() != null && dto.getCustomer().getId() != null) {
        customerIds.add(dto.getCustomer().getId());
      }

      if (dto.getItems() != null) {
        for (OrderItemApiDto item : dto.getItems()) {
          Long itemId = parseItemId(item.getId());
          if (itemId != null) {
            itemIds.add(itemId);
          }
        }
      }
    }

    // --- Step 2: Bulk fetch existing ---
    Map<Long, Order> existingOrders = new HashMap<>();
    for (Order o : orderRepository.findAllByIdIn(orderIds)) {
      existingOrders.put(o.getId(), o);
    }

    Map<String, Customer> existingCustomers = new HashMap<>();
    for (Customer c : customerRepository.findAllByIdIn(customerIds)) {
      existingCustomers.put(c.getId(), c);
    }

    Map<Long, OrderItem> existingItems = new HashMap<>();
    for (OrderItem i : orderItemRepository.findAllByIdIn(itemIds)) {
      existingItems.put(i.getId(), i);
    }

    // Fetch existing status histories to avoid duplicate insert
    Set<String> existingStatusHistoryKeys = new HashSet<>();
    for (OrderStatusHistory h : orderStatusHistoryRepository.findAllByOrder_IdIn(orderIds)) {
      if (h.getOrder() != null && h.getOrder().getId() != null
          && h.getNewStatus() != null && h.getUpdatedAt() != null) {
        String key = h.getOrder().getId() + "_" + h.getNewStatus() + "_" + h.getUpdatedAt();
        existingStatusHistoryKeys.add(key);
      }
    }

    // --- Step 3: Build entity lists (map from DTO, no DB queries) ---
    Map<Long, Order> ordersToSaveMap = new LinkedHashMap<>();
    Map<Long, OrderItem> itemsToSaveMap = new LinkedHashMap<>();
    List<Customer> customersToSave = new ArrayList<>();
    List<OrderStatusHistory> statusHistoriesToSave = new ArrayList<>();
    Set<String> seenCustomerIds = new HashSet<>();

    int insertedCustomers = 0, updatedCustomers = 0;
    int insertedOrders = 0, updatedOrders = 0;
    int insertedOrderItems = 0, updatedOrderItems = 0;
    int insertedStatusHistories = 0, updatedStatusHistories = 0;
    int skippedOrders = 0;

    var errorMessages = new ArrayList<String>();
    var skippedOrderIds = new ArrayList<String>();

    for (OrderApiDto dto : orders) {
      try {
        // --- Customer ---
        if (dto.getCustomer() != null
            && dto.getCustomer().getId() != null
            && !seenCustomerIds.contains(dto.getCustomer().getId())) {

          CustomerSyncResult custRes = mapCustomer(dto.getCustomer(), dto.getShopId(), existingCustomers);
          if (custRes.entity != null) {
            customersToSave.add(custRes.entity);
            if (custRes.isNew) {
              insertedCustomers++;
            } else {
              updatedCustomers++;
            }
          }
          seenCustomerIds.add(dto.getCustomer().getId());
        }

        // --- Order ---
        Long orderId = parseOrderId(dto.getId());
        if (orderId == null) {
          throw new IllegalArgumentException("Cannot parse order id: " + dto.getId());
        }

        Order order = existingOrders.get(orderId);
        boolean isNewOrder = (order == null);
        if (isNewOrder) {
          order = new Order();
          order.setId(orderId);
        }

        mapOrder(order, dto, isNewOrder);
        ordersToSaveMap.put(orderId, order);

        if (isNewOrder) {
          insertedOrders++;
        } else {
          updatedOrders++;
        }

        // --- Order Items ---
        if (dto.getItems() != null) {
          for (OrderItemApiDto itemDto : dto.getItems()) {
            Long itemId = parseItemId(itemDto.getId());
            if (itemId == null) {
              log.warn("Cannot parse item id: {}", itemDto.getId());
              continue;
            }

            OrderItem item = existingItems.get(itemId);
            boolean isNewItem = (item == null);
            if (isNewItem) {
              item = new OrderItem();
              item.setId(itemId);
            }

            mapOrderItem(item, itemDto, orderId, existingVariationIds);
            itemsToSaveMap.put(itemId, item);

            if (isNewItem) {
              insertedOrderItems++;
            } else {
              updatedOrderItems++;
            }
          }
        }

        // --- Status Histories ---
        List<StatusHistoryDto> histories = dto.getStatusHistory();
        if (histories != null && !histories.isEmpty()) {
          Set<String> seenHistoryKeys = new HashSet<>();

          for (StatusHistoryDto histDto : histories) {
            if (histDto.getNewStatus() == null) {
              continue;
            }

            LocalDateTime updatedAt = parseDateTime(histDto.getUpdatedAt(), "statusHistory.updatedAt");
            if (updatedAt == null) {
              continue;
            }

            String histKey = orderId + "_" + histDto.getNewStatus() + "_" + updatedAt;

            // duplicate inside current payload
            if (!seenHistoryKeys.add(histKey)) {
              continue;
            }

            // duplicate already existing in database
            if (existingStatusHistoryKeys.contains(histKey)) {
              continue;
            }

            OrderStatusHistory hist = new OrderStatusHistory();
            hist.setOrder(order);
            hist.setOldStatus(histDto.getOldStatus());
            hist.setNewStatus(histDto.getNewStatus());
            hist.setEditorId(histDto.getEditorId());
            hist.setEditorName(histDto.getEditorName());
            hist.setEditorFb(histDto.getEditorFb());
            hist.setUpdatedAt(updatedAt);

            statusHistoriesToSave.add(hist);
            existingStatusHistoryKeys.add(histKey);
            insertedStatusHistories++;
          }
        }

      } catch (Exception e) {
        log.error("Failed to sync order id={}: {}", dto.getId(), e.getMessage(), e);
        errorMessages.add("Order " + dto.getId() + ": " + e.getMessage());
        skippedOrderIds.add(dto.getId());
        skippedOrders++;
      }
    }

    // --- Step 4: Batch save with deadlock retry ---
    List<Order> ordersToSave = new ArrayList<>(ordersToSaveMap.values());
    List<OrderItem> itemsToSave = new ArrayList<>(itemsToSaveMap.values());

    saveWithDeadlockRetry(() -> {
      if (!customersToSave.isEmpty()) {
        customerRepository.saveAll(customersToSave);
        log.debug("Saved {} customers", customersToSave.size());
      }
      if (!ordersToSave.isEmpty()) {
        orderRepository.saveAll(ordersToSave);
        log.debug("Saved {} orders", ordersToSave.size());
      }
      if (!itemsToSave.isEmpty()) {
        orderItemRepository.saveAll(itemsToSave);
        log.debug("Saved {} order items", itemsToSave.size());
      }
      if (!statusHistoriesToSave.isEmpty()) {
        orderStatusHistoryRepository.saveAll(statusHistoriesToSave);
        log.debug("Saved {} status histories", statusHistoriesToSave.size());
      }
    });

    return new BatchSyncResult(
        insertedCustomers, updatedCustomers,
        insertedOrders, updatedOrders,
        insertedOrderItems, updatedOrderItems,
        insertedStatusHistories, updatedStatusHistories,
        skippedOrders, errorMessages, skippedOrderIds
    );
  }
  // ============================================================
  // Mapping helpers (no DB access)
  // ============================================================

  private record CustomerSyncResult(Customer entity, boolean isNew) {}

  private CustomerSyncResult mapCustomer(CustomerDTO dto, Long shopId, Map<String, Customer> existing) {
    if (dto == null || dto.getId() == null) {
      return new CustomerSyncResult(null, false);
    }
    Customer customer = existing.get(dto.getId());
    boolean isNew = (customer == null);
    if (isNew) {
      customer = new Customer();
      customer.setId(dto.getId());
    }
    customer.setShopId(shopId);
    customer.setName(dto.getName() != null ? dto.getName() : "Unknown");
    customer.setGender(dto.getGender());
    customer.setFbId(dto.getFbId());
    customer.setReferralCode(dto.getReferralCode());
    customer.setInsertedAt(parseDateTime(dto.getInsertedAt(), "customer.insertedAt"));
    customer.setUpdatedAt(parseDateTime(dto.getUpdatedAt(), "customer.updatedAt"));
    return new CustomerSyncResult(customer, isNew);
  }

  private void mapOrder(Order order, OrderApiDto dto, boolean isNew) {
    order.setShopId(dto.getShopId());
    order.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
    order.setStatusName(dto.getStatusName());

    if (dto.getCustomer() != null && dto.getCustomer().getId() != null) {
      order.setCustomerId(dto.getCustomer().getId());
    }
    if (dto.getCreator() != null && dto.getCreator().getId() != null) {
      order.setCreatorId(dto.getCreator().getId());
    }

    if (dto.getTotalPrice() != null) order.setTotalPrice(dto.getTotalPrice());
    if (dto.getTotalPriceAfterSubDiscount() != null) {
      order.setTotalPriceAfterSubDiscount(BigDecimal.valueOf(dto.getTotalPriceAfterSubDiscount()));
    }
    if (dto.getCod() != null) order.setCod(BigDecimal.valueOf(dto.getCod()));
    if (dto.getPrepaid() != null) order.setPrepaid(BigDecimal.valueOf(dto.getPrepaid()));
    if (dto.getShippingFee() != null) order.setShippingFee(BigDecimal.valueOf(dto.getShippingFee()));

    order.setBillFullName(dto.getBillFullName());
    order.setBillPhoneNumber(dto.getBillPhoneNumber());
    order.setBillEmail(dto.getBillEmail());
    order.setNote(dto.getNote());
    order.setOrderSources(dto.getOrderSources());
    order.setOrderSourcesName(dto.getOrderSourcesName());
    if (dto.getSystemId() != null) order.setOrderCode(String.valueOf(dto.getSystemId()));

    ShippingAddressDTO addr = dto.getShippingAddress();
    if (addr != null) {
      order.setShippingFullName(addr.getFullName());
      order.setShippingPhoneNumber(addr.getPhoneNumber());
      order.setShippingAddress(addr.getAddress());
      order.setShippingFullAddress(addr.getFullAddress());
      order.setShippingProvinceName(addr.getProvinceName());
      order.setShippingDistrictName(addr.getDistrictName());
      order.setShippingCommuneName(addr.getCommuneName());
    }

    if (isNew) order.setInsertedAt(parseDateTime(dto.getInsertedAt(), "order.insertedAt"));
    order.setUpdatedAt(parseDateTime(dto.getUpdatedAt(), "order.updatedAt"));
  }

  private void mapOrderItem(OrderItem item, OrderItemApiDto dto, Long orderId, Set<String> existingVariationIds) {
    item.setOrderId(orderId);
    item.setProductId(dto.getProductId());
    String variationId = dto.getVariationId();
    // Only set variationId if it exists in product_variations table
    if (variationId != null && !variationId.isBlank()
        && existingVariationIds != null && existingVariationIds.contains(variationId)) {
      item.setVariationId(variationId);
    } else {
      item.setVariationId(null);
    }
    item.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
    if (dto.getDiscountEachProduct() != null) item.setDiscountEachProduct(dto.getDiscountEachProduct());
    if (dto.getTotalDiscount() != null) item.setTotalDiscount(dto.getTotalDiscount().doubleValue());

    VariationInfoApiDto varInfo = dto.getVariationInfo();
    if (varInfo != null) {
      item.setVariationName(varInfo.getName());
      item.setRetailPrice(varInfo.getRetailPrice() != null ? varInfo.getRetailPrice().doubleValue() : null);
      item.setWeight(varInfo.getWeight());
      if (varInfo.getName() != null) item.setProductName(varInfo.getName());
    }
  }

  // ============================================================
  // ID parsing helpers
  // ============================================================

  Long parseOrderId(String id) {
    if (id == null || id.isBlank()) return null;
    try {
      return Long.parseLong(id.trim());
    } catch (NumberFormatException e) {
      String numeric = id.replaceAll("[^0-9]", "");
      if (!numeric.isEmpty()) return Long.parseLong(numeric);
      return null;
    }
  }

  Long parseItemId(String id) {
    return parseOrderId(id);
  }

  // ============================================================
  // Datetime parsing helpers
  // ============================================================

  private static final List<DateTimeFormatter> DATETIME_FORMATTERS = List.of(
      DateTimeFormatter.ISO_LOCAL_DATE_TIME,
      DateTimeFormatter.ISO_DATE_TIME,
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
  );

  private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
      DateTimeFormatter.ISO_LOCAL_DATE,
      DateTimeFormatter.ofPattern("yyyy-MM-dd")
  );

  LocalDateTime parseDateTime(String value, String fieldName) {
    if (value == null || value.isBlank()) return LocalDateTime.now();
    for (DateTimeFormatter fmt : DATETIME_FORMATTERS) {
      try {
        return LocalDateTime.parse(value, fmt);
      } catch (DateTimeParseException ignored) {}
    }
    try {
      long millis = Long.parseLong(value.trim());
      return LocalDateTime.ofInstant(
          java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
    } catch (NumberFormatException ignored) {}
    log.warn("Cannot parse datetime '{}' for field {}, using current time", value, fieldName);
    return LocalDateTime.now();
  }

  LocalDate parseDate(String value, String fieldName) {
    if (value == null || value.isBlank()) return null;
    for (DateTimeFormatter fmt : DATE_FORMATTERS) {
      try {
        return LocalDate.parse(value, fmt);
      } catch (DateTimeParseException ignored) {}
    }
    log.warn("Cannot parse date '{}' for field {}", value, fieldName);
    return null;
  }

  // ============================================================
  // Skipped orders file writer
  // ============================================================

  private static final DateTimeFormatter FILE_DATE_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private void saveSkippedOrders(List<String> skippedIds, List<String> errorMessages) {
    if (skippedIds == null || skippedIds.isEmpty()) {
      log.info("No skipped orders â€” skipping file write.");
      return;
    }
    try {
      Path logDir = Paths.get("logs");
      if (!Files.exists(logDir)) Files.createDirectories(logDir);

      String timestamp = LocalDateTime.now().format(FILE_DATE_FMT);
      Path file = logDir.resolve("skipped_orders_" + timestamp + ".json");

      var lines = new StringBuilder();
      lines.append("[\n");
      for (int i = 0; i < skippedIds.size(); i++) {
        String id = skippedIds.get(i);
        String reason = (i < errorMessages.size()) ? errorMessages.get(i) : "unknown";
        lines.append("  {\"orderId\": \"").append(id).append("\", \"reason\": \"").append(reason).append("\"}");
        if (i < skippedIds.size() - 1) lines.append(",");
        lines.append("\n");
      }
      lines.append("]");
      Files.writeString(file, lines.toString());
      log.info("Skipped orders saved to: {}", file.toAbsolutePath());
    } catch (IOException e) {
      log.error("Failed to write skipped orders file: {}", e.getMessage());
    }
  }

  // ============================================================
  // Deadlock retry helper
  // ============================================================

  @FunctionalInterface
  private interface RetryableOperation {
    void execute() throws Exception;
  }

  private void saveWithDeadlockRetry(RetryableOperation op) {
    int retries = 0;
    while (retries < 3) {
      try {
        op.execute();
        return;
      } catch (RuntimeException re) {
        if (re.getMessage() != null && re.getMessage().contains("Deadlock")) {
          retries++;
          log.warn("Deadlock detected on attempt {}, retrying in {}ms...", retries, 200 * retries);
          if (retries >= 3) {
            log.error("Deadlock persisted after 3 retries", re);
            throw re;
          }
          try { Thread.sleep(200L * retries); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        } else {
          throw re;
        }
      } catch (Exception e) {
        if (e.getMessage() != null && e.getMessage().contains("Deadlock")) {
          retries++;
          log.warn("Deadlock detected on attempt {}, retrying in {}ms...", retries, 200 * retries);
          if (retries >= 3) {
            log.error("Deadlock persisted after 3 retries", e);
            throw new RuntimeException("Deadlock persisted after 3 retries", e);
          }
          try { Thread.sleep(200L * retries); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        } else {
          throw new RuntimeException(e);
        }
      }
    }
  }

  // ============================================================
  // Result classes
  // ============================================================

  private record BatchSyncResult(
      int insertedCustomers, int updatedCustomers,
      int insertedOrders, int updatedOrders,
      int insertedOrderItems, int updatedOrderItems,
      int insertedStatusHistories, int updatedStatusHistories,
      int skippedOrders,
      List<String> errorMessages, List<String> skippedOrderIds
  ) {}
}
