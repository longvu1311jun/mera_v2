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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final ObjectMapper objectMapper;

    public PersistenceResult saveFromWebhook(JsonNode webhookData) {
        log.info("=== BẮT ĐẦU LƯU DATA TỪ WEBHOOK VÀO DB ===");

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
            log.info("Processing order ID: {}", orderId);

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

        } catch (Exception e) {
            log.error("Lỗi khi lưu data từ webhook: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
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

            // Generate customer ID from phone number (CustomerInfo doesn't have id field)
            String customerId = null;
            String phone = getPhoneNumber(webhook);
            if (phone != null && !phone.isBlank()) {
                customerId = "CUST_" + phone.replaceAll("[^0-9]", "");
            }

            if (customerId == null || customerId.isBlank()) {
                log.info("   Customer: khong co phone de tao customer ID, bo qua");
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
            customer.setShopId(1L); // Default shop ID
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

            // Luu tung phone number
            for (String phone : phoneNumbers) {
                String normalizedPhone = normalizePhoneNumber(phone);
                if (normalizedPhone != null && !normalizedPhone.isBlank()) {
                    // Kiem tra da ton tai chua
                    List<CustomerPhoneNumber> existing = customerPhoneNumberRepository.findAll();
                    boolean exists = existing.stream()
                            .anyMatch(cp -> cp.getCustomerId().equals(customerId) &&
                                    normalizePhoneNumber(cp.getPhoneNumber()).equals(normalizedPhone));

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
                // Parse inserted_at from webhook data
                order.setInsertedAt(parseWebhookDateTime(webhook.getInsertedAt()));
                result.setSaved(true);
                log.info("   Order: Tạo mới order ID={}", orderId);
            }

            // Map order fields
            order.setUpdatedAt(LocalDateTime.now());
            order.setStatus(webhook.getStatus() != null ? webhook.getStatus() : 0);

            // Shop ID - hardcode for now
            order.setShopId(1546758L);

            // Customer info - CustomerInfo doesn't have id field, so we use phone-based customer ID
            String phone = getPhoneNumber(webhook);
            if (phone != null && !phone.isBlank()) {
                order.setCustomerId("CUST_" + phone.replaceAll("[^0-9]", ""));
            }

            // Shipping address
            PosOrderWebhook.ShippingAddress shippingAddress = getShippingAddress(webhook);
            if (shippingAddress != null) {
                order.setShippingFullName(shippingAddress.getFullName());
                order.setShippingPhoneNumber(shippingAddress.getPhoneNumber());
                order.setShippingAddress(shippingAddress.getAddress());
                order.setShippingFullAddress(shippingAddress.getFullAddress());
                order.setShippingProvinceName(shippingAddress.getProvinceName());
            }

            // Assigning seller/care
            PosOrderWebhook.AssigningSeller seller = webhook.getAssigningSeller();
            if (seller != null) {
                order.setAssigningSellerId(seller.getId());
            }

            PosOrderWebhook.AssigningSeller care = webhook.getAssigningCare();
            if (care != null) {
                order.setAssigningCareId(care.getId());
            }

            // Order sources
            order.setOrderSourcesName(webhook.getOrderSourcesName());

            // Raw data
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

    private PosOrderWebhook.CustomerInfo getCustomerInfo(PosOrderWebhook webhook) {
        if (webhook.getCustomer() != null) {
            return webhook.getCustomer();
        }
        if (webhook.getData() != null && webhook.getData().getCustomer() != null) {
            return webhook.getData().getCustomer();
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
