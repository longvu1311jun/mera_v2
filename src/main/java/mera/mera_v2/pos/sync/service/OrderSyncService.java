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
import mera.mera_v2.repository.WarehouseRepository;
import mera.mera_v2.repository.PosUserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
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
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncService {

  private static final int ORDER_UPSERT_CHUNK_SIZE = 50;

  private final OrderApiClient orderApiClient;
  private final CustomerRepository customerRepository;
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;
  private final ProductVariationRepository productVariationRepository;
  private final WarehouseRepository warehouseRepository;
  private final PosUserRepository posUserRepository;
  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

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

    // Pipeline: pre-fetch next page while processing current page
    CompletableFuture<OrderListResponseDto> prefetchFuture = null;

    do {
      // Get current page - either from pre-fetch or fetch now
      OrderListResponseDto resp;
      if (prefetchFuture != null) {
        resp = prefetchFuture.join();
        prefetchFuture = null;
      } else {
        log.info("Fetching page {} ...", currentPage);
        resp = fetchPageWithRetry(startTimestamp, endTimestamp, currentPage, pageSize, updateStatus, status);
      }

      if (resp == null) {
        errorMessages.add("Page " + currentPage + " failed after 3 retries");
        currentPage++;
        continue;
      }

      List<OrderApiDto> orders = resp.getData() != null ? resp.getData() : List.of();
      if (resp.getTotalPages() != null) totalPages = resp.getTotalPages();
      if (resp.getTotalEntries() != null) totalEntries = resp.getTotalEntries();

      log.info("Page {}/{}: fetched {} orders (totalEntries={})",
          currentPage, totalPages, orders.size(), resp.getTotalEntries());

      // Start pre-fetching next page in background while processing current page
      if (currentPage + 1 <= totalPages) {
        final int nextPage = currentPage + 1;
        prefetchFuture = CompletableFuture.supplyAsync(() ->
            fetchPageWithRetry(startTimestamp, endTimestamp, nextPage, pageSize, updateStatus, status)
        );
      }

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

      // Collect warehouse IDs and pre-load
      Set<String> pageWarehouseIds = new HashSet<>();
      for (OrderApiDto order : orders) {
        if (order.getWarehouseId() != null && !order.getWarehouseId().isBlank()) {
          pageWarehouseIds.add(order.getWarehouseId());
        }
      }
      Set<String> existingWarehouseIds = new HashSet<>();
      if (!pageWarehouseIds.isEmpty()) {
        warehouseRepository.findAllById(pageWarehouseIds)
            .forEach(w -> existingWarehouseIds.add(w.getId()));
      }

      // Collect creator IDs and pre-load pos_users
      Set<String> pageCreatorIds = new HashSet<>();
      for (OrderApiDto order : orders) {
        if (order.getCreator() != null && order.getCreator().getId() != null
            && !order.getCreator().getId().isBlank()) {
          pageCreatorIds.add(order.getCreator().getId());
        }
      }
      Set<String> existingPosUserIds = new HashSet<>();
      if (!pageCreatorIds.isEmpty()) {
        posUserRepository.findAllById(pageCreatorIds)
            .forEach(u -> existingPosUserIds.add(u.getId()));
      }

      // Retry batch sync với deadlock handling
      BatchSyncResult batchResult = retryBatchSync(orders, existingVarIds, existingWarehouseIds, existingPosUserIds);

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
    return syncPageBatch(orders, null, null);
  }

  @Transactional
  public BatchSyncResult syncPageBatch(List<OrderApiDto> orders, Set<String> existingVariationIds) {
    return syncPageBatch(orders, existingVariationIds, null);
  }

  @Transactional
  public BatchSyncResult syncPageBatch(List<OrderApiDto> orders, Set<String> existingVariationIds, Set<String> existingWarehouseIds) {
    return syncPageBatch(orders, existingVariationIds, existingWarehouseIds, null);
  }

  @Transactional
  public BatchSyncResult syncPageBatch(List<OrderApiDto> orders, Set<String> existingVariationIds, Set<String> existingWarehouseIds, Set<String> existingPosUserIds) {
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

      String customerId = resolveCustomerId(dto);
      if (customerId != null) {
        customerIds.add(customerId);
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

    // --- Step 2: Bulk fetch existing IDs (lightweight, no full entity load) ---
    Set<Long> existingOrderIds = orderIds.isEmpty()
        ? new HashSet<>()
        : new HashSet<>(orderRepository.findExistingIds(orderIds));

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
    Set<String> validCustomerIds = new HashSet<>(existingCustomers.keySet());

    int insertedCustomers = 0, updatedCustomers = 0;
    int insertedOrders = 0, updatedOrders = 0;
    int insertedOrderItems = 0, updatedOrderItems = 0;
    int insertedStatusHistories = 0, updatedStatusHistories = 0;
    int skippedOrders = 0;

    var errorMessages = new ArrayList<String>();
    var skippedOrderIds = new ArrayList<String>();

    for (OrderApiDto dto : orders) {
      try {
        // --- Customer (nested object hoặc customer_id phẳng) ---
        String customerId = resolveCustomerId(dto);
        if (customerId != null && !seenCustomerIds.contains(customerId)) {
          CustomerSyncResult custRes;
          if (dto.getCustomer() != null && dto.getCustomer().getId() != null) {
            custRes = mapCustomer(dto.getCustomer(), dto.getShopId(), existingCustomers);
          } else {
            custRes = mapStubCustomer(customerId, dto, existingCustomers);
          }
          if (custRes.entity != null) {
            customersToSave.add(custRes.entity);
            validCustomerIds.add(custRes.entity.getId());
            if (custRes.isNew) {
              insertedCustomers++;
            } else {
              updatedCustomers++;
            }
          } else {
            validCustomerIds.add(customerId);
          }
          seenCustomerIds.add(customerId);
        }

        // --- Order ---
        Long orderId = parseOrderId(dto.getId());
        if (orderId == null) {
          throw new IllegalArgumentException("Cannot parse order id: " + dto.getId());
        }

        boolean isNewOrder = !existingOrderIds.contains(orderId);
        Order order = new Order();
        order.setId(orderId);

        mapOrder(order, dto, existingWarehouseIds, validCustomerIds, existingPosUserIds);
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

    if (!customersToSave.isEmpty()) {
      saveWithDeadlockRetry(() -> self.persistCustomersInNewTx(customersToSave));
      log.debug("Saved {} customers", customersToSave.size());
    }

    saveWithDeadlockRetry(() -> {
      if (!ordersToSave.isEmpty()) {
        batchUpsertOrders(ordersToSave);
        log.debug("Batch upserted {} orders", ordersToSave.size());
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
  // Batch upsert orders via JdbcTemplate
  // ============================================================

  private static final String UPSERT_ORDER_SQL = """
      INSERT INTO orders (id, order_code, shop_id, page_id, customer_id, conversation_id, post_id,
          ad_id, creator_id, assigning_seller_id, assigning_care_id, marketer_id, last_editor_id,
          warehouse_id, status, sub_status, status_name, bill_full_name, bill_phone_number, bill_email,
          shipping_full_name, shipping_phone_number, shipping_address, shipping_full_address,
          shipping_province_id, shipping_province_name, shipping_district_id, shipping_district_name,
          shipping_commune_id, shipping_commune_name, shipping_country_code, shipping_post_code,
          order_sources, order_sources_name, ads_source, p_utm_source, p_utm_medium, p_utm_campaign,
          p_utm_content, p_utm_term, p_utm_id, is_livestream, is_live_shopping, total_price,
          total_price_after_sub_discount, total_discount, shipping_fee, surcharge, tax, cod,
          money_to_collect, prepaid, cash, transfer_money, charged_by_momo, charged_by_card,
          charged_by_qrpay, exchange_payment, exchange_value, partner_fee, return_fee, fee_marketplace,
          buyer_total_amount, levera_point, is_free_shipping, is_exchange_order, is_calculation_tax,
          is_smc, customer_pay_fee, received_at_shop, partner, tracking_link, time_send_partner,
          estimate_delivery_date, returned_reason, returned_reason_name, note, note_print, link,
          time_assign_seller, time_assign_care, inserted_at, updated_at, lt_type, tick, created_at,
          customer_address, customer_name, customer_phone, last_editor_name, order_id,
          partner_delivery_name, partner_tracking_id, account, account_name, order_link, raw_data)
      VALUES (:id, :order_code, :shop_id, :page_id, :customer_id, :conversation_id, :post_id,
          :ad_id, :creator_id, :assigning_seller_id, :assigning_care_id, :marketer_id, :last_editor_id,
          :warehouse_id, :status, :sub_status, :status_name, :bill_full_name, :bill_phone_number, :bill_email,
          :shipping_full_name, :shipping_phone_number, :shipping_address, :shipping_full_address,
          :shipping_province_id, :shipping_province_name, :shipping_district_id, :shipping_district_name,
          :shipping_commune_id, :shipping_commune_name, :shipping_country_code, :shipping_post_code,
          :order_sources, :order_sources_name, :ads_source, :p_utm_source, :p_utm_medium, :p_utm_campaign,
          :p_utm_content, :p_utm_term, :p_utm_id, :is_livestream, :is_live_shopping, :total_price,
          :total_price_after_sub_discount, :total_discount, :shipping_fee, :surcharge, :tax, :cod,
          :money_to_collect, :prepaid, :cash, :transfer_money, :charged_by_momo, :charged_by_card,
          :charged_by_qrpay, :exchange_payment, :exchange_value, :partner_fee, :return_fee, :fee_marketplace,
          :buyer_total_amount, :levera_point, :is_free_shipping, :is_exchange_order, :is_calculation_tax,
          :is_smc, :customer_pay_fee, :received_at_shop, :partner, :tracking_link, :time_send_partner,
          :estimate_delivery_date, :returned_reason, :returned_reason_name, :note, :note_print, :link,
          :time_assign_seller, :time_assign_care, :inserted_at, :updated_at, :lt_type, :tick, :created_at,
          :customer_address, :customer_name, :customer_phone, :last_editor_name, :order_id,
          :partner_delivery_name, :partner_tracking_id, :account, :account_name, :order_link, :raw_data)
      ON DUPLICATE KEY UPDATE
          order_code = VALUES(order_code), shop_id = VALUES(shop_id), page_id = VALUES(page_id),
          customer_id = VALUES(customer_id), conversation_id = VALUES(conversation_id),
          post_id = VALUES(post_id), ad_id = VALUES(ad_id), creator_id = VALUES(creator_id),
          assigning_seller_id = VALUES(assigning_seller_id), assigning_care_id = VALUES(assigning_care_id),
          marketer_id = VALUES(marketer_id), last_editor_id = VALUES(last_editor_id),
          warehouse_id = VALUES(warehouse_id), status = VALUES(status), sub_status = VALUES(sub_status),
          status_name = VALUES(status_name), bill_full_name = VALUES(bill_full_name),
          bill_phone_number = VALUES(bill_phone_number), bill_email = VALUES(bill_email),
          shipping_full_name = VALUES(shipping_full_name), shipping_phone_number = VALUES(shipping_phone_number),
          shipping_address = VALUES(shipping_address), shipping_full_address = VALUES(shipping_full_address),
          shipping_province_id = VALUES(shipping_province_id), shipping_province_name = VALUES(shipping_province_name),
          shipping_district_id = VALUES(shipping_district_id), shipping_district_name = VALUES(shipping_district_name),
          shipping_commune_id = VALUES(shipping_commune_id), shipping_commune_name = VALUES(shipping_commune_name),
          shipping_country_code = VALUES(shipping_country_code), shipping_post_code = VALUES(shipping_post_code),
          order_sources = VALUES(order_sources), order_sources_name = VALUES(order_sources_name),
          ads_source = VALUES(ads_source), p_utm_source = VALUES(p_utm_source), p_utm_medium = VALUES(p_utm_medium),
          p_utm_campaign = VALUES(p_utm_campaign), p_utm_content = VALUES(p_utm_content),
          p_utm_term = VALUES(p_utm_term), p_utm_id = VALUES(p_utm_id), is_livestream = VALUES(is_livestream),
          is_live_shopping = VALUES(is_live_shopping), total_price = VALUES(total_price),
          total_price_after_sub_discount = VALUES(total_price_after_sub_discount),
          total_discount = VALUES(total_discount), shipping_fee = VALUES(shipping_fee),
          surcharge = VALUES(surcharge), tax = VALUES(tax), cod = VALUES(cod),
          money_to_collect = VALUES(money_to_collect), prepaid = VALUES(prepaid), cash = VALUES(cash),
          transfer_money = VALUES(transfer_money), charged_by_momo = VALUES(charged_by_momo),
          charged_by_card = VALUES(charged_by_card), charged_by_qrpay = VALUES(charged_by_qrpay),
          exchange_payment = VALUES(exchange_payment), exchange_value = VALUES(exchange_value),
          partner_fee = VALUES(partner_fee), return_fee = VALUES(return_fee),
          fee_marketplace = VALUES(fee_marketplace), buyer_total_amount = VALUES(buyer_total_amount),
          levera_point = VALUES(levera_point), is_free_shipping = VALUES(is_free_shipping),
          is_exchange_order = VALUES(is_exchange_order), is_calculation_tax = VALUES(is_calculation_tax),
          is_smc = VALUES(is_smc), customer_pay_fee = VALUES(customer_pay_fee),
          received_at_shop = VALUES(received_at_shop), partner = VALUES(partner),
          tracking_link = VALUES(tracking_link), time_send_partner = VALUES(time_send_partner),
          estimate_delivery_date = VALUES(estimate_delivery_date), returned_reason = VALUES(returned_reason),
          returned_reason_name = VALUES(returned_reason_name), note = VALUES(note),
          note_print = VALUES(note_print), link = VALUES(link), time_assign_seller = VALUES(time_assign_seller),
          time_assign_care = VALUES(time_assign_care), updated_at = VALUES(updated_at),
          lt_type = VALUES(lt_type), tick = VALUES(tick), created_at = VALUES(created_at),
          customer_address = VALUES(customer_address), customer_name = VALUES(customer_name),
          customer_phone = VALUES(customer_phone), last_editor_name = VALUES(last_editor_name),
          order_id = VALUES(order_id), partner_delivery_name = VALUES(partner_delivery_name),
          partner_tracking_id = VALUES(partner_tracking_id), account = VALUES(account),
          account_name = VALUES(account_name), order_link = VALUES(order_link), raw_data = VALUES(raw_data)
      """;

  private void batchUpsertOrders(List<Order> orders) {
    if (orders.isEmpty()) {
      return;
    }
    for (int i = 0; i < orders.size(); i += ORDER_UPSERT_CHUNK_SIZE) {
      List<Order> chunk = orders.subList(i, Math.min(i + ORDER_UPSERT_CHUNK_SIZE, orders.size()));
      SqlParameterSource[] batchParams = chunk.stream()
          .map(this::orderToSqlParams)
          .toArray(SqlParameterSource[]::new);
      namedParameterJdbcTemplate.batchUpdate(UPSERT_ORDER_SQL, batchParams);
    }
  }

  private static final String UPSERT_CUSTOMER_SQL = """
      INSERT INTO customers (
          id, shop_id, name, gender, date_of_birth, fb_id, referral_code, customer_referral_code,
          is_discount_by_level, reward_point, used_reward_point, current_debts, level_id, is_block,
          order_count, succeed_order_count, returned_order_count, purchased_amount, last_order_at,
          assigned_user_id, creator_id, inserted_at, updated_at, lt_real
      ) VALUES (
          :id, :shop_id, :name, :gender, :date_of_birth, :fb_id, :referral_code, :customer_referral_code,
          :is_discount_by_level, :reward_point, :used_reward_point, :current_debts, :level_id, :is_block,
          :order_count, :succeed_order_count, :returned_order_count, :purchased_amount, :last_order_at,
          :assigned_user_id, :creator_id, :inserted_at, :updated_at, :lt_real
      )
      ON DUPLICATE KEY UPDATE
          shop_id = VALUES(shop_id),
          name = VALUES(name),
          gender = VALUES(gender),
          date_of_birth = VALUES(date_of_birth),
          fb_id = VALUES(fb_id),
          referral_code = VALUES(referral_code),
          customer_referral_code = VALUES(customer_referral_code),
          is_discount_by_level = VALUES(is_discount_by_level),
          reward_point = VALUES(reward_point),
          used_reward_point = VALUES(used_reward_point),
          current_debts = VALUES(current_debts),
          level_id = VALUES(level_id),
          is_block = VALUES(is_block),
          order_count = VALUES(order_count),
          succeed_order_count = VALUES(succeed_order_count),
          returned_order_count = VALUES(returned_order_count),
          purchased_amount = VALUES(purchased_amount),
          last_order_at = VALUES(last_order_at),
          assigned_user_id = VALUES(assigned_user_id),
          creator_id = VALUES(creator_id),
          inserted_at = IFNULL(inserted_at, VALUES(inserted_at)),
          updated_at = VALUES(updated_at),
          lt_real = VALUES(lt_real)
      """;

  private static final int CUSTOMER_UPSERT_CHUNK_SIZE = 50;

  private void batchUpsertCustomers(List<Customer> customers) {
    if (customers == null || customers.isEmpty()) {
      return;
    }
    for (int i = 0; i < customers.size(); i += CUSTOMER_UPSERT_CHUNK_SIZE) {
      List<Customer> chunk = customers.subList(i, Math.min(i + CUSTOMER_UPSERT_CHUNK_SIZE, customers.size()));
      SqlParameterSource[] batchParams = chunk.stream()
          .map(this::customerToSqlParams)
          .toArray(SqlParameterSource[]::new);
      namedParameterJdbcTemplate.batchUpdate(UPSERT_CUSTOMER_SQL, batchParams);
    }
  }

  public void persistCustomersInNewTx(List<Customer> customers) {
    if (customers == null || customers.isEmpty()) {
      return;
    }
    batchUpsertCustomers(customers);
  }

  private MapSqlParameterSource customerToSqlParams(Customer c) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    p.addValue("id", c.getId());
    p.addValue("shop_id", c.getShopId());
    p.addValue("name", c.getName());
    p.addValue("gender", c.getGender());
    p.addValue("date_of_birth", c.getDateOfBirth() != null ? java.sql.Date.valueOf(c.getDateOfBirth()) : null);
    p.addValue("fb_id", c.getFbId());
    p.addValue("referral_code", c.getReferralCode());
    p.addValue("customer_referral_code", c.getCustomerReferralCode());
    p.addValue("is_discount_by_level", c.getIsDiscountByLevel());
    p.addValue("reward_point", c.getRewardPoint());
    p.addValue("used_reward_point", c.getUsedRewardPoint());
    p.addValue("current_debts", c.getCurrentDebts());
    p.addValue("level_id", c.getLevelId());
    p.addValue("is_block", c.getIsBlock());
    p.addValue("order_count", c.getOrderCount());
    p.addValue("succeed_order_count", c.getSucceedOrderCount());
    p.addValue("returned_order_count", c.getReturnedOrderCount());
    p.addValue("purchased_amount", c.getPurchasedAmount());
    p.addValue("last_order_at", c.getLastOrderAt() != null ? Timestamp.valueOf(c.getLastOrderAt()) : null);
    p.addValue("assigned_user_id", c.getAssignedUserId());
    p.addValue("creator_id", c.getCreatorId());
    p.addValue("inserted_at", toTimestamp(c.getInsertedAt()));
    p.addValue("updated_at", toTimestamp(c.getUpdatedAt()));
    p.addValue("lt_real", c.getLtReal());
    return p;
  }

  private MapSqlParameterSource orderToSqlParams(Order o) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    p.addValue("id", o.getId());
    p.addValue("order_code", o.getOrderCode());
    p.addValue("shop_id", o.getShopId());
    p.addValue("page_id", o.getPageId());
    p.addValue("customer_id", o.getCustomerId());
    p.addValue("conversation_id", o.getConversationId());
    p.addValue("post_id", o.getPostId());
    p.addValue("ad_id", o.getAdId());
    p.addValue("creator_id", o.getCreatorId());
    p.addValue("assigning_seller_id", o.getAssigningSellerId());
    p.addValue("assigning_care_id", o.getAssigningCareId());
    p.addValue("marketer_id", o.getMarketerId());
    p.addValue("last_editor_id", o.getLastEditorId());
    p.addValue("warehouse_id", o.getWarehouseId());
    p.addValue("status", o.getStatus());
    p.addValue("sub_status", o.getSubStatus());
    p.addValue("status_name", o.getStatusName());
    p.addValue("bill_full_name", o.getBillFullName());
    p.addValue("bill_phone_number", o.getBillPhoneNumber());
    p.addValue("bill_email", o.getBillEmail());
    p.addValue("shipping_full_name", o.getShippingFullName());
    p.addValue("shipping_phone_number", o.getShippingPhoneNumber());
    p.addValue("shipping_address", o.getShippingAddress());
    p.addValue("shipping_full_address", o.getShippingFullAddress());
    p.addValue("shipping_province_id", o.getShippingProvinceId());
    p.addValue("shipping_province_name", o.getShippingProvinceName());
    p.addValue("shipping_district_id", o.getShippingDistrictId());
    p.addValue("shipping_district_name", o.getShippingDistrictName());
    p.addValue("shipping_commune_id", o.getShippingCommuneId());
    p.addValue("shipping_commune_name", o.getShippingCommuneName());
    p.addValue("shipping_country_code", o.getShippingCountryCode());
    p.addValue("shipping_post_code", o.getShippingPostCode());
    p.addValue("order_sources", o.getOrderSources());
    p.addValue("order_sources_name", o.getOrderSourcesName());
    p.addValue("ads_source", o.getAdsSource());
    p.addValue("p_utm_source", o.getPUtmSource());
    p.addValue("p_utm_medium", o.getPUtmMedium());
    p.addValue("p_utm_campaign", o.getPUtmCampaign());
    p.addValue("p_utm_content", o.getPUtmContent());
    p.addValue("p_utm_term", o.getPUtmTerm());
    p.addValue("p_utm_id", o.getPUtmId());
    p.addValue("is_livestream", o.getIsLivestream());
    p.addValue("is_live_shopping", o.getIsLiveShopping());
    p.addValue("total_price", o.getTotalPrice());
    p.addValue("total_price_after_sub_discount", o.getTotalPriceAfterSubDiscount());
    p.addValue("total_discount", o.getTotalDiscount());
    p.addValue("shipping_fee", o.getShippingFee());
    p.addValue("surcharge", o.getSurcharge());
    p.addValue("tax", o.getTax());
    p.addValue("cod", o.getCod());
    p.addValue("money_to_collect", o.getMoneyToCollect());
    p.addValue("prepaid", o.getPrepaid());
    p.addValue("cash", o.getCash());
    p.addValue("transfer_money", o.getTransferMoney());
    p.addValue("charged_by_momo", o.getChargedByMomo());
    p.addValue("charged_by_card", o.getChargedByCard());
    p.addValue("charged_by_qrpay", o.getChargedByQrpay());
    p.addValue("exchange_payment", o.getExchangePayment());
    p.addValue("exchange_value", o.getExchangeValue());
    p.addValue("partner_fee", o.getPartnerFee());
    p.addValue("return_fee", o.getReturnFee());
    p.addValue("fee_marketplace", o.getFeeMarketplace());
    p.addValue("buyer_total_amount", o.getBuyerTotalAmount());
    p.addValue("levera_point", o.getLeveraPoint());
    p.addValue("is_free_shipping", o.getIsFreeShipping());
    p.addValue("is_exchange_order", o.getIsExchangeOrder());
    p.addValue("is_calculation_tax", o.getIsCalculationTax());
    p.addValue("is_smc", o.getIsSmc());
    p.addValue("customer_pay_fee", o.getCustomerPayFee());
    p.addValue("received_at_shop", o.getReceivedAtShop());
    p.addValue("partner", o.getPartner());
    p.addValue("tracking_link", o.getTrackingLink());
    p.addValue("time_send_partner", toTimestamp(o.getTimeSendPartner()));
    p.addValue("estimate_delivery_date", o.getEstimateDeliveryDate() != null
        ? java.sql.Date.valueOf(o.getEstimateDeliveryDate()) : null);
    p.addValue("returned_reason", o.getReturnedReason());
    p.addValue("returned_reason_name", o.getReturnedReasonName());
    p.addValue("note", o.getNote());
    p.addValue("note_print", o.getNotePrint());
    p.addValue("link", o.getLink());
    p.addValue("time_assign_seller", toTimestamp(o.getTimeAssignSeller()));
    p.addValue("time_assign_care", toTimestamp(o.getTimeAssignCare()));
    p.addValue("inserted_at", toTimestamp(o.getInsertedAt()));
    p.addValue("updated_at", toTimestamp(o.getUpdatedAt()));
    p.addValue("lt_type", o.getLtType());
    p.addValue("tick", o.getTick());
    p.addValue("created_at", toTimestamp(o.getCreatedAt()));
    p.addValue("customer_address", o.getCustomerAddress());
    p.addValue("customer_name", o.getCustomerName());
    p.addValue("customer_phone", o.getCustomerPhone());
    p.addValue("last_editor_name", o.getLastEditorName());
    p.addValue("order_id", o.getOrderId());
    p.addValue("partner_delivery_name", o.getPartnerDeliveryName());
    p.addValue("partner_tracking_id", o.getPartnerTrackingId());
    p.addValue("account", o.getAccount());
    p.addValue("account_name", o.getAccountName());
    p.addValue("order_link", o.getOrderLink());
    p.addValue("raw_data", o.getRawData());
    return p;
  }

  private Timestamp toTimestamp(LocalDateTime ldt) {
    return ldt != null ? Timestamp.valueOf(ldt) : null;
  }

  // ============================================================
  // API fetch with retry
  // ============================================================

  private OrderListResponseDto fetchPageWithRetry(
      long startTimestamp, long endTimestamp, int page, int pageSize,
      String updateStatus, String status) {
    int retries = 0;
    while (retries < 3) {
      try {
        return orderApiClient.fetchOrdersPage(
            startTimestamp, endTimestamp, page, pageSize, updateStatus, status
        );
      } catch (Exception e) {
        retries++;
        log.warn("Page {} fetch attempt {} failed: {}", page, retries, e.getMessage());
        if (retries >= 3) {
          log.error("Page {} failed after 3 retries, skipping page", page);
          return null;
        }
        try { Thread.sleep(2000L * retries); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
      }
    }
    return null;
  }

  // ============================================================
  // Mapping helpers (no DB access)
  // ============================================================

  private record CustomerSyncResult(Customer entity, boolean isNew) {}

  private CustomerSyncResult mapCustomer(CustomerDTO dto, Long shopId, Map<String, Customer> existing) {
    if (dto == null || dto.getId() == null) {
      return new CustomerSyncResult(null, false);
    }
    boolean isNew = !existing.containsKey(dto.getId());
    Customer customer = new Customer();
    customer.setId(dto.getId());
    customer.setShopId(shopId != null ? shopId : 0L);
    customer.setName(dto.getName() != null ? dto.getName() : "Unknown");
    customer.setGender(dto.getGender());
    customer.setFbId(dto.getFbId());
    customer.setReferralCode(dto.getReferralCode());
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime insertedAt = parseDateTime(dto.getInsertedAt(), "customer.insertedAt");
    LocalDateTime updatedAt = parseDateTime(dto.getUpdatedAt(), "customer.updatedAt");
    customer.setInsertedAt(insertedAt != null ? insertedAt : now);
    customer.setUpdatedAt(updatedAt != null ? updatedAt : now);
    return new CustomerSyncResult(customer, isNew);
  }

  private CustomerSyncResult mapStubCustomer(
      String customerId,
      OrderApiDto dto,
      Map<String, Customer> existing
  ) {
    if (customerId == null || customerId.isBlank()) {
      return new CustomerSyncResult(null, false);
    }
    if (existing.containsKey(customerId)) {
      return new CustomerSyncResult(null, false);
    }
    Customer customer = new Customer();
    customer.setId(customerId);
    customer.setShopId(dto.getShopId() != null ? dto.getShopId() : 0L);
    String name = dto.getBillFullName();
    if (name == null || name.isBlank()) {
      name = dto.getShippingFullName();
    }
    customer.setName(name != null && !name.isBlank() ? name : "Unknown");
    LocalDateTime now = LocalDateTime.now();
    customer.setInsertedAt(now);
    customer.setUpdatedAt(now);
    return new CustomerSyncResult(customer, true);
  }

  private String resolveCustomerId(OrderApiDto dto) {
    if (dto.getCustomer() != null && dto.getCustomer().getId() != null && !dto.getCustomer().getId().isBlank()) {
      return dto.getCustomer().getId();
    }
    if (dto.getCustomerId() != null && !dto.getCustomerId().isBlank()) {
      return dto.getCustomerId();
    }
    return null;
  }

  private void mapOrder(Order order, OrderApiDto dto, Set<String> existingWarehouseIds, Set<String> validCustomerIds, Set<String> existingPosUserIds) {
    order.setRawData(dto.getRawData());
    order.setShopId(dto.getShopId());
    order.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
    order.setStatusName(dto.getStatusName());

    String customerId = resolveCustomerId(dto);
    if (customerId != null && validCustomerIds.contains(customerId)) {
      order.setCustomerId(customerId);
    } else {
      order.setCustomerId(null);
    }
    // === Creator ID - chỉ set nếu tồn tại trong pos_users ===
    String creatorId = dto.getCreator() != null ? dto.getCreator().getId() : null;
    if (creatorId != null && !creatorId.isBlank()
        && existingPosUserIds != null && existingPosUserIds.contains(creatorId)) {
      order.setCreatorId(creatorId);
    } else {
      order.setCreatorId(null);
    }

    // === User IDs ===
    order.setAssigningSellerId(dto.getAssigningSellerId());
    order.setAssigningCareId(dto.getAssigningCareId());
    order.setMarketerId(dto.getMarketerId());
    order.setLastEditorId(dto.getLastEditorId());
    order.setPageId(dto.getPageId());
    order.setAdId(dto.getAdId());
    order.setAccount(dto.getAccount());
    order.setSubStatus(dto.getSubStatus());

    // === Warehouse ID - chỉ set nếu tồn tại trong DB ===
    if (dto.getWarehouseId() != null && !dto.getWarehouseId().isBlank()
        && existingWarehouseIds != null && existingWarehouseIds.contains(dto.getWarehouseId())) {
      order.setWarehouseId(dto.getWarehouseId());
    } else {
      order.setWarehouseId(null);
    }

    // === Money fields ===
    if (dto.getTotalPrice() != null) order.setTotalPrice(dto.getTotalPrice());
    if (dto.getTotalPriceAfterSubDiscount() != null) {
      order.setTotalPriceAfterSubDiscount(BigDecimal.valueOf(dto.getTotalPriceAfterSubDiscount()));
    }
    if (dto.getTotalDiscount() != null) {
      order.setTotalDiscount(BigDecimal.valueOf(dto.getTotalDiscount()));
    }
    if (dto.getCod() != null) order.setCod(BigDecimal.valueOf(dto.getCod()));
    if (dto.getPrepaid() != null) order.setPrepaid(BigDecimal.valueOf(dto.getPrepaid()));
    if (dto.getShippingFee() != null) order.setShippingFee(BigDecimal.valueOf(dto.getShippingFee()));
    if (dto.getSurcharge() != null) order.setSurcharge(BigDecimal.valueOf(dto.getSurcharge()));
    if (dto.getTax() != null) order.setTax(BigDecimal.valueOf(dto.getTax()));
    if (dto.getMoneyToCollect() != null) {
      order.setMoneyToCollect(BigDecimal.valueOf(dto.getMoneyToCollect()));
    }
    if (dto.getCash() != null) order.setCash(BigDecimal.valueOf(dto.getCash()));
    if (dto.getTransferMoney() != null) order.setTransferMoney(BigDecimal.valueOf(dto.getTransferMoney()));
    if (dto.getChargedByMomo() != null) order.setChargedByMomo(BigDecimal.valueOf(dto.getChargedByMomo()));
    if (dto.getChargedByCard() != null) order.setChargedByCard(BigDecimal.valueOf(dto.getChargedByCard()));
    if (dto.getChargedByQrpay() != null) order.setChargedByQrpay(BigDecimal.valueOf(dto.getChargedByQrpay()));
    if (dto.getExchangePayment() != null) order.setExchangePayment(BigDecimal.valueOf(dto.getExchangePayment()));
    if (dto.getExchangeValue() != null) order.setExchangeValue(BigDecimal.valueOf(dto.getExchangeValue()));
    if (dto.getPartnerFee() != null) order.setPartnerFee(BigDecimal.valueOf(dto.getPartnerFee()));
    if (dto.getFeeMarketplace() != null) order.setFeeMarketplace(BigDecimal.valueOf(dto.getFeeMarketplace()));
    if (dto.getBuyerTotalAmount() != null) {
      order.setBuyerTotalAmount(BigDecimal.valueOf(dto.getBuyerTotalAmount()));
    }
    if (dto.getLeveraPoint() != null) order.setLeveraPoint(dto.getLeveraPoint().intValue());

    // === Boolean flags ===
    order.setIsLivestream(boolToInt(dto.getIsLivestream()));
    order.setIsLiveShopping(boolToInt(dto.getIsLiveShopping()));
    order.setIsFreeShipping(boolToInt(dto.getIsFreeShipping()));
    order.setIsSmc(boolToInt(dto.getIsSmc()));
    order.setIsCalculationTax(dto.getIsCalculationTax());
    order.setCustomerPayFee(dto.getCustomerPayFee());
    order.setReceivedAtShop(boolToInt(dto.getReceivedAtShop()));
    order.setIsExchangeOrder(dto.getIsExchangeOrder());

    // === Bill & Shipping Info ===
    order.setBillFullName(dto.getBillFullName());
    order.setBillPhoneNumber(dto.getBillPhoneNumber());
    order.setBillEmail(dto.getBillEmail());
    order.setNote(dto.getNote());
    order.setNotePrint(dto.getNotePrint());
    order.setLink(dto.getLink());
    order.setOrderSources(dto.getOrderSources());
    order.setOrderSourcesName(dto.getOrderSourcesName());
    if (dto.getSystemId() != null) order.setOrderCode(String.valueOf(dto.getSystemId()));

    // === Shipping Address ===
    ShippingAddressDTO addr = dto.getShippingAddress();
    if (addr != null) {
      order.setShippingFullName(addr.getFullName());
      order.setShippingPhoneNumber(addr.getPhoneNumber());
      order.setShippingAddress(addr.getAddress());
      order.setShippingFullAddress(addr.getFullAddress());
      order.setShippingProvinceName(addr.getProvinceName());
      order.setShippingDistrictName(addr.getDistrictName());
      order.setShippingCommuneName(addr.getCommuneName());
      order.setShippingProvinceId(addr.getProvinceId());
      order.setShippingDistrictId(addr.getDistrictId());
      order.setShippingCommuneId(addr.getCommuneId());
    }

    // === Flat shipping address fields (if present in DTO) ===
    if (dto.getShippingFullName() != null) order.setShippingFullName(dto.getShippingFullName());
    if (dto.getShippingPhoneNumber() != null) order.setShippingPhoneNumber(dto.getShippingPhoneNumber());
    if (dto.getShippingFullAddress() != null) order.setShippingFullAddress(dto.getShippingFullAddress());
    if (dto.getShippingProvinceId() != null) order.setShippingProvinceId(dto.getShippingProvinceId());
    if (dto.getShippingProvinceName() != null) order.setShippingProvinceName(dto.getShippingProvinceName());
    if (dto.getShippingDistrictId() != null) order.setShippingDistrictId(dto.getShippingDistrictId());
    if (dto.getShippingDistrictName() != null) order.setShippingDistrictName(dto.getShippingDistrictName());
    if (dto.getShippingCommuneId() != null) order.setShippingCommuneId(dto.getShippingCommuneId());
    if (dto.getShippingCommuneName() != null) order.setShippingCommuneName(dto.getShippingCommuneName());
    if (dto.getShippingCountryCode() != null) order.setShippingCountryCode(dto.getShippingCountryCode());
    if (dto.getShippingPostCode() != null) order.setShippingPostCode(dto.getShippingPostCode());

    // === UTM Fields ===
    order.setPUtmSource(dto.getPUtmSource());
    order.setPUtmMedium(dto.getPUtmMedium());
    order.setPUtmCampaign(dto.getPUtmCampaign());
    order.setPUtmContent(dto.getPUtmContent());
    order.setPUtmTerm(dto.getPUtmTerm());
    order.setPUtmId(dto.getPUtmId());

    // === Tracking & Partner ===
    order.setTrackingLink(dto.getTrackingLink());
    order.setOrderLink(dto.getOrderLink());
    order.setReturnedReason(dto.getReturnedReason());
    order.setReturnedReasonName(dto.getReturnedReasonName());
    order.setTimeSendPartner(parseDateTime(dto.getTimeSendPartner(), "order.timeSendPartner"));

    // === Conversation ===
    order.setConversationId(dto.getConversationId());
    order.setPostId(dto.getPostId());
    order.setAccountName(dto.getAccountName());

    order.setInsertedAt(parseDateTime(dto.getInsertedAt(), "order.insertedAt"));
    order.setUpdatedAt(parseDateTime(dto.getUpdatedAt(), "order.updatedAt"));
  }

  private Integer boolToInt(Boolean value) {
    return value != null && value ? 1 : 0;
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

  private BatchSyncResult retryBatchSync(List<OrderApiDto> orders, Set<String> existingVarIds, Set<String> existingWarehouseIds, Set<String> existingPosUserIds) {
    int retries = 0;
    int maxRetries = 10;
    long waitTimeMs = 1000;

    while (retries < maxRetries) {
      try {
        return self.syncPageBatch(orders, existingVarIds, existingWarehouseIds, existingPosUserIds);
      } catch (Exception e) {
        if (isDeadlockOrRollbackOnly(e)) {
          retries++;
          log.warn("Page batch deadlock on attempt {}/{}, waiting {}ms...", retries, maxRetries, waitTimeMs);
          if (retries >= maxRetries) {
            log.error("Page batch failed after {} retries, skipping batch", maxRetries);
            List<String> errors = List.of("Batch failed after " + maxRetries + " retries: " + e.getMessage());
            return new BatchSyncResult(0, 0, 0, 0, 0, 0, 0, 0, orders.size(), errors, List.of());
          }
          try { Thread.sleep(waitTimeMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
          waitTimeMs *= 1.5; // Exponential backoff
        } else {
          throw e;
        }
      }
    }
    return new BatchSyncResult(0, 0, 0, 0, 0, 0, 0, 0, orders.size(), List.of("Max retries exceeded"), List.of());
  }

  @FunctionalInterface
  private interface RetryableOperation {
    void execute() throws Exception;
  }

  private void saveWithDeadlockRetry(RetryableOperation op) {
    int retries = 0;
    int maxRetries = 10;
    while (retries < maxRetries) {
      try {
        op.execute();
        return;
      } catch (Exception e) {
        if (isDeadlockOrRollbackOnly(e)) {
          retries++;
          long waitTime = 1000L * retries; // 1s, 2s, 3s...
          log.warn("Deadlock/rollback error on attempt {}/{}, waiting {}ms before retry...",
              retries, maxRetries, waitTime);
          if (retries >= maxRetries) {
            log.error("Deadlock persisted after {} retries, giving up", maxRetries);
            return; // Skip this batch instead of throwing
          }
          try { Thread.sleep(waitTime); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        } else {
          log.error("Non-deadlock error: {}", e.getMessage());
          throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
      }
    }
  }

  private boolean isDeadlockOrRollbackOnly(Throwable t) {
    if (t == null) return false;
    String msg = t.getMessage();
    if (msg != null) {
      String lower = msg.toLowerCase();
      if (lower.contains("deadlock")
          || lower.contains("rollback-only")
          || lower.contains("record has changed since last read")
          || lower.contains("could not execute statement")) {
        return true;
      }
    }
    // Check nested exception
    Throwable cause = t.getCause();
    if (cause != null && cause != t) {
      return isDeadlockOrRollbackOnly(cause);
    }
    return false;
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
