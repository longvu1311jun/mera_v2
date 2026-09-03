package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.Customer;
import mera.mera_v2.entity.CustomerPhoneNumber;
import mera.mera_v2.entity.Order;
import mera.mera_v2.entity.OrderItem;
import mera.mera_v2.entity.OrderPayment;
import mera.mera_v2.entity.OrderStatusHistory;
import mera.mera_v2.lark.webhook.dto.PosOrderWebhook;
import mera.mera_v2.repository.CustomerPhoneNumberRepository;
import mera.mera_v2.repository.CustomerRepository;
import mera.mera_v2.repository.OrderItemRepository;
import mera.mera_v2.repository.OrderPaymentRepository;
import mera.mera_v2.repository.OrderRepository;
import mera.mera_v2.repository.OrderStatusHistoryRepository;
import mera.mera_v2.repository.PosUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service để lưu data từ webhook vào database
 * Thực hiện upsert: insert nếu chưa tồn tại, update nếu đã tồn tại
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookPersistenceService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CustomerPhoneNumberRepository customerPhoneNumberRepository;
    private final PosUserRepository posUserRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /** Giay toi da cho mot buoc ghi cua webhook. */
    private static final int WRITE_TIMEOUT_SECONDS = 10;

    /**
     * Chan cung thoi gian moi buoc ghi. Spring ap timeout nay qua Statement.setQueryTimeout(),
     * tuc driver JDBC huy cau lenh — KHONG phu thuoc innodb_lock_wait_timeout / lock_wait_timeout
     * cua server. Can thiet vi neu sessionVariables trong JDBC URL khong duoc ap dung thi thread
     * ket khoa se om connection rat lau roi can pool (xem log "[DbDiag]" luc khoi dong).
     *
     * Chi ap cho luong webhook — job sync/report co cau query nang van chay khong gioi han.
     */
    private TransactionTemplate boundedTx;

    private TransactionTemplate boundedTx() {
        if (boundedTx == null) {
            TransactionTemplate t = new TransactionTemplate(transactionManager);
            t.setTimeout(WRITE_TIMEOUT_SECONDS);
            boundedTx = t;
        }
        return boundedTx;
    }

    public PersistenceResult saveFromWebhook(JsonNode webhookData) {
        log.info("=== BẮT ĐẦU LƯU DATA TỪ WEBHOOK VÀO DB ===");

        int maxRetries = 3;
        long waitTimeMs = 500;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            PersistenceResult result = new PersistenceResult();

            try {
                // Parse webhook data
                PosOrderWebhook orderWebhook = objectMapper.treeToValue(webhookData, PosOrderWebhook.class);

                if (orderWebhook.getId() == null) {
                    log.error("Webhook không có order ID, bỏ qua lưu DB");
                    result.setSuccess(false);
                    result.setErrorMessage("Order ID is null");
                    return result;
                }

                Long orderId = orderWebhook.getId();
                log.info("Processing order ID: {} (attempt {}/{})", orderId, attempt, maxRetries);

                // Moi buoc chay trong mot transaction rieng, bi chan cung o 10 giay.
                // 1. Luu Customer
                CustomerResult customerResult = boundedTx().execute(s -> saveCustomer(orderWebhook));
                result.setCustomerSaved(customerResult.isSaved());
                result.setCustomerUpdated(customerResult.isUpdated());

                // 2. Luu Order
                OrderResult orderResult = boundedTx().execute(s -> saveOrder(orderWebhook));
                result.setOrderSaved(orderResult.isSaved());
                result.setOrderUpdated(orderResult.isUpdated());

                // 3. Luu OrderItems
                int itemsSaved = boundedTx().execute(s -> saveOrderItems(orderWebhook, webhookData));
                result.setItemsSaved(itemsSaved);

                // 4. Luu OrderPayments (neu co)
                int paymentsSaved = boundedTx().execute(s -> saveOrderPayments(orderWebhook, webhookData));
                result.setPaymentsSaved(paymentsSaved);

                // 5. Luu OrderStatusHistory
                int historiesSaved = boundedTx().execute(s -> saveOrderStatusHistory(orderWebhook));
                result.setHistoriesSaved(historiesSaved);

                // 6. KHONG tinh LT o day nua.
                // calculateForOrder() chay `UPDATE customers SET lt_count` — dung chinh dong
                // customers ma job sync dang giu khoa, gay "Lock wait timeout exceeded" roi keo
                // theo can Hikari pool. LT khong can realtime: OrderSyncService da goi
                // calculateForOrders() cho moi don sau khi sync, va ket qua la idempotent
                // (recalculate tu orders) nen chay sau van ra dung so.

                result.setSuccess(true);
                log.info("=== HOÀN THÀNH LƯU DATA: Order #{} ===", orderId);
                log.info("   Customer: saved={}, updated={}", result.isCustomerSaved(), result.isCustomerUpdated());
                log.info("   Order: saved={}, updated={}", result.isOrderSaved(), result.isOrderUpdated());
                log.info("   OrderItems: {}", result.getItemsSaved());
                log.info("   Payments: {}", result.getPaymentsSaved());
                log.info("   StatusHistories: {}", result.getHistoriesSaved());

                return result;

            } catch (Exception e) {
                if (isRetryableError(e) && attempt < maxRetries) {
                    log.warn("Retryable error on attempt {}/{}: {}. Retrying in {}ms...",
                        attempt, maxRetries, e.getMessage(), waitTimeMs);
                    try { Thread.sleep(waitTimeMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    waitTimeMs *= 2;
                } else {
                    log.error("Lỗi khi lưu data từ webhook: {}", e.getMessage(), e);
                    result.setSuccess(false);
                    result.setErrorMessage(e.getMessage());
                    return result;
                }
            }
        }

        PersistenceResult result = new PersistenceResult();
        result.setSuccess(false);
        result.setErrorMessage("Max retries exceeded");
        return result;
    }

    private boolean isRetryableError(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("deadlock")
                || lower.contains("lock wait timeout")
                || lower.contains("try restarting transaction")
                // Bi boundedTx cat o 10 giay — lan sau khoa co the da duoc nha
                || lower.contains("transaction timed out")
                || lower.contains("query execution was interrupted")
                || lower.contains("statement cancelled")
                || lower.contains("rollback-only")
                || lower.contains("record has changed since last read")
                || lower.contains("could not execute statement")) {
                return true;
            }
        }
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return isRetryableError(cause);
        }
        return false;
    }

    /**
     * Loi tam thoi (lock timeout / deadlock) phai bay len saveFromWebhook de retry ca luot ghi.
     * Truoc day moi buoc deu nuot Exception nen vong retry 3 lan khong bao gio chay,
     * va data cua webhook am tham bi mat trong khi controller van bao thanh cong.
     * Loi khong retry duoc (data xau, FK...) thi van chi log — retry cung khong cuu duoc.
     */
    private void rethrowIfRetryable(Exception e, String step) {
        if (isRetryableError(e)) {
            throw new IllegalStateException("Loi tam thoi khi luu " + step + ": " + e.getMessage(), e);
        }
    }

    /**
     * Luu hoac cap nhat Customer.
     *
     * Luu y ve transaction: cac method saveXxx o day la private + duoc goi noi bo (this.xxx)
     * nen @Transactional KHONG bao gio co hieu luc — Spring AOP khong proxy duoc.
     * Chu y day la co y: moi buoc chay bang transaction rieng cua repository (ngan nhat co the),
     * gom ca webhook vao mot transaction dai se lam tang manh xung dot khoa voi job sync.
     */
    private CustomerResult saveCustomer(PosOrderWebhook webhook) {
        CustomerResult result = new CustomerResult();

        try {
            PosOrderWebhook.CustomerInfo customerInfo = getCustomerInfo(webhook);
            if (customerInfo == null) {
                log.info("   Customer: khong co thong tin customer trong webhook");
                return result;
            }

            // Dùng mã khách thật từ payload (ưu tiên id — trùng customers.id của luồng đồng bộ),
            // KHÔNG tự chế mã từ SĐT nữa
            String customerId = resolveCustomerId(customerInfo);
            if (customerId == null || customerId.isBlank()) {
                log.info("   Customer: webhook khong co customer id, bo qua");
                return result;
            }

            String name = getCustomerName(customerInfo, webhook);
            Long shopId = customerInfo.getShopId() != null ? customerInfo.getShopId() : webhook.getShopId();
            if (shopId == null) shopId = 1546758L;

            // Uu tien UPDATE co muc tieu (chi 3 cot webhook thuc su biet) thay vi save(entity):
            // tranh ghi de lt_count do job LT tinh song song, va rut ngan thoi gian giu X-lock.
            int updated = customerRepository.updateBasicInfo(customerId, name, shopId, LocalDateTime.now());

            if (updated > 0) {
                result.setUpdated(true);
                log.info("   Customer: Cap nhat customer ID={}", customerId);
            } else {
                Customer customer = new Customer();
                customer.setId(customerId);
                customer.setInsertedAt(parseWebhookDateTime(webhook.getInsertedAt()));
                customer.setName(name);
                customer.setShopId(shopId);
                customer.setUpdatedAt(LocalDateTime.now());
                customerRepository.save(customer);
                result.setSaved(true);
                log.info("   Customer: Tao moi customer ID={}", customerId);
            }

            // Luu phone numbers
            saveCustomerPhoneNumbers(customerId, webhook);

        } catch (Exception e) {
            log.error("   Customer: Loi khi luu customer: {}", e.getMessage());
            rethrowIfRetryable(e, "customer");
        }

        return result;
    }

    /**
     * Lưu số điện thoại của customer
     */
    private void saveCustomerPhoneNumbers(String customerId, PosOrderWebhook webhook) {
        try {
            List<String> phoneNumbers = new ArrayList<>();

            // Lay tu customer info
            PosOrderWebhook.CustomerInfo customerInfo = getCustomerInfo(webhook);
            if (customerInfo != null) {
                if (customerInfo.getPhoneNumber() != null && !customerInfo.getPhoneNumber().isBlank()) {
                    phoneNumbers.add(customerInfo.getPhoneNumber());
                }
                if (customerInfo.getPhoneNumbers() != null) {
                    phoneNumbers.addAll(customerInfo.getPhoneNumbers());
                }
            }

            // Lay tu shipping address
            PosOrderWebhook.ShippingAddress shippingAddress = getShippingAddress(webhook);
            if (shippingAddress != null && shippingAddress.getPhoneNumber() != null) {
                String phone = shippingAddress.getPhoneNumber();
                if (!phoneNumbers.contains(phone)) {
                    phoneNumbers.add(phone);
                }
            }

            // Luu tung phone number - dung query chi tiết thay vi findAll()
            for (String phone : phoneNumbers) {
                String normalizedPhone = normalizePhoneNumber(phone);
                if (normalizedPhone != null && !normalizedPhone.isBlank()) {
                    // Kiem tra da ton tai chua bang query chi tiet
                    List<CustomerPhoneNumber> existing = customerPhoneNumberRepository
                            .findByCustomerIdAndNormalizedPhone(customerId, normalizedPhone);
                    boolean exists = !existing.isEmpty();

                    if (!exists) {
                        CustomerPhoneNumber cpn = new CustomerPhoneNumber();
                        cpn.setCustomerId(customerId);
                        cpn.setPhoneNumber(normalizedPhone);
                        cpn.setIsPrimary(phoneNumbers.indexOf(phone) == 0);
                        cpn.setCreatedAt(LocalDateTime.now());
                        customerPhoneNumberRepository.save(cpn);
                        log.debug("   Customer: Da luu phone number: {}", normalizedPhone);
                    }
                }
            }
        } catch (Exception e) {
            log.error("   Customer: Loi khi luu phone numbers: {}", e.getMessage());
        }
    }

    /**
     * Luu hoac cap nhat Order
     */
    // Chay ben trong boundedTx() cua saveFromWebhook (transaction rieng, timeout 10s).
    private OrderResult saveOrder(PosOrderWebhook webhook) {
        OrderResult result = new OrderResult();

        try {
            Long orderId = webhook.getId();
            Optional<Order> existingOrder = orderRepository.findById(orderId);
            Order order;

            if (existingOrder.isPresent()) {
                order = existingOrder.get();
                result.setUpdated(true);
                log.info("   Order: Cập nhật order ID={}", orderId);
            } else {
                order = new Order();
                order.setId(orderId);
                order.setInsertedAt(parseWebhookDateTime(webhook.getInsertedAt()));
                result.setSaved(true);
                log.info("   Order: Tạo mới order ID={}", orderId);
            }

            // === Basic Info ===
            order.setUpdatedAt(LocalDateTime.now());
            order.setStatus(webhook.getStatus() != null ? webhook.getStatus() : 0);
            order.setShopId(webhook.getShopId() != null ? webhook.getShopId() : 1546758L);
            order.setPageId(webhook.getPageId());
            order.setAdId(webhook.getAdId());
            order.setAccount(webhook.getAccountName());

            // === Customer ===
            String orderCustomerId = resolveCustomerId(getCustomerInfo(webhook));
            if (orderCustomerId != null && !orderCustomerId.isBlank()) {
                order.setCustomerId(orderCustomerId);
            }

            // === User IDs ===
            String creatorId = getValidCreatorId(webhook.getCreator());
            order.setCreatorId(creatorId);

            String assigningSellerId = getValidAssigningSellerId(webhook.getAssigningSeller());
            order.setAssigningSellerId(assigningSellerId);

            String assigningCareId = getValidAssigningSellerId(webhook.getAssigningCare());
            order.setAssigningCareId(assigningCareId);

            String lastEditorId = getValidCreatorId(webhook.getLastEditor());
            order.setLastEditorId(lastEditorId);

            // Fallback to direct ID fields if object fields are null
            if (order.getAssigningSellerId() == null && webhook.getAssigningSellerId() != null) {
                String sellerId = posUserRepository.existsById(webhook.getAssigningSellerId())
                    ? webhook.getAssigningSellerId() : null;
                order.setAssigningSellerId(sellerId);
            }
            if (order.getAssigningCareId() == null && webhook.getAssigningCareId() != null) {
                String careId = posUserRepository.existsById(webhook.getAssigningCareId())
                    ? webhook.getAssigningCareId() : null;
                order.setAssigningCareId(careId);
            }
            if (order.getMarketerId() != null && !posUserRepository.existsById(webhook.getMarketerId())) {
                order.setMarketerId(null);
            }
            if (order.getLastEditorId() == null && webhook.getLastEditorId() != null) {
                String editorId = posUserRepository.existsById(webhook.getLastEditorId())
                    ? webhook.getLastEditorId() : null;
                order.setLastEditorId(editorId);
            }
            order.setWarehouseId(webhook.getWarehouseId());

            // === Shipping Address ===
            PosOrderWebhook.ShippingAddress shippingAddress = getShippingAddress(webhook);
            if (shippingAddress != null) {
                order.setShippingFullName(shippingAddress.getFullName());
                order.setShippingPhoneNumber(shippingAddress.getPhoneNumber());
                order.setShippingAddress(shippingAddress.getAddress());
                order.setShippingFullAddress(shippingAddress.getFullAddress());
                order.setShippingProvinceName(shippingAddress.getProvinceName());
                order.setShippingDistrictName(shippingAddress.getDistrictName());
                order.setShippingCommuneName(shippingAddress.getCommuneName());
                order.setShippingProvinceId(shippingAddress.getProvinceId());
                order.setShippingDistrictId(shippingAddress.getDistrictId());
                order.setShippingCommuneId(shippingAddress.getCommuneId());
                order.setShippingCountryCode(shippingAddress.getCountryCode());
                order.setShippingPostCode(shippingAddress.getPostCode());
            }

            // === Bill Info ===
            order.setBillFullName(webhook.getBillFullName());
            order.setBillPhoneNumber(webhook.getBillPhoneNumber());
            order.setBillEmail(webhook.getBillEmail());

            // === Money Fields ===
            if (webhook.getTotalPrice() != null) order.setTotalPrice(webhook.getTotalPrice());
            if (webhook.getTotalPriceAfterSubDiscount() != null) {
                order.setTotalPriceAfterSubDiscount(BigDecimal.valueOf(webhook.getTotalPriceAfterSubDiscount()));
            }
            if (webhook.getTotalDiscount() != null) {
                order.setTotalDiscount(BigDecimal.valueOf(webhook.getTotalDiscount()));
            }
            if (webhook.getCod() != null) order.setCod(BigDecimal.valueOf(webhook.getCod()));
            if (webhook.getPrepaid() != null) order.setPrepaid(BigDecimal.valueOf(webhook.getPrepaid()));
            if (webhook.getShippingFee() != null) order.setShippingFee(BigDecimal.valueOf(webhook.getShippingFee()));
            if (webhook.getSurcharge() != null) order.setSurcharge(BigDecimal.valueOf(webhook.getSurcharge()));
            if (webhook.getTax() != null) order.setTax(BigDecimal.valueOf(webhook.getTax()));
            if (webhook.getMoneyToCollect() != null) {
                order.setMoneyToCollect(BigDecimal.valueOf(webhook.getMoneyToCollect()));
            }
            if (webhook.getCash() != null) order.setCash(BigDecimal.valueOf(webhook.getCash()));
            if (webhook.getTransferMoney() != null) order.setTransferMoney(BigDecimal.valueOf(webhook.getTransferMoney()));
            if (webhook.getChargedByMomo() != null) order.setChargedByMomo(BigDecimal.valueOf(webhook.getChargedByMomo()));
            if (webhook.getChargedByCard() != null) order.setChargedByCard(BigDecimal.valueOf(webhook.getChargedByCard()));
            if (webhook.getChargedByQrpay() != null) order.setChargedByQrpay(BigDecimal.valueOf(webhook.getChargedByQrpay()));
            if (webhook.getExchangePayment() != null) order.setExchangePayment(BigDecimal.valueOf(webhook.getExchangePayment()));
            if (webhook.getExchangeValue() != null) order.setExchangeValue(BigDecimal.valueOf(webhook.getExchangeValue()));
            if (webhook.getPartnerFee() != null) order.setPartnerFee(BigDecimal.valueOf(webhook.getPartnerFee()));
            if (webhook.getFeeMarketplace() != null) order.setFeeMarketplace(BigDecimal.valueOf(webhook.getFeeMarketplace()));
            if (webhook.getBuyerTotalAmount() != null) {
                order.setBuyerTotalAmount(BigDecimal.valueOf(webhook.getBuyerTotalAmount()));
            }
            if (webhook.getLeveraPoint() != null) order.setLeveraPoint(webhook.getLeveraPoint().intValue());

            // === Boolean Flags ===
            order.setIsLivestream(boolToInt(webhook.getIsLivestream()));
            order.setIsLiveShopping(boolToInt(webhook.getIsLiveShopping()));
            order.setIsFreeShipping(boolToInt(webhook.getIsFreeShipping()));
            order.setIsSmc(boolToInt(webhook.getIsSmc()));
            order.setIsCalculationTax(webhook.getIsCalculationTax());
            order.setCustomerPayFee(webhook.getCustomerPayFee());
            order.setReceivedAtShop(boolToInt(webhook.getReceivedAtShop()));
            order.setIsExchangeOrder(webhook.getIsExchangeOrder());

            // === Order Sources ===
            order.setOrderSources(webhook.getOrderSources());
            order.setOrderSourcesName(webhook.getOrderSourcesName());
            order.setSubStatus(webhook.getSubStatus());

            // === Note ===
            order.setNote(webhook.getNote());
            order.setNotePrint(webhook.getNotePrint());
            order.setLink(webhook.getLink());

            // === UTM Fields ===
            order.setPUtmSource(webhook.getPUtmSource());
            order.setPUtmMedium(webhook.getPUtmMedium());
            order.setPUtmCampaign(webhook.getPUtmCampaign());
            order.setPUtmContent(webhook.getPUtmContent());
            order.setPUtmTerm(webhook.getPUtmTerm());
            order.setPUtmId(webhook.getPUtmId());

            // === Tracking & Partner ===
            order.setTrackingLink(webhook.getTrackingLink());
            order.setOrderLink(webhook.getOrderLink());
            order.setReturnedReason(webhook.getReturnedReason());
            order.setReturnedReasonName(webhook.getReturnedReasonName());
            if (webhook.getTimeSendPartner() != null) {
                order.setTimeSendPartner(parseWebhookDateTime(webhook.getTimeSendPartner()));
            }

            // === Times ===
            if (webhook.getTimeAssignSeller() != null) {
                order.setTimeAssignSeller(parseWebhookDateTime(webhook.getTimeAssignSeller()));
            }
            if (webhook.getTimeAssignCare() != null) {
                order.setTimeAssignCare(parseWebhookDateTime(webhook.getTimeAssignCare()));
            }

            // === Conversation ===
            order.setConversationId(webhook.getConversationId());
            order.setPostId(webhook.getPostId());

            // === Raw data ===
            // Chi ghi raw_data (LONGTEXT ~2.5KB/don, 1.5GB toan bang) luc tao moi. Moi webhook cua
            // don cu deu ghi de LONGTEXT nay -> undo/redo lon, giu X-lock lau hon, ma khong ai doc lai.
            if (result.isSaved()) {
                try {
                    order.setRawData(objectMapper.writeValueAsString(webhook));
                } catch (Exception e) {
                    log.warn("Không thể lưu raw data: {}", e.getMessage());
                }
            }

            orderRepository.save(order);

        } catch (Exception e) {
            log.error("   Order: Lỗi khi lưu order: {}", e.getMessage());
            rethrowIfRetryable(e, "order");
        }

        return result;
    }

    private Integer boolToInt(Boolean value) {
        return value != null && value ? 1 : 0;
    }

    /**
     * Luu Order Items
     */
    // Chay ben trong boundedTx() cua saveFromWebhook (transaction rieng, timeout 10s).
    private int saveOrderItems(PosOrderWebhook webhook, JsonNode webhookData) {
        int savedCount = 0;
        try {
            JsonNode itemsNode = webhookData.has("items") ? webhookData.get("items") : null;
            if (itemsNode == null && webhookData.has("data")) {
                itemsNode = webhookData.get("data").get("items");
            }

            if (itemsNode == null || !itemsNode.isArray()) {
                log.info("   OrderItems: khong co items trong webhook");
                return 0;
            }

            Long orderId = webhook.getId();
            List<Long> existingItemIds = new ArrayList<>();

            for (JsonNode itemNode : itemsNode) {
                try {
                    Long itemId = parseLongId(itemNode.has("id") ? itemNode.get("id").asText() : null);
                    if (itemId == null) continue;

                    existingItemIds.add(itemId);
                    Optional<OrderItem> existingItem = orderItemRepository.findById(itemId);
                    OrderItem item;

                    if (existingItem.isPresent()) {
                        item = existingItem.get();
                    } else {
                        item = new OrderItem();
                        item.setId(itemId);
                    }

                    item.setOrderId(orderId);
                    // product_id / variation_id BẮT BUỘC phải lưu — LtCalculationService
                    // match combo theo 2 cột này; thiếu chúng thì mọi đơn qua webhook đều LT=false
                    item.setProductId(getTextField(itemNode, "product_id"));
                    item.setVariationId(getTextField(itemNode, "variation_id"));
                    item.setProductName(getTextField(itemNode, "product_name"));
                    item.setVariationName(getTextField(itemNode, "variation_name"));
                    item.setQuantity(getIntField(itemNode, "quantity", 1));
                    item.setRetailPrice(getDoubleField(itemNode, "retail_price"));
                    item.setNote(getTextField(itemNode, "note"));

                    orderItemRepository.save(item);
                    savedCount++;
                } catch (Exception e) {
                    log.warn("   OrderItems: Loi khi luu item: {}", e.getMessage());
                    rethrowIfRetryable(e, "order item");
                }
            }

        } catch (Exception e) {
            log.error("   OrderItems: Loi khi luu items: {}", e.getMessage());
            rethrowIfRetryable(e, "order items");
        }
        return savedCount;
    }

    /**
     * Luu Order Payments
     */
    // Chay ben trong boundedTx() cua saveFromWebhook (transaction rieng, timeout 10s).
    private int saveOrderPayments(PosOrderWebhook webhook, JsonNode webhookData) {
        int savedCount = 0;
        try {
            JsonNode paymentsNode = webhookData.has("payments") ? webhookData.get("payments") : null;
            if (paymentsNode == null && webhookData.has("data")) {
                paymentsNode = webhookData.get("data").get("payments");
            }

            if (paymentsNode == null || !paymentsNode.isArray()) {
                log.info("   Payments: khong co payments trong webhook");
                return 0;
            }

            Long orderId = webhook.getId();
            // Payment cua don nay da co trong DB — de chong tao trung khi payload khong co "id".
            // Truoc day moi webhook cua cung don (POS ban lai moi lan sua) tao them mot dong moi.
            List<OrderPayment> existingPayments = orderPaymentRepository.findAllByOrderId(orderId);

            for (JsonNode paymentNode : paymentsNode) {
                try {
                    Long paymentId = parseLongId(paymentNode.has("id") ? paymentNode.get("id").asText() : null);
                    String method = getTextField(paymentNode, "method");
                    BigDecimal amount = BigDecimal.ZERO;
                    String amountStr = paymentNode.has("amount") ? paymentNode.get("amount").asText() : null;
                    if (amountStr != null) {
                        try {
                            amount = new BigDecimal(amountStr);
                        } catch (Exception ignored) {}
                    }

                    OrderPayment payment;
                    if (paymentId != null) {
                        Optional<OrderPayment> existing = orderPaymentRepository.findById(paymentId);
                        if (existing.isPresent()) {
                            payment = existing.get();
                        } else {
                            payment = new OrderPayment();
                            payment.setId(paymentId);
                        }
                    } else {
                        payment = findMatchingPayment(existingPayments, method, amount);
                        if (payment == null) {
                            payment = new OrderPayment();
                            existingPayments.add(payment);
                        }
                    }

                    payment.setOrderId(orderId);
                    payment.setMethod(method);
                    payment.setBankName(getTextField(paymentNode, "bank_name"));
                    payment.setAccountNumber(getTextField(paymentNode, "account_number"));
                    payment.setAccountName(getTextField(paymentNode, "account_name"));
                    payment.setAmount(amount);

                    if (payment.getCreatedAt() == null) {
                        payment.setCreatedAt(LocalDateTime.now());
                    }

                    orderPaymentRepository.save(payment);
                    savedCount++;
                } catch (Exception e) {
                    log.warn("   Payments: Lỗi khi lưu payment: {}", e.getMessage());
                    rethrowIfRetryable(e, "payment");
                }
            }

        } catch (Exception e) {
            log.error("   Payments: Lỗi khi lưu payments: {}", e.getMessage());
            rethrowIfRetryable(e, "payments");
        }
        return savedCount;
    }

    /**
     * Luu Order Status History
     */
    // Chay ben trong boundedTx() cua saveFromWebhook (transaction rieng, timeout 10s).
    private int saveOrderStatusHistory(PosOrderWebhook webhook) {
        int savedCount = 0;
        try {
            Long orderId = webhook.getId();

            // Lay cac status history da ton tai trong DB.
            // Key so theo GIAY (cot updated_at la DATETIME khong co phan le) — neu giu mili-giay tu
            // payload thi khong bao gio khop, moi webhook lai INSERT trung -> vi pham UNIQUE
            // uk_status_history_order_status_time va ton mot round-trip DB vo ich cho tung dong.
            Set<String> existingKeys = new HashSet<>();
            Set<Integer> existingStatuses = new HashSet<>();
            List<OrderStatusHistory> existingHistories = orderStatusHistoryRepository.findAllByOrder_IdIn(List.of(orderId));
            for (OrderStatusHistory h : existingHistories) {
                if (h.getNewStatus() != null && h.getUpdatedAt() != null) {
                    existingKeys.add(historyKey(orderId, h.getNewStatus(), h.getUpdatedAt()));
                    existingStatuses.add(h.getNewStatus());
                }
            }

            List<PosOrderWebhook.HistoryItem> histories = webhook.getHistories();
            if (histories == null || histories.isEmpty()) {
                // Khong co history trong payload: chi ghi nhan status hien tai neu don CHUA TUNG o
                // status do. Truoc day key dung LocalDateTime.now() nen khong bao gio trung -> moi
                // webhook them mot dong history moi cho cung mot status.
                Integer status = webhook.getStatus();
                if (status != null && !existingStatuses.contains(status)) {
                    OrderStatusHistory history = new OrderStatusHistory();
                    history.setOrder(orderRepository.findById(orderId).orElse(null));
                    history.setNewStatus(status);
                    history.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                    orderStatusHistoryRepository.save(history);
                    savedCount = 1;
                }
                return savedCount;
            }

            for (PosOrderWebhook.HistoryItem histItem : histories) {
                try {
                    if (histItem.getStatus() == null) continue;

                    LocalDateTime updatedAt;
                    if (histItem.getUpdatedAt() != null) {
                        updatedAt = parseWebhookDateTime(histItem.getUpdatedAt());
                    } else {
                        updatedAt = LocalDateTime.now();
                    }
                    updatedAt = updatedAt.truncatedTo(ChronoUnit.SECONDS);

                    Integer newStatus = null;
                    if (histItem.getStatus().getNewValue() != null) {
                        newStatus = parseInteger(histItem.getStatus().getNewValue().toString());
                    }

                    // Check trung lap
                    if (newStatus != null) {
                        String key = historyKey(orderId, newStatus, updatedAt);
                        if (existingKeys.contains(key)) {
                            log.debug("   StatusHistory: Da ton tai, bo qua status={}, time={}", newStatus, updatedAt);
                            continue;
                        }
                        existingKeys.add(key);
                    }

                    OrderStatusHistory history = new OrderStatusHistory();
                    history.setOrder(orderRepository.findById(orderId).orElse(null));

                    // Lay old status
                    if (histItem.getStatus().getOldValue() != null) {
                        history.setOldStatus(parseInteger(histItem.getStatus().getOldValue().toString()));
                    }
                    if (newStatus != null) {
                        history.setNewStatus(newStatus);
                    }

                    history.setUpdatedAt(updatedAt);

                    orderStatusHistoryRepository.save(history);
                    savedCount++;
                } catch (Exception e) {
                    log.warn("   StatusHistory: Loi khi luu history item: {}", e.getMessage());
                    rethrowIfRetryable(e, "status history item");
                }
            }

        } catch (Exception e) {
            log.error("   StatusHistory: Loi khi luu histories: {}", e.getMessage());
            rethrowIfRetryable(e, "status histories");
        }
        return savedCount;
    }

    // ============== HELPER METHODS ==============

    /** Key chong trung status history: order + status + thoi diem lam tron GIAY (khop DATETIME trong DB). */
    private String historyKey(Long orderId, Integer newStatus, LocalDateTime updatedAt) {
        return orderId + "_" + newStatus + "_" + updatedAt.truncatedTo(ChronoUnit.SECONDS);
    }

    /** Tim payment cung don da co san (khi payload khong co id) theo method + amount. */
    private OrderPayment findMatchingPayment(List<OrderPayment> existing, String method, BigDecimal amount) {
        for (OrderPayment p : existing) {
            boolean sameMethod = (p.getMethod() == null && method == null)
                    || (p.getMethod() != null && p.getMethod().equals(method));
            boolean sameAmount = p.getAmount() != null && amount != null
                    && p.getAmount().compareTo(amount) == 0;
            if (sameMethod && sameAmount) {
                return p;
            }
        }
        return null;
    }

    /**
     * Get valid user ID from CreatorInfo by checking if user exists in pos_users table.
     * Returns null if user doesn't exist to avoid foreign key constraint violation.
     */
    private String getValidCreatorId(PosOrderWebhook.CreatorInfo userInfo) {
        if (userInfo == null || userInfo.getId() == null) {
            return null;
        }
        String userId = userInfo.getId();
        if (posUserRepository.existsById(userId)) {
            return userId;
        }
        log.warn("   User ID '{}' does not exist in pos_users, setting to null", userId);
        return null;
    }

    /**
     * Get valid user ID from AssigningSeller by checking if user exists in pos_users table.
     * Returns null if user doesn't exist to avoid foreign key constraint violation.
     */
    private String getValidAssigningSellerId(PosOrderWebhook.AssigningSeller seller) {
        if (seller == null || seller.getId() == null) {
            return null;
        }
        String userId = seller.getId();
        if (posUserRepository.existsById(userId)) {
            return userId;
        }
        log.warn("   Seller ID '{}' does not exist in pos_users, setting to null", userId);
        return null;
    }

    private PosOrderWebhook.CustomerInfo getCustomerInfo(PosOrderWebhook webhook) {
        if (webhook.getCustomer() != null) {
            return webhook.getCustomer();
        }
        if (webhook.getData() != null && webhook.getData().getCustomer() != null) {
            return webhook.getData().getCustomer();
        }
        return null;
    }

    /**
     * Mã khách thật từ payload webhook: ưu tiên customer.id (trùng customers.id của luồng
     * đồng bộ POS), fallback customer.customer_id. Trả null nếu payload không có — KHÔNG tự tạo mã.
     */
    private String resolveCustomerId(PosOrderWebhook.CustomerInfo customerInfo) {
        if (customerInfo == null) return null;
        if (customerInfo.getId() != null && !customerInfo.getId().isBlank()) {
            return customerInfo.getId();
        }
        if (customerInfo.getCustomerId() != null && !customerInfo.getCustomerId().isBlank()) {
            return customerInfo.getCustomerId();
        }
        return null;
    }

    private PosOrderWebhook.ShippingAddress getShippingAddress(PosOrderWebhook webhook) {
        // Ưu tiên new address từ history
        if (webhook.getShippingAddress() != null) {
            PosOrderWebhook.ShippingAddress addr = webhook.getShippingAddress();
            if (addr.getNewAddress() != null) {
                return addr.getNewAddress();
            }
            return addr;
        }
        return null;
    }

    private String getCustomerName(PosOrderWebhook.CustomerInfo customerInfo, PosOrderWebhook webhook) {
        if (customerInfo != null) {
            String name = customerInfo.getFullName();
            if (name != null && !name.isBlank()) return name;
            name = customerInfo.getName();
            if (name != null && !name.isBlank()) return name;
        }

        PosOrderWebhook.ShippingAddress shipping = getShippingAddress(webhook);
        if (shipping != null && shipping.getFullName() != null) {
            return shipping.getFullName();
        }

        if (webhook.getRootFullName() != null) return webhook.getRootFullName();
        if (webhook.getRootName() != null) return webhook.getRootName();

        return "Khách hàng";
    }

    private String getPhoneNumber(PosOrderWebhook webhook) {
        PosOrderWebhook.CustomerInfo customerInfo = getCustomerInfo(webhook);
        if (customerInfo != null) {
            if (customerInfo.getPhoneNumber() != null && !customerInfo.getPhoneNumber().isBlank()) {
                return customerInfo.getPhoneNumber();
            }
            if (customerInfo.getPhoneNumbers() != null && !customerInfo.getPhoneNumbers().isEmpty()) {
                return customerInfo.getPhoneNumbers().get(0);
            }
        }

        PosOrderWebhook.ShippingAddress shipping = getShippingAddress(webhook);
        if (shipping != null && shipping.getPhoneNumber() != null) {
            return shipping.getPhoneNumber();
        }

        if (webhook.getRootPhoneNumber() != null) return webhook.getRootPhoneNumber();
        if (webhook.getRootPhone() != null) return webhook.getRootPhone();

        return null;
    }

    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("[^0-9]", "");
    }

    private String getTextField(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }

    private Integer getIntField(JsonNode node, String fieldName, Integer defaultValue) {
        if (node == null) return defaultValue;
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asInt() : defaultValue;
    }

    private Double getDoubleField(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asDouble() : null;
    }

    private Long parseLongId(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            String numeric = id.replaceAll("[^0-9]", "");
            if (!numeric.isEmpty()) {
                try {
                    return Long.parseLong(numeric);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private Integer parseInteger(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return LocalDateTime.now();
        try {
            // Try parsing with various formats
            DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            };
            for (DateTimeFormatter fmt : formatters) {
                try {
                    return LocalDateTime.parse(value, fmt);
                } catch (DateTimeParseException ignored) {}
            }
            // Try ISO format
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            log.warn("Cannot parse datetime '{}', using now", value);
            return LocalDateTime.now();
        }
    }

    /**
     * Parse datetime from webhook data (already in correct timezone)
     */
    private LocalDateTime parseWebhookDateTime(String value) {
        if (value == null || value.isBlank()) return LocalDateTime.now();
        try {
            // Handle timestamps with microseconds: 2025-12-25T02:10:11.020365
            String cleanValue = value;
            int dotIndex = cleanValue.indexOf('.');
            if (dotIndex > 0) {
                cleanValue = cleanValue.substring(0, Math.min(dotIndex + 4, cleanValue.length()));
            }
            return LocalDateTime.parse(cleanValue);
        } catch (Exception e) {
            log.warn("Cannot parse webhook datetime '{}', using now", value);
            return LocalDateTime.now();
        }
    }

    // ============== RESULT CLASSES ==============

    @lombok.Data
    public static class PersistenceResult {
        private boolean success;
        private String errorMessage;
        private boolean customerSaved;
        private boolean customerUpdated;
        private boolean orderSaved;
        private boolean orderUpdated;
        private int itemsSaved;
        private int paymentsSaved;
        private int historiesSaved;
        // Bo ltType/ltCount: webhook khong tinh LT nua (xem ghi chu o saveFromWebhook buoc 6)
    }

    @lombok.Data
    private static class CustomerResult {
        private boolean saved;
        private boolean updated;
    }

    @lombok.Data
    private static class OrderResult {
        private boolean saved;
        private boolean updated;
    }
}
