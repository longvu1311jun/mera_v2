package mera.mera_v2.pos.mapping;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/admin/pos-users-baselark")
@RequiredArgsConstructor
public class PosUserBaseLarkMappingController {

    private final PosUserBaseLarkMappingService mappingService;

    @GetMapping
    public String mappingPage(Model model) {
        model.addAttribute("users", mappingService.listUsers());
        return "pos-user-baselark-mapping";
    }

    @PostMapping("/api/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> save(@RequestBody SavePosUserBaseLarkRequest request) {
        int count = mappingService.saveBaseLark(
                request != null ? request.updates() : null
        );
        return ResponseEntity.ok(Map.of(
                "success", true,
                "saved", count
        ));
    }
}
