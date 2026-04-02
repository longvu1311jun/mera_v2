package mera.mera_v2.customer.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SearchInfoPageController {

  @GetMapping("/search-info")
  public String searchInfo() {
    return "searchInfo";
  }
}
