package mera.mera_v2.lark.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.LarkBitableConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/lark-bitable")
@RequiredArgsConstructor
@Slf4j
public class LarkBitableConfigController {

    private final LarkBitableConfigService configService;

    @GetMapping
    public String listConfigs(Model model) {
        List<LarkBitableConfigDto> configs = configService.getAllConfigs().stream()
                .map(this::toDto)
                .toList();
        model.addAttribute("configs", configs);
        model.addAttribute("newConfig", new LarkBitableConfigDto());
        return "lark-bitable-config";
    }

    @PostMapping("/save")
    public String saveConfig(@ModelAttribute LarkBitableConfigDto dto, RedirectAttributes redirectAttributes) {
        try {
            LarkBitableConfig config = toEntity(dto);
            configService.saveConfig(config);
            redirectAttributes.addFlashAttribute("success", "Lưu cấu hình thành công!");
        } catch (Exception e) {
            log.error("Error saving config", e);
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/lark-bitable";
    }

    @PostMapping("/delete/{id}")
    public String deleteConfig(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            configService.deleteConfig(id);
            redirectAttributes.addFlashAttribute("success", "Xóa cấu hình thành công!");
        } catch (Exception e) {
            log.error("Error deleting config", e);
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/lark-bitable";
    }

    @PostMapping("/set-default/{id}")
    public String setDefault(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            configService.setDefault(id);
            redirectAttributes.addFlashAttribute("success", "Đặt làm mặc định thành công!");
        } catch (Exception e) {
            log.error("Error setting default", e);
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/lark-bitable";
    }

    @GetMapping("/api/configs")
    @ResponseBody
    public ResponseEntity<List<LarkBitableConfigDto>> getConfigsApi() {
        List<LarkBitableConfigDto> configs = configService.getAllConfigs().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(configs);
    }

    @GetMapping("/api/configs/default")
    @ResponseBody
    public ResponseEntity<LarkBitableConfigDto> getDefaultConfigApi() {
        return configService.getDefaultConfig()
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private LarkBitableConfigDto toDto(LarkBitableConfig entity) {
        return LarkBitableConfigDto.builder()
                .id(entity.getId())
                .configName(entity.getConfigName())
                .baseName(entity.getBaseName())
                .baseId(entity.getBaseId())
                .tableName(entity.getTableName())
                .tableId(entity.getTableId())
                .viewId(entity.getViewId())
                .userAccessToken(entity.getUserAccessToken())
                .isDefault(entity.getIsDefault())
                .shopId(entity.getShopId())
                .build();
    }

    private LarkBitableConfig toEntity(LarkBitableConfigDto dto) {
        return LarkBitableConfig.builder()
                .id(dto.getId())
                .configName(dto.getConfigName())
                .baseName(dto.getBaseName())
                .baseId(dto.getBaseId())
                .tableName(dto.getTableName())
                .tableId(dto.getTableId())
                .viewId(dto.getViewId())
                .userAccessToken(dto.getUserAccessToken())
                .isDefault(dto.getIsDefault())
                .shopId(dto.getShopId())
                .build();
    }
}
