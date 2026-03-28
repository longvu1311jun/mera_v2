package mera.mera_v2.lark.webhook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.dto.BitableRecordRequest;
import mera.mera_v2.lark.webhook.dto.PosOrderWebhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosToBitableMapper {
    
    private final SellerBaseMappingService sellerBaseMappingService;
    
    @Value("${lark.default.user-id:ou_4cf48041bec4170651def0c025217097}")
    private String defaultUserId;
    
    public Map<String, Object> mapToBitableFields(PosOrderWebhook webhookData) {
        Map<String, Object> fields = new HashMap<>();
        
        PosOrderWebhook.ShippingAddress shippingAddress = getShippingAddress(webhookData);
        PosOrderWebhook.CustomerInfo customer = getCustomer(webhookData);
        
        // Tên khách hàng
        String fullName = null;
        if (shippingAddress != null && shippingAddress.getFullName() != null) {
            fullName = shippingAddress.getFullName();
        } else if (customer != null) {
            fullName = customer.getFullName() != null ? customer.getFullName() : customer.getName();
        }
        if ((fullName == null || fullName.isBlank()) && webhookData != null) {
            fullName = webhookData.getRootFullName() != null ? webhookData.getRootFullName() : webhookData.getRootName();
        }
        if (fullName != null && !fullName.isBlank()) {
            fields.put("Tên khách hàng", BitableRecordRequest.FieldValue.text(fullName));
        }
        
        // Điện thoại
        String phoneNumber = null;
        if (shippingAddress != null && shippingAddress.getPhoneNumber() != null) {
            phoneNumber = shippingAddress.getPhoneNumber();
        } else if (customer != null) {
            if (customer.getPhoneNumbers() != null && !customer.getPhoneNumbers().isEmpty()) {
                phoneNumber = customer.getPhoneNumbers().get(0);
            } else {
                phoneNumber = customer.getPhoneNumber() != null ? customer.getPhoneNumber() : customer.getPhone();
            }
        }
        if ((phoneNumber == null || phoneNumber.isBlank()) && webhookData != null) {
            phoneNumber = webhookData.getRootPhoneNumber() != null ? webhookData.getRootPhoneNumber() : webhookData.getRootPhone();
        }
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            fields.put("Điện thoại", BitableRecordRequest.FieldValue.text(phoneNumber));
        }
        
        // Địa chỉ
        String fullAddress = null;
        if (shippingAddress != null) {
            fullAddress = shippingAddress.getFullAddress() != null ? shippingAddress.getFullAddress() : shippingAddress.getAddress();
        }
        if ((fullAddress == null || fullAddress.isBlank()) && customer != null) {
            fullAddress = customer.getFullAddress() != null ? customer.getFullAddress() : customer.getAddress();
        }
        if ((fullAddress == null || fullAddress.isBlank()) && webhookData != null) {
            fullAddress = webhookData.getRootFullAddress() != null ? webhookData.getRootFullAddress() : webhookData.getRootAddress();
        }
        if (fullAddress != null && !fullAddress.isBlank()) {
            fields.put("Địa chỉ", BitableRecordRequest.FieldValue.text(fullAddress));
        }
        
        // Tỉnh/Thành phố (text)
        String provinceName = null;
        if (shippingAddress != null && shippingAddress.getProvinceName() != null) {
            provinceName = shippingAddress.getProvinceName();
        } else if (customer != null) {
            provinceName = customer.getProvinceName();
        }
        if ((provinceName == null || provinceName.isBlank()) && webhookData != null) {
            provinceName = webhookData.getRootProvinceName();
        }
        if (provinceName != null && !provinceName.isBlank()) {
            fields.put("Tỉnh/Thành phố", BitableRecordRequest.FieldValue.text(provinceName));
        }
        
        // Link - conversation_link (KHÔNG thêm vào request body)
        // Bỏ qua trường Link theo yêu cầu

        // Tên Liệu Trình - mặc định (array)
        fields.put("Tên Liệu Trình", BitableRecordRequest.FieldValue.singleSelect("Liệu trình 1"));
        
        log.info("✅ Mapped {} fields total", fields.size());

        return fields;
    }
    
    /**
     * Lấy tên khách hàng từ webhook
     */
    public String getTenKhach(PosOrderWebhook webhookData) {
        PosOrderWebhook.ShippingAddress shippingAddress = getShippingAddress(webhookData);
        PosOrderWebhook.CustomerInfo customer = getCustomer(webhookData);
        
        if (shippingAddress != null && shippingAddress.getFullName() != null) {
            return shippingAddress.getFullName();
        } else if (customer != null) {
            return customer.getFullName() != null ? customer.getFullName() : customer.getName();
        } else if (webhookData != null) {
            return webhookData.getRootFullName() != null ? webhookData.getRootFullName() : webhookData.getRootName();
        }
        return null;
    }
    
    /**
     * Lấy số điện thoại từ webhook
     */
    public String getDienThoai(PosOrderWebhook webhookData) {
        PosOrderWebhook.ShippingAddress shippingAddress = getShippingAddress(webhookData);
        PosOrderWebhook.CustomerInfo customer = getCustomer(webhookData);
        
        if (shippingAddress != null && shippingAddress.getPhoneNumber() != null) {
            return shippingAddress.getPhoneNumber();
        } else if (customer != null) {
            if (customer.getPhoneNumbers() != null && !customer.getPhoneNumbers().isEmpty()) {
                return customer.getPhoneNumbers().get(0);
            } else {
                return customer.getPhoneNumber() != null ? customer.getPhoneNumber() : customer.getPhone();
            }
        } else if (webhookData != null) {
            return webhookData.getRootPhoneNumber() != null ? webhookData.getRootPhoneNumber() : webhookData.getRootPhone();
        }
        return null;
    }
    
    /**
     * Lấy địa chỉ từ webhook
     */
    public String getDiaChi(PosOrderWebhook webhookData) {
        PosOrderWebhook.ShippingAddress shippingAddress = getShippingAddress(webhookData);
        PosOrderWebhook.CustomerInfo customer = getCustomer(webhookData);
        
        if (shippingAddress != null) {
            return shippingAddress.getFullAddress() != null ? shippingAddress.getFullAddress() : shippingAddress.getAddress();
        } else if (customer != null) {
            return customer.getFullAddress() != null ? customer.getFullAddress() : customer.getAddress();
        } else if (webhookData != null) {
            return webhookData.getRootFullAddress() != null ? webhookData.getRootFullAddress() : webhookData.getRootAddress();
        }
        return null;
    }
    
    /**
     * Lấy tuổi từ webhook (nếu có)
     * Note: Hiện tại webhook không có field tuổi, có thể tính từ ngày sinh nếu có trong tương lai
     */
    public Integer getTuoi(PosOrderWebhook webhookData) {
        // TODO: Tính tuổi từ ngày sinh nếu có trong webhook
        return null;
    }
    
    private PosOrderWebhook.ShippingAddress getShippingAddress(PosOrderWebhook webhookData) {
        if (webhookData == null) {
            return null;
        }
        
        // Ưu tiên 1: ROOT LEVEL
        if (webhookData.getShippingAddress() != null) {
            PosOrderWebhook.ShippingAddress addr = webhookData.getShippingAddress();
            log.info("✅ Found shipping_address at ROOT level");
            if (addr.getNewAddress() != null) {
                return addr.getNewAddress();
            }
            return addr;
        }
        
        // Ưu tiên 2: data.shipping_address
        if (webhookData.getData() != null) {
            PosOrderWebhook.ShippingAddress addr = webhookData.getData().getShippingAddress();
            if (addr != null) {
//                log.info("✅ Found shipping_address in data.shipping_address");
                if (addr.getNewAddress() != null) {
                    return addr.getNewAddress();
                }
                return addr;
            }
            
            // Ưu tiên 3: histories
            if (webhookData.getData().getHistories() != null) {
                for (int i = webhookData.getData().getHistories().size() - 1; i >= 0; i--) {
                    PosOrderWebhook.HistoryItem history = webhookData.getData().getHistories().get(i);
                    if (history != null && history.getShippingAddress() != null 
                            && history.getShippingAddress().getNewAddress() != null) {
                        return history.getShippingAddress().getNewAddress();
                    }
                }
            }
        }
        
        // Ưu tiên 4: root histories
        if (webhookData.getHistories() != null) {
            for (int i = webhookData.getHistories().size() - 1; i >= 0; i--) {
                PosOrderWebhook.HistoryItem history = webhookData.getHistories().get(i);
                if (history != null && history.getShippingAddress() != null 
                        && history.getShippingAddress().getNewAddress() != null) {
                    log.info("✅ Found shipping_address in root histories[{}]", i);
                    return history.getShippingAddress().getNewAddress();
                }
            }
        }
        
        return null;
    }
    
    private PosOrderWebhook.CustomerInfo getCustomer(PosOrderWebhook webhookData) {
        if (webhookData == null) {
            return null;
        }
        
        // Ưu tiên 1: ROOT LEVEL
        PosOrderWebhook.CustomerInfo customer = webhookData.getCustomer();
        if (customer != null) {
            String customerName = customer.getName();
            if (customerName != null && !customerName.isBlank()) {
//                log.info("✅ Found customer at ROOT level: {}", customerName);
            } else {
                log.info("✅ Found customer at ROOT level: (name is null or blank)");
            }
            return customer;
        }
        
        // Ưu tiên 2: data.customer
        if (webhookData.getData() != null && webhookData.getData().getCustomer() != null) {
            return webhookData.getData().getCustomer();
        }
        
        return null;
    }

    public PosOrderWebhook.AssigningSeller getAssigningCare(PosOrderWebhook webhookData) {
        if (webhookData == null) {
            return null;
        }
        
        // Ưu tiên 1: ROOT LEVEL
        PosOrderWebhook.AssigningSeller assigningCare = webhookData.getAssigningCare();
        if (assigningCare != null) {
            String careName = assigningCare.getName();
            if (careName != null && !careName.isBlank()) {
                log.info("✅ Found assigning_care at ROOT level: {}", careName);
                return assigningCare;
            } else {
                log.info("✅ Found assigning_care at ROOT level: (name is null or blank)");
            }
        }
        
        // Ưu tiên 2: data.assigning_care
        if (webhookData.getData() != null && webhookData.getData().getAssigningCare() != null) {
            PosOrderWebhook.AssigningSeller dataCare = webhookData.getData().getAssigningCare();
            String dataCareName = dataCare.getName();
            if (dataCareName != null && !dataCareName.isBlank()) {
                return dataCare;
            }
        }
        
        return null;
    }
    
    /**
     * Lấy số điện thoại của CSKH từ webhook
     */
    public String getCskhPhoneNumber(PosOrderWebhook webhookData) {
        PosOrderWebhook.AssigningSeller cskh = getAssigningCare(webhookData);
        if (cskh != null && cskh.getPhoneNumber() != null && !cskh.getPhoneNumber().isBlank()) {
            return cskh.getPhoneNumber();
        }
        return null;
    }
    
    public String findBaseIdFromWebhook(PosOrderWebhook webhookData) {
        String sellerName = null;
        String source = null;
        
        // Ưu tiên 1: ROOT assigning_care
        if (webhookData.getAssigningCare() != null 
                && webhookData.getAssigningCare().getName() != null) {
            sellerName = webhookData.getAssigningCare().getName();
            source = "root.assigning_care";
        }
        // Ưu tiên 2: ROOT assigning_seller
        else if (webhookData.getAssigningSeller() != null 
                && webhookData.getAssigningSeller().getName() != null) {
            sellerName = webhookData.getAssigningSeller().getName();
            source = "root.assigning_seller";
        }
        // Ưu tiên 3: data.assigning_care
        else if (webhookData.getData() != null 
                && webhookData.getData().getAssigningCare() != null
                && webhookData.getData().getAssigningCare().getName() != null) {
            sellerName = webhookData.getData().getAssigningCare().getName();
            source = "data.assigning_care";
        }
        // Ưu tiên 4: data.assigning_seller
        else if (webhookData.getData() != null 
                && webhookData.getData().getAssigningSeller() != null
                && webhookData.getData().getAssigningSeller().getName() != null) {
            sellerName = webhookData.getData().getAssigningSeller().getName();
            source = "data.assigning_seller";
        }
        
        if (sellerName != null && !sellerName.isBlank()) {
            log.info("🔍 Tìm Base ID cho seller '{}' (từ {})", sellerName, source);
            return sellerBaseMappingService.findBaseIdBySellerName(sellerName)
                    .orElse(null);
        }
        
        return null;
    }
    
    public Map<String, Object> mapToBitableFieldsWithOverrides(
            PosOrderWebhook webhookData,
            Map<String, Object> fieldOverrides
    ) {
        Map<String, Object> fields = mapToBitableFields(webhookData);
        if (fieldOverrides != null) {
            fields.putAll(fieldOverrides);
        }
        return fields;
    }
}
