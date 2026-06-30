package mera.mera_v2.pos.assignment;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrderAssignmentPageController {

    @GetMapping("/assignment-monitoring")
    public String showAssignmentPage() {
        return "order-assignment";
    }
}
