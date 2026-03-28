package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.dto.OrderDetailViewModel;
import mera.mera_v2.lark.webhook.dto.PancakeOrderResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDetailMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderDetailViewModel mapToDetailView(PancakeOrderResponse.PancakeOrder order) {
        OrderDetailViewModel vm = new OrderDetailViewModel();

        // Bảng 1: Thông tin đơn hàng
        vm.setOrderInfo(mapOrderInfo(order));

        // Bảng 2: Khách hàng
        vm.setCustomer(mapCustomer(order.getCustomer()));

        // Bảng 3: Địa chỉ khách hàng
        vm.setAddresses(mapAddresses(order));

        // Bảng 4: Sản phẩm trong đơn
        vm.setItems(mapItems(order.getItems()));

        // Bảng 5: Lịch sử trạng thái
        vm.setStatusHistories(mapStatusHistories(order.getSystemId(), order.getStatusHistory()));

        // Bảng 6: Lịch sử chỉnh sửa
        vm.setEditHistories(mapEditHistories(order.getSystemId(), order.getHistories()));

        // Bảng 7: Nhân viên liên quan
        vm.setUsers(mapUsers(order));

        return vm;
    }

    private OrderDetailViewModel.OrderInfo mapOrderInfo(PancakeOrderResponse.PancakeOrder o) {
        OrderDetailViewModel.OrderInfo info = new OrderDetailViewModel.OrderInfo();
        info.setId(o.getSystemId());
        info.setOrderCode(o.getId());
        info.setShopId(o.getShopId());
        info.setStatus(o.getStatus());
        info.setStatusName(o.getStatusName());
        info.setBillFullName(o.getBillFullName());
        info.setBillPhoneNumber(o.getBillPhoneNumber());
        info.setBillEmail(o.getBillEmail());

        if (o.getShippingAddress() != null) {
            info.setShippingFullName(o.getShippingAddress().getFullName());
            info.setShippingPhoneNumber(o.getShippingAddress().getPhoneNumber());
            info.setShippingFullAddress(o.getShippingAddress().getFullAddress());
            info.setShippingProvinceName(o.getShippingAddress().getProvinceName());
            info.setShippingDistrictName(o.getShippingAddress().getDistrictName());
            info.setShippingCommuneName(o.getShippingAddress().getCommuneName());
        }

        info.setTotalPrice(toBigDecimal(o.getTotalPrice()));
        info.setTotalDiscount(toBigDecimal(o.getTotalDiscount()));
        info.setShippingFee(toBigDecimal(o.getShippingFee()));
        info.setTax(toBigDecimal(o.getTax()));
        info.setCod(toBigDecimal(o.getCod()));
        info.setMoneyToCollect(toBigDecimal(o.getMoneyToCollect()));
        info.setOrderSourcesName(o.getOrderSourcesName());
        info.setAdsSource(o.getAdsSource());
        info.setNote(o.getNote());

        if (o.getCreator() != null) info.setCreatorId(o.getCreator().getId());
        if (o.getAssigningSeller() != null) info.setAssigningSellerId(o.getAssigningSeller().getId());
        if (o.getAssigningCare() != null) info.setAssigningCareId(o.getAssigningCare().getId());
        if (o.getMarketer() != null) info.setMarketerId(o.getMarketer().getId());

        info.setInsertedAt(parseDateTime(o.getInsertedAt()));
        info.setUpdatedAt(parseDateTime(o.getUpdatedAt()));

        return info;
    }

    private OrderDetailViewModel.CustomerInfo mapCustomer(PancakeOrderResponse.Customer c) {
        if (c == null) return null;

        OrderDetailViewModel.CustomerInfo info = new OrderDetailViewModel.CustomerInfo();
        info.setCustomerId(c.getCustomerId());
        info.setName(c.getName());
        info.setGender(c.getGender());
        info.setFbId(c.getFbId());
        info.setOrderCount(c.getOrderCount());
        info.setSucceedOrderCount(c.getSucceedOrderCount());
        info.setPurchasedAmount(toBigDecimal(c.getPurchasedAmount()));
        // Customer không có leveraPoint và isBlock - để null
        info.setInsertedAt(parseDateTime(c.getInsertedAt()));

        if (c.getPhoneNumbers() != null && !c.getPhoneNumbers().isEmpty()) {
            info.setPhoneNumbers(String.join(", ", c.getPhoneNumbers()));
        }

        return info;
    }

    private List<OrderDetailViewModel.AddressInfo> mapAddresses(PancakeOrderResponse.PancakeOrder o) {
        List<OrderDetailViewModel.AddressInfo> addresses = new ArrayList<>();

        // Từ shipping address trong order
        if (o.getShippingAddress() != null) {
            OrderDetailViewModel.AddressInfo addr = new OrderDetailViewModel.AddressInfo();
            addr.setFullName(o.getShippingAddress().getFullName());
            addr.setPhoneNumber(o.getShippingAddress().getPhoneNumber());
            addr.setAddress(o.getShippingAddress().getAddress());
            addr.setFullAddress(o.getShippingAddress().getFullAddress());
            addr.setProvinceId(o.getShippingAddress().getProvinceId());
            addr.setDistrictId(o.getShippingAddress().getDistrictId());
            addr.setCommuneId(o.getShippingAddress().getCommuneId());
            addresses.add(addr);
        }

        // Từ customer shop_customer_addresses
        if (o.getCustomer() != null && o.getCustomer().getShopCustomerAddresses() != null) {
            for (PancakeOrderResponse.ShopCustomerAddress addr : o.getCustomer().getShopCustomerAddresses()) {
                OrderDetailViewModel.AddressInfo info = new OrderDetailViewModel.AddressInfo();
                info.setId(addr.getId());
                info.setFullName(addr.getFullName());
                info.setPhoneNumber(addr.getPhoneNumber());
                info.setAddress(addr.getAddress());
                info.setFullAddress(addr.getFullAddress());
                info.setProvinceId(addr.getProvinceId());
                info.setDistrictId(addr.getDistrictId());
                info.setCommuneId(addr.getCommuneId());
                addresses.add(info);
            }
        }

        return addresses;
    }

    private List<OrderDetailViewModel.ItemInfo> mapItems(List<PancakeOrderResponse.Item> items) {
        if (items == null) return new ArrayList<>();

        return items.stream().map(i -> {
            OrderDetailViewModel.ItemInfo info = new OrderDetailViewModel.ItemInfo();
            info.setItemId(i.getId());
            info.setVariationId(i.getVariationId());
            info.setVariationName(i.getVariationName());
            info.setQuantity(i.getQuantity());
            info.setRetailPrice(toBigDecimal(i.getRetailPrice()));
            info.setTaxRate(toBigDecimal(i.getTaxRate()));
            info.setWeight(toBigDecimal(i.getWeight()));
            info.setDiscountEachProduct(toBigDecimal(i.getDiscountEachProduct()));
            info.setTotalDiscount(toBigDecimal(i.getTotalDiscount()));
            info.setNote(i.getNote());
            return info;
        }).collect(Collectors.toList());
    }

    private List<OrderDetailViewModel.StatusHistoryInfo> mapStatusHistories(Long orderId, List<PancakeOrderResponse.StatusHistory> histories) {
        if (histories == null) return new ArrayList<>();

        return histories.stream().map(h -> {
            OrderDetailViewModel.StatusHistoryInfo info = new OrderDetailViewModel.StatusHistoryInfo();
            info.setOrderId(orderId);
            info.setOldStatus(h.getOldStatus());
            info.setNewStatus(h.getStatus());
            info.setEditorId(h.getEditorId());
            info.setEditorName(convertToString(h.getEditor()));
            info.setUpdatedAt(parseDateTime(h.getUpdatedAt()));
            return info;
        }).collect(Collectors.toList());
    }

    private String convertToString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        return obj.toString();
    }

    private List<OrderDetailViewModel.EditHistoryInfo> mapEditHistories(Long orderId, List<PancakeOrderResponse.History> histories) {
        List<OrderDetailViewModel.EditHistoryInfo> result = new ArrayList<>();

        if (histories == null) return result;

        for (PancakeOrderResponse.History h : histories) {
            if (h.getField() == null) continue;

            // Mỗi History object có thể có nhiều field changed
            // Nhưng theo cấu trúc API, mỗi History có 1 field cụ thể
            OrderDetailViewModel.EditHistoryInfo info = new OrderDetailViewModel.EditHistoryInfo();
            info.setOrderId(orderId);
            info.setFieldChanged(h.getField());
            info.setOldValue(serializeValue(h.getOldValue()));
            info.setNewValue(serializeValue(h.getNewValue()));
            // History không có updatedAt - để null
            result.add(info);
        }

        return result;
    }

    private List<OrderDetailViewModel.UserInfo> mapUsers(PancakeOrderResponse.PancakeOrder o) {
        List<OrderDetailViewModel.UserInfo> users = new ArrayList<>();

        if (o.getCreator() != null) {
            OrderDetailViewModel.UserInfo u = new OrderDetailViewModel.UserInfo();
            u.setId(o.getCreator().getId());
            u.setName(o.getCreator().getName());
            u.setEmail(o.getCreator().getEmail());
            u.setFbId(o.getCreator().getFbId());
            u.setPhoneNumber(o.getCreator().getPhoneNumber());
            u.setRole("creator");
            users.add(u);
        }

        if (o.getAssigningSeller() != null) {
            OrderDetailViewModel.UserInfo u = new OrderDetailViewModel.UserInfo();
            u.setId(o.getAssigningSeller().getId());
            u.setName(o.getAssigningSeller().getName());
            u.setEmail(o.getAssigningSeller().getEmail());
            u.setFbId(o.getAssigningSeller().getFbId());
            u.setPhoneNumber(o.getAssigningSeller().getPhoneNumber());
            u.setRole("seller");
            users.add(u);
        }

        if (o.getAssigningCare() != null) {
            OrderDetailViewModel.UserInfo u = new OrderDetailViewModel.UserInfo();
            u.setId(o.getAssigningCare().getId());
            u.setName(o.getAssigningCare().getName());
            u.setEmail(o.getAssigningCare().getEmail());
            u.setFbId(o.getAssigningCare().getFbId());
            u.setPhoneNumber(o.getAssigningCare().getPhoneNumber());
            u.setRole("care");
            users.add(u);
        }

        if (o.getMarketer() != null) {
            OrderDetailViewModel.UserInfo u = new OrderDetailViewModel.UserInfo();
            u.setId(o.getMarketer().getId());
            u.setName(o.getMarketer().getName());
            u.setEmail(o.getMarketer().getEmail());
            u.setFbId(o.getMarketer().getFbId());
            u.setPhoneNumber(o.getMarketer().getPhoneNumber());
            u.setRole("marketer");
            users.add(u);
        }

        return users;
    }

    private BigDecimal toBigDecimal(Double value) {
        if (value == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(value);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeValue(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }
}
