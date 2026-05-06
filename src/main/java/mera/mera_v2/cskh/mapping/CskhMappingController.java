package mera.mera_v2.cskh.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.CskhBaseMapping;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/cskh-mapping")
@RequiredArgsConstructor
@Slf4j
public class CskhMappingController {

    private final CskhBaseMappingService mappingService;

    @GetMapping
    public String listMappings(Model model) {
        List<CskhBaseMapping> mappings = mappingService.getActiveMappings();
        List<CskhMappingDto> dtos = mappings.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        model.addAttribute("mappings", dtos);
        return "cskh-mapping";
    }

    @PostMapping("/reload")
    public String reloadMappings(RedirectAttributes redirectAttributes) {
        try {
            log.info("Bắt đầu reload CSKH Base Mapping...");
            int count = mappingService.loadAndSaveMappings();
            redirectAttributes.addFlashAttribute("success", "Đã load và lưu " + count + " mappings thành công!");
        } catch (Exception e) {
            log.error("Lỗi khi reload mapping: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/cskh-mapping";
    }

    @GetMapping("/api/mappings")
    @ResponseBody
    public List<CskhMappingDto> getMappingsApi() {
        return mappingService.getActiveMappings().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private CskhMappingDto toDto(CskhBaseMapping entity) {
        return CskhMappingDto.builder()
                .id(entity.getId())
                .posName(entity.getPosName())
                .posPhone(entity.getPosPhone())
                .larkBaseName(entity.getLarkBaseName())
                .larkBaseId(entity.getLarkBaseId())
                .khachHangTableId(entity.getKhachHangTableId())
                .traoDoiTableId(entity.getTraoDoiTableId())
                .lichHenTableId(entity.getLichHenTableId())
                .viewId(entity.getViewId())
                .isActive(entity.getIsActive())
                .departmentName(entity.getDepartmentName())
                .build();
    }
}
