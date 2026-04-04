package mera.mera_v2.customer.Controller;

import lombok.RequiredArgsConstructor;
import mera.mera_v2.customer.Service.SearchService;
import mera.mera_v2.model.UserConfigDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchConfigController {

    private final SearchService searchService;
    private static final String SESSION_USER_CONFIGS = "SESSION_USER_CONFIGS";

    @SuppressWarnings("unchecked")
    @GetMapping("/search-configs")
    public ResponseEntity<List<UserConfigDto>> getSearchConfigs(HttpSession session) {
        try {
            searchService.ensureUserConfigsStored(session);
            List<UserConfigDto> configs = (List<UserConfigDto>) session.getAttribute("SESSION_USER_CONFIGS");
            if (configs == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
