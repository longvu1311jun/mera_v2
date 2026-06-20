package mera.mera_v2.cskh.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.SearchConfig;
import mera.mera_v2.customer.Service.SearchConfigService;
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

    private final SearchConfigService searchConfigService;

    @GetMapping
    public String listMappings(Model model) {
        List<SearchConfig> configs = searchConfigService.getActiveConfigs();
        List<CskhMappingDto> dtos = configs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        model.addAttribute("mappings", dtos);
        return "cskh-mapping";
    }

    @PostMapping("/reload")
    public String reloadMappings(RedirectAttributes redirectAttributes) {
        try {
            log.info("Bat dau reload CSKH Base Mapping tu DB...");
            int count = searchConfigService.reloadAll();
            redirectAttributes.addFlashAttribute("success", "Da load va luu " + count + " mappings thanh cong!");
        } catch (Exception e) {
            log.error("Loi khi reload mapping: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Loi: " + e.getMessage());
        }
        return "redirect:/admin/cskh-mapping";
    }

    @GetMapping("/api/mappings")
    @ResponseBody
    public List<CskhMappingDto> getMappingsApi() {
        return searchConfigService.getActiveConfigs().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private CskhMappingDto toDto(SearchConfig cfg) {
        return CskhMappingDto.builder()
                .id(cfg.getLarkBaseId())
                .posName(cfg.getPosName())
                .posPhone(cfg.getPosPhone())
                .larkBaseName(cfg.getLarkBaseName())
                .larkBaseId(cfg.getLarkBaseId())
                .khachHangTableId(cfg.getKhachHangTableId())
                .traoDoiTableId(cfg.getTraoDoiTableId())
                .lichHenTableId(cfg.getLichHenTableId())
                .viewId(cfg.getKhachHangViewId())
                .isActive(cfg.getSyncStatus() != null && cfg.getSyncStatus() == 2)
                .departmentName(cfg.getDepartmentName())
                .build();
    }
}
