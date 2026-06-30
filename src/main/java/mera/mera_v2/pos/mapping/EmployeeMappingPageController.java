package mera.mera_v2.pos.mapping;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/employee-mapping")
@RequiredArgsConstructor
public class EmployeeMappingPageController {

    @GetMapping
    public String mappingPage(Model model) {
        return "employee-mapping";
    }
}
