package mera.mera_v2.lark.webhook.service;

import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.dto.BaseTableMapping;
import mera.mera_v2.lark.webhook.getTableID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service để quản lý mapping giữa Base Name và Base ID + Table ID
 */
@Slf4j
@Service
public class BaseTableMappingService {
    
    private List<BaseTableMapping> baseTableMappings = new ArrayList<>();
    
    @Autowired(required = false)
    private getTableID getTableIDService;
    
    /**
     * Load danh sách Base và Table mapping từ getTableID service
     */
    public void loadMappings() {
        if (getTableIDService == null) {
            log.warn("⚠️ getTableIDService is not available, cannot load mappings");
            return;
        }
        
        try {
            List<getTableID.BaseIdInfo> baseIds = getTableIDService.getAllBaseIds();
            java.util.Map<String, List<getTableID.TableIdInfo>> baseTableMap = 
                    getTableIDService.getAllBaseIdsAndTableIds();
            
            baseTableMappings.clear();
            
            for (getTableID.BaseIdInfo baseInfo : baseIds) {
                List<getTableID.TableIdInfo> tableIds = 
                        baseTableMap.getOrDefault(baseInfo.getBaseId(), new ArrayList<>());
                
                // Trim() Base Name để loại bỏ khoảng trắng thừa khi lưu vào mapping
                String baseName = baseInfo.getBaseName() != null ? baseInfo.getBaseName().trim() : null;
                
                // Nếu có tables, thêm từng table vào mapping
                if (!tableIds.isEmpty()) {
                    for (getTableID.TableIdInfo tableInfo : tableIds) {
                        baseTableMappings.add(new BaseTableMapping(
                                baseName,
                                baseInfo.getBaseId(),
                                tableInfo.getTableId(),
                                tableInfo.getTableName() != null ? tableInfo.getTableName().trim() : null
                        ));
                    }
                } else {
                    // Nếu không có table, vẫn thêm base vào mapping (table ID sẽ null)
                    baseTableMappings.add(new BaseTableMapping(
                            baseName,
                            baseInfo.getBaseId(),
                            null,
                            null
                    ));
                }
            }
            
            log.info("✅ Loaded {} Base-Table mappings", baseTableMappings.size());
        } catch (Exception e) {
            log.error("❌ Failed to load Base-Table mappings: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Extract số điện thoại từ tên (tìm chuỗi số có độ dài từ 8-11 chữ số)
     */
    private String extractPhoneNumber(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        // Pattern để tìm số điện thoại: 8-11 chữ số liên tiếp
        Pattern pattern = Pattern.compile("\\d{8,11}");
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
    
    /**
     * Tìm Base ID và Table ID dựa trên Base Name (CSKH name)
     * So sánh bằng số điện thoại (SDT) trong tên thay vì so sánh toàn bộ tên
     * @param baseName Tên Base (tên CSKH)
     * @return Optional chứa BaseTableMapping nếu tìm thấy
     */
    public Optional<BaseTableMapping> findMappingByBaseName(String baseName) {
        if (baseName == null || baseName.isBlank()) {
            return Optional.empty();
        }
        
        String trimmedName = baseName.trim();
        String phoneFromInput = extractPhoneNumber(trimmedName);
        
        log.debug("🔍 Searching for mapping with base name: '{}'", trimmedName);
        log.debug("   Extracted phone number: {}", phoneFromInput != null ? phoneFromInput : "(not found)");
        
        if (phoneFromInput == null || phoneFromInput.isBlank()) {
            log.warn("⚠️ Cannot extract phone number from base name: '{}'", trimmedName);
            // Fallback: thử exact match nếu không tìm được SDT
            for (BaseTableMapping mapping : baseTableMappings) {
                String mappingBaseName = mapping.getBaseName() != null ? mapping.getBaseName().trim() : null;
                if (mappingBaseName != null && trimmedName.equalsIgnoreCase(mappingBaseName)) {
                    log.info("✅ Found exact mapping (fallback) for base name '{}': baseId={}, tableId={}", 
                            trimmedName, mapping.getBaseId(), mapping.getTableId());
                    return Optional.of(mapping);
                }
            }
            return Optional.empty();
        }
        
        // Tìm match bằng SDT
        for (BaseTableMapping mapping : baseTableMappings) {
            String mappingBaseName = mapping.getBaseName() != null ? mapping.getBaseName().trim() : null;
            if (mappingBaseName != null) {
                String phoneFromMapping = extractPhoneNumber(mappingBaseName);
                if (phoneFromMapping != null && phoneFromMapping.equals(phoneFromInput)) {
                    log.info("✅ Found mapping by phone number '{}' for base name '{}': baseId={}, tableId={}", 
                            phoneFromInput, trimmedName, mapping.getBaseId(), mapping.getTableId());
                    return Optional.of(mapping);
                }
            }
        }
        
        log.warn("⚠️ No mapping found for base name: '{}' (phone: '{}')", trimmedName, phoneFromInput);
        log.debug("Available base names with phones in mappings: {}", 
                baseTableMappings.stream()
                    .map(m -> {
                        String name = m.getBaseName() != null ? m.getBaseName().trim() : "null";
                        String phone = extractPhoneNumber(name);
                        return "'" + name + "' (phone: " + (phone != null ? phone : "N/A") + ")";
                    })
                    .collect(java.util.stream.Collectors.joining(", ")));
        return Optional.empty();
    }
    
    /**
     * Lấy tất cả mappings
     */
    public List<BaseTableMapping> getAllMappings() {
        return new ArrayList<>(baseTableMappings);
    }
    
    /**
     * Set mappings từ bên ngoài (ví dụ từ session sau khi login)
     */
    public void setMappings(List<BaseTableMapping> mappings) {
        this.baseTableMappings = new ArrayList<>(mappings);
        log.info("✅ Set {} Base-Table mappings", baseTableMappings.size());
    }
}
