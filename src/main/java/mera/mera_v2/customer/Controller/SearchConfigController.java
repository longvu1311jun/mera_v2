package mera.mera_v2.customer.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.customer.Dto.SearchConfigDto;
import mera.mera_v2.customer.Service.SearchConfigService;
import mera.mera_v2.entity.SearchConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SearchConfigController {

    private final SearchConfigService searchConfigService;

    @GetMapping("/search-configs")
    public ResponseEntity<List<SearchConfigDto>> getSearchConfigs() {
        try {
            List<SearchConfig> configs = searchConfigService.getActiveConfigs();
            List<SearchConfigDto> dtos = configs.stream()
                    .map(SearchConfigDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Error getting search configs", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @PostMapping("/search-configs/reload")
    public ResponseEntity<?> reloadConfigs() {
        try {
            log.info("Starting reload of search configs...");
            int count = searchConfigService.reloadAll();
            return ResponseEntity.ok(Map.of(
                    "message", "Da reload " + count + " configs",
                    "count", count
            ));
        } catch (Exception e) {
            log.error("Error reloading configs", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Loi reload: " + e.getMessage()));
        }
    }
}
