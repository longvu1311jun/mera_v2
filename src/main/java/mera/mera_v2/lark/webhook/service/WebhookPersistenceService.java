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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

                // 1. Luu Customer
                CustomerResult customerResult = saveCustomer(orderWebhook);
                result.setCustomerSaved(customerResult.isSaved());
                result.setCustomerUpdated(customerResult.isUpdated());

                // 2. Luu Order
                OrderResult orderResult = saveOrder(orderWebhook);
                result.setOrderSaved(orderResult.isSaved());
                result.setOrderUpdated(orderResult.isUpdated());

                // 3. Luu OrderItems
                int itemsSaved = saveOrderItems(orderWebhook, webhookData);
                result.setItemsSaved(itemsSaved);

                // 4. Luu OrderPayments (neu co)
                int paymentsSaved = saveOrderPayments(orderWebhook, webhookData);
                result.setPaymentsSaved(paymentsSaved);

                // 5. Luu OrderStatusHistory
                int historiesSaved = saveOrderStatusHistory(orderWebhook);
                result.setHistoriesSaved(historiesSaved);

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
     * Luu hoac cap nhat Customer
     */
    @Transactional
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

            Optional<Customer> existingCustomer = customerRepository.findById(customerId);
            Customer customer;

            if (existingCustomer.isPresent()) {
                customer = existingCustomer.get();
                result.setUpdated(true);
                log.info("   Customer: Cap nhat customer ID={}", customerId);
            } else {
                customer = new Customer();
                customer.setId(customerId);
                // Parse inserted_at from webhook data
                customer.setInsertedAt(parseWebhookDateTime(webhook.getInsertedAt()));
                result.setSaved(true);
                log.info("   Customer: Tao moi customer ID={}", customerId);
            }

            // Map customer fields
            customer.setName(getCustomerName(customerInfo, webhook));
            Long shopId = customerInfo.getShopId() != null ? customerInfo.getShopId() : webhook.getShopId();
            customer.setShopId(shopId != null ? shopId : 1546758L);
            customer.setUpdatedAt(LocalDateTime.now());

            // Luu customer
            customer = customerRepository.save(customer);

            // Luu phone numbers
            saveCustomerPhoneNumbers(customerId, webhook);

        } catch (Exception e) {
            log.error("   Customer: Loi khi luu customer: {}", e.getMessage());
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
    @Transactional
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
            try {
                order.setRawData(objectMapper.writeValueAsString(webhook));
            } catch (Exception e) {
                log.warn("Không thể lưu raw data: {}", e.getMessage());
            }

            orderRepository.save(order);

        } catch (Exception e) {
            log.error("   Order: Lỗi khi lưu order: {}", e.getMessage());
        }

        return result;
    }

    private Integer boolToInt(Boolean value) {
        return value != null && value ? 1 : 0;
    }

    /**
     * Luu Order Items
     */
    @Transactional
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
                    item.setProductName(getTextField(itemNode, "product_name"));
                    item.setVariationName(getTextField(itemNode, "variation_name"));
                    item.setQuantity(getIntField(itemNode, "quantity", 1));
                    item.setRetailPrice(getDoubleField(itemNode, "retail_price"));
                    item.setNote(getTextField(itemNode, "note"));

                    orderItemRepository.save(item);
                    savedCount++;
                } catch (Exception e) {
                    log.warn("   OrderItems: Loi khi luu item: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("   OrderItems: Loi khi luu items: {}", e.getMessage());
        }
        return savedCount;
    }

    /**
     * Luu Order Payments
     */
    @Transactional
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

            for (JsonNode paymentNode : paymentsNode) {
                try {
                    Long paymentId = parseLongId(paymentNode.has("id") ? paymentNode.get("id").asText() : null);
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
                        payment = new OrderPayment();
                    }

                    payment.setOrderId(orderId);
                    payment.setMethod(getTextField(paymentNode, "method"));
                    payment.setBankName(getTextField(paymentNode, "bank_name"));
                    payment.setAccountNumber(getTextField(paymentNode, "account_number"));
                    payment.setAccountName(getTextField(paymentNode, "account_name"));

                    String amountStr = paymentNode.has("amount") ? paymentNode.get("amount").asText() : null;
                    if (amountStr != null) {
                        try {
                            payment.setAmount(new BigDecimal(amountStr));
                        } catch (Exception e) {
                            payment.setAmount(BigDecimal.ZERO);
                        }
                    }

                    payment.setCreatedAt(LocalDateTime.now());

                    orderPaymentRepository.save(payment);
                    savedCount++;
                } catch (Exception e) {
                    log.warn("   Payments: Lỗi khi lưu payment: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("   Payments: Lỗi khi lưu payments: {}", e.getMessage());
        }
        return savedCount;
    }

    /**
     * Luu Order Status History
     */
    @Transactional
    private int saveOrderStatusHistory(PosOrderWebhook webhook) {
        int savedCount = 0;
        try {
            Long orderId = webhook.getId();

            // Lay cac status history da ton tai trong DB
            Set<String> existingKeys = new HashSet<>();
            List<OrderStatusHistory> existingHistories = orderStatusHistoryRepository.findAllByOrder_IdIn(List.of(orderId));
            for (OrderStatusHistory h : existingHistories) {
                if (h.getNewStatus() != null && h.getUpdatedAt() != null) {
                    String key = orderId + "_" + h.getNewStatus() + "_" + h.getUpdatedAt().toString();
                    existingKeys.add(key);
                }
            }

            List<PosOrderWebhook.HistoryItem> histories = webhook.getHistories();
            if (histories == null || histories.isEmpty()) {
                // Neu khong co history, tao mot ban ghi tu status hien tai
                if (webhook.getStatus() != null) {
                    String key = orderId + "_" + webhook.getStatus() + "_" + LocalDateTime.now().toString();
                    if (!existingKeys.contains(key)) {
                        OrderStatusHistory history = new OrderStatusHistory();
                        history.setOrder(orderRepository.findById(orderId).orElse(null));
                        history.setNewStatus(webhook.getStatus());
                        history.setUpdatedAt(LocalDateTime.now());
                        orderStatusHistoryRepository.save(history);
                        savedCount = 1;
                    }
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

                    Integer newStatus = null;
                    if (histItem.getStatus().getNewValue() != null) {
                        newStatus = parseInteger(histItem.getStatus().getNewValue().toString());
                    }

                    // Check trung lap
                    if (newStatus != null) {
                        String key = orderId + "_" + newStatus + "_" + updatedAt.toString();
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
                }
            }

        } catch (Exception e) {
            log.error("   StatusHistory: Loi khi luu histories: {}", e.getMessage());
        }
        return savedCount;
    }

    // ============== HELPER METHODS ==============

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
